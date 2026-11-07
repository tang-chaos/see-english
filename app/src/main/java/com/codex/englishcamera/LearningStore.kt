package com.codex.englishcamera

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

data class LearningState(
    val page: Int = 0,
    val frozen: Boolean = false,
    val pending: Boolean = false,
    val bitmap: Bitmap? = null,
    val found: List<Found> = emptyList(),
    val selected: String? = null,
    val large: Boolean = false,
    val allLabels: Boolean = false,
    val saved: Set<String> = emptySet(),
    val reviewed: Set<String> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val hasFrame: Boolean = false,
    val recognitionWarning: String? = null,
    val capturing: Boolean = false,
    val diagnostics: String? = null,
    val runningVlm: Boolean = false,
    val vlmProgress: String? = null,
    val vlmAnswer: VlmAnswer? = null,
    val vlmError: String? = null,
    val modelReady: Boolean = false,
    val importingModel: Boolean = false,
    val importProgress: String? = null
)

class LearningStore(app: Application) : AndroidViewModel(app) {
    val lexicon = Lexicon.load(app)
    val engine = DetectionEngine(app, lexicon)
    internal var capturePhoto: ((Int) -> Unit)? = null
    internal var photoInference: ((Bitmap) -> DetectionBatch)? = null
    internal var offlineInference: (suspend (Bitmap,(String)->Unit)->VlmAnswer)? = null
    private val offlineClient=OfflineVisionClient(app)
    private var offlineJob: kotlinx.coroutines.Job?=null
    private var importJob: kotlinx.coroutines.Job?=null
    private val prefs = app.getSharedPreferences("learning", 0)
    private val userWords=decodeUserWords(prefs.getString("userWords",null))
    private val initialHistory=decodeHistory(prefs.getString("history",null)).filter { word(it.key)!=null }
    private val historyKeys=initialHistory.map { it.key }.toMutableSet()
    var ui by mutableStateOf(LearningState(
        large = prefs.getBoolean("large", false),
        modelReady = OfflineModel.available(app),
        history = initialHistory,
        saved = prefs.getStringSet("saved", emptySet()).orEmpty().filter { word(it) != null }.toSet(),
        reviewed = prefs.getStringSet("reviewed", emptySet()).orEmpty().filter {
            word(it)!=null && it in prefs.getStringSet("saved",emptySet()).orEmpty()
        }.toSet()
    ))
        internal set
    @Volatile private var epoch = 0
    private val stabilizer = Stabilizer()
    private val confirmation = ConfirmationGate()
    private var ttsReady = false
    private var tts: TextToSpeech? = null
    private var lastFrame = 0L

    init {
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                val offlineVoice=tts?.voices?.filter { it.locale.language=="en" && !it.isNetworkConnectionRequired }
                    ?.sortedBy { if(it.locale.country=="US")0 else 1 }?.firstOrNull()
                ttsReady = result != null && result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED &&
                    offlineVoice!=null && tts?.setVoice(offlineVoice)==TextToSpeech.SUCCESS
            }
        }
    }

    fun session(): Int = epoch
    fun frame(generation: Int, bitmap: Bitmap, found: List<Found>, refined: Boolean = true, warning: String? = null) {
        if (generation != epoch || ui.frozen || ui.capturing || ui.page != 0) return
        val freezing = ui.pending && refined
        lastFrame = SystemClock.elapsedRealtime()
        ui = ui.copy(bitmap = bitmap, found = if (freezing) found else stabilizer.update(confirmation.update(found)),
            frozen = freezing, pending = ui.pending && !freezing, hasFrame = true, error = null, recognitionWarning = warning)
        recordFoundHistory(ui.found)
    }
    fun freeze() {
        if(ui.pending || ui.frozen)return
        if (!ui.hasFrame) { notice("相机正在准备，请稍后再试"); return }
        capturePhoto?.let { capture ->
            ui=ui.copy(pending=true,capturing=true,selected=null,message=null)
            try { capture(epoch) } catch(error: Exception) { captureFailed(epoch) }
            return
        }
        val photo=ui.bitmap ?: return
        analyzePhoto(photo) { photo }
    }
    fun live() {
        cancelOfflineVision()
        epoch++; stabilizer.reset(); confirmation.reset()
        ui = ui.copy(frozen = false, pending = false, bitmap = null,
            found = emptyList(), selected = null, hasFrame = false, error = null, recognitionWarning = null,
            capturing=false,
            vlmAnswer=null,vlmError=null,vlmProgress=null)
    }
    fun page(index: Int) {
        if(index !in 0..2)return
        cancelOfflineVision()
        if (ui.pending) {
            ui = ui.copy(frozen = false, bitmap = null, found = emptyList(), hasFrame = false)
        }
        epoch++; ui = ui.copy(page = index, pending = false, selected = null,capturing=false)
        if (index == 0 && !ui.frozen) live()
    }
    fun select(key: String?) { ui = ui.copy(selected = key) }
    fun notice(text: String?) { ui = ui.copy(message = text) }
    fun failure(generation: Int, text: String) {
        if (generation == epoch && !ui.frozen) ui = ui.copy(error = text, pending = false, capturing=false, found = emptyList())
    }
    fun captured(generation: Int, bitmap: Bitmap) {
        if(generation!=epoch || !ui.capturing || ui.page!=0) { bitmap.recycle();return }
        analyzePhoto(bitmap) { bitmap }
    }
    fun captureFailed(generation: Int) {
        if(generation==epoch && ui.capturing)ui=ui.copy(pending=false,capturing=false,message="没有拍到清晰照片，请保持相机开启再试")
    }
    fun expire() {
        if (!ui.frozen && ui.found.isNotEmpty() && SystemClock.elapsedRealtime() - lastFrame > 5000) {
            ui = ui.copy(found = emptyList())
        }
    }
    fun stopCamera() {
        cancelOfflineVision()
        epoch++; stabilizer.reset(); confirmation.reset()
        ui = ui.copy(pending = false,capturing=false, found = if (ui.frozen) ui.found else emptyList(), hasFrame = ui.frozen)
    }
    fun large(value: Boolean) { prefs.edit { putBoolean("large", value) }; ui = ui.copy(large = value) }
    fun allLabels(value: Boolean) { ui = ui.copy(allLabels = value) }
    fun save(key: String) {
        if (word(key) == null) return
        val next = ui.saved.toMutableSet()
        val removed = !next.add(key)
        if (removed) next.remove(key)
        val reviewed = ui.reviewed.intersect(next)
        prefs.edit {
            putStringSet("saved", next); putStringSet("reviewed", reviewed)
            putString("userWords",encodeUserWords(userWords.values))
        }
        ui = ui.copy(saved = next.toSet(), reviewed = reviewed,
            message = if (removed) "已移出生词本" else "已加入生词本")
    }
    fun word(key: String): Word? = lexicon[key] ?: userWords[key]
    fun termKey(term: VlmTerm): String = term.word(lexicon)?.key ?: userWordKey(term.english.trim().take(80))
    fun saveTerm(term: VlmTerm) {
        val existing=term.word(lexicon)
        if(existing!=null) { save(existing.key);return }
        val custom=userWord(term) ?: return
        userWords.putIfAbsent(custom.key,custom)
        save(custom.key)
    }
    /** Record only accepted results; duplicate frames never trigger another preference write. */
    internal fun recordRecognizedTerms(terms: List<VlmTerm>,now: Long=System.currentTimeMillis()) {
        val added=mutableListOf<HistoryEntry>()
        for(term in terms) {
            val known=term.word(lexicon)
            val entry=known ?: userWord(term) ?: continue
            if(!historyKeys.add(entry.key))continue
            if(known==null)userWords.putIfAbsent(entry.key,entry)
            added+=HistoryEntry(entry.key,now)
        }
        if(added.isEmpty())return
        val next=added+ui.history
        prefs.edit { putString("history",encodeHistory(next));putString("userWords",encodeUserWords(userWords.values)) }
        ui=ui.copy(history=next)
    }
    private fun recordFoundHistory(found: List<Found>) {
        recordRecognizedTerms(found.mapNotNull { item -> word(item.key)?.let { VlmTerm(it.english,it.chinese) } })
    }
    fun clearHistory() {
        historyKeys.clear()
        userWords.keys.retainAll(ui.saved)
        prefs.edit { putString("history","[]");putString("userWords",encodeUserWords(userWords.values)) }
        ui=ui.copy(history=emptyList(),selected=ui.selected?.takeIf { word(it)!=null },message="已清空识别历史，生词本已保留")
    }
    fun review(key: String) {
        if (key !in ui.saved) return
        val next = ui.reviewed.toMutableSet()
        if (!next.add(key)) next.remove(key)
        prefs.edit { putStringSet("reviewed", next) }
        ui = ui.copy(reviewed = next.toSet())
    }
    fun speak(text: String) {
        if (!ttsReady) { notice("请先在系统“文字转语音”设置中安装英语语音包"); return }
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${SystemClock.uptimeMillis()}") == TextToSpeech.ERROR) {
            notice("朗读未成功，请检查系统英语语音包")
        }
    }
    fun speechSettings() {
        runCatching {
            getApplication<Application>().startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { notice("请在手机系统设置中搜索“文字转语音”") }
    }
    fun importPhoto(uri: android.net.Uri) {
        analyzePhoto(null) {
            readLearningPhoto(getApplication(),uri)
        }
    }
    fun exportDiagnostics(uri: android.net.Uri) {
        val state=ui
        val bitmap=state.bitmap ?: return
        if(!state.frozen || state.pending)return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output=getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("Cannot open destination")
                    writePhotoDiagnostics(output,bitmap,state.found,state.diagnostics,if(state.vlmAnswer!=null)"OFFLINE_8B" else "LITE0")
                }
                notice("诊断包已保存，内含本次照片和识别记录")
            } catch(error: Exception) { notice("诊断包保存失败，请重新选择位置") }
        }
    }
    fun refreshOfflineModel() { ui=ui.copy(modelReady=OfflineModel.available(getApplication())) }
    fun importOfflineModel(uri: android.net.Uri) {
        if(ui.runningVlm || ui.importingModel)return
        ui=ui.copy(importingModel=true,importProgress="正在准备导入…",vlmError=null)
        importJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val job=kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                    OfflineModel.import(getApplication(),uri) { text ->
                        job?.ensureActive()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if(importJob?.isActive==true)ui=ui.copy(importProgress=text)
                        }
                    }
                }
                ui=ui.copy(importingModel=false,importProgress="模型已导入，可断网使用",modelReady=true)
            } catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException)throw error
                ui=ui.copy(importingModel=false,importProgress=null,vlmError=error.message ?: "模型导入失败")
            }
        }
    }
    fun offlineVision() {
        val bitmap=ui.bitmap ?: return notice("请先拍照定格或从相册选择照片")
        if(!ui.frozen || ui.runningVlm || ui.importingModel)return
        epoch++;val generation=epoch
        ui=ui.copy(pending=true,runningVlm=true,vlmAnswer=null,vlmError=null,vlmProgress="正在准备离线识别…",selected=null)
        offlineJob=viewModelScope.launch {
            try {
                val progress: (String)->Unit={ text -> if(epoch==generation)ui=ui.copy(vlmProgress=text) }
                val answer=offlineInference?.invoke(bitmap,progress) ?: offlineClient.recognize(bitmap,progress)
                if(epoch==generation) {
                    ui=ui.copy(pending=false,runningVlm=false,vlmAnswer=answer,vlmProgress=null,
                        found=emptyList(),recognitionWarning="识别完成，可在“模型识别”中查看词卡",
                        diagnostics=org.json.JSONObject().put("pipeline","Qwen3-VL-8B offline")
                            .put("raw",answer.raw).put("elapsed_ms",answer.elapsedMs).put("metrics",org.json.JSONObject(answer.metrics)).toString(2))
                    recordRecognizedTerms(answer.terms)
                }
            } catch(error: Exception) {
                if(error is kotlinx.coroutines.CancellationException)throw error
                if(epoch==generation)ui=ui.copy(pending=false,runningVlm=false,vlmProgress=null,vlmError=error.message ?: "离线识别未完成")
            }
        }
    }
    fun cancelOfflineVision() {
        if(!ui.runningVlm)return
        epoch++;offlineJob?.cancel();offlineJob=null
        ui=ui.copy(pending=false,runningVlm=false,vlmProgress=null)
    }
    private fun analyzePhoto(preview: Bitmap?,load: ()->Bitmap) {
        cancelOfflineVision()
        epoch++;val generation=epoch
        ui=ui.copy(pending=true,frozen=true,bitmap=preview,found=emptyList(),selected=null,error=null,
            recognitionWarning=null,capturing=false,diagnostics=null,vlmAnswer=null,vlmError=null)
        viewModelScope.launch {
            try {
                val (bitmap,batch)=withContext(Dispatchers.Default) {
                    val bitmap=load()
                    bitmap to (photoInference?.invoke(bitmap) ?: engine.detect(bitmap,detailed=true))
                }
                if(epoch==generation) {
                    ui=ui.copy(bitmap=bitmap,found=batch.found,recognitionWarning=batch.warning,
                        pending=false,hasFrame=true,diagnostics=batch.diagnostics)
                    recordFoundHistory(batch.found)
                }
            } catch(error: Throwable) {
                if(error is kotlinx.coroutines.CancellationException)throw error
                if(error !is Exception && error !is LinkageError)throw error
                if(epoch==generation)ui=ui.copy(pending=false,error="照片识别失败，可返回相机重试")
                android.util.Log.e("EnglishCamera","Snapshot inference failed",error)
            }
        }
    }
    override fun onCleared() {
        epoch++
        offlineJob?.cancel();importJob?.cancel()
        tts?.stop(); tts?.shutdown(); engine.close()
    }
}
