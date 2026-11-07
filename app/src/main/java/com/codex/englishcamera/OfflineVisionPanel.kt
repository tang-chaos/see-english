package com.codex.englishcamera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun OfflineVisionPanel(store: LearningStore,importModel: ()->Unit) {
    val state=store.ui
    var settings by remember { mutableStateOf(!state.modelReady) }
    var raw by remember(state.vlmAnswer) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().heightIn(max=640.dp).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconBadge(Glyph.SPARK,SoftGold,Green,48)
            Column(Modifier.padding(start=12.dp)) {
                Text("模型识别",fontSize=25.sp,fontWeight=FontWeight.Bold)
                Text("发现更多物品，认识更多单词",color=Muted,fontSize=13.sp,modifier=Modifier.padding(top=3.dp))
            }
        }
        Surface(color=Mint,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(state.modelReady)"● 离线模型已就绪" else "○ 离线模型尚未安装",color=Green,fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Text("先拍照定格或从相册选择照片，再开始识别。照片在手机本地处理，加载和识别需要一些时间。",color=Muted,fontSize=13.sp,lineHeight=20.sp)
            }
        }
        if(state.runningVlm) {
            WordCardShell {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.vlmProgress ?: "正在识别…",modifier=Modifier.padding(top=8.dp),fontSize=14.sp)
                TextButton(onClick={store.cancelOfflineVision()}) { Text("停止识别") }
            }
        } else {
            Button(onClick={store.offlineVision()},enabled=state.modelReady && state.frozen && state.bitmap!=null && !state.importingModel,
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                AppIcon(Glyph.SPARK,Modifier.size(20.dp),MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp));Text("识别这张照片",fontSize=16.sp)
            }
        }
        state.vlmError?.let {
            Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
                Text(it,color=MaterialTheme.colorScheme.onErrorContainer,modifier=Modifier.padding(16.dp),fontSize=14.sp)
            }
        }
        state.vlmAnswer?.let { answer ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("发现 ${answer.terms.size} 个单词",fontSize=17.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                Text("用时 ${answer.elapsedMs/1000} 秒",color=Muted,fontSize=12.sp)
            }
            if(answer.terms.isEmpty())Text("这次没有解析出可靠词卡。可以查看原始回答，或靠近单个物品重新拍照。")
            answer.terms.forEach { term ->
                val word=term.word(store.lexicon)
                WordCardShell {
                        Text(word?.english ?: term.english,fontSize=if(state.large)27.sp else 23.sp,fontWeight=FontWeight.Bold)
                        Text(word?.ipa?.ifBlank { "暂未收录音标" } ?: "暂未收录音标",color=Green,fontSize=if(state.large)19.sp else 15.sp)
                        Text(word?.chinese ?: term.chinese,fontSize=if(state.large)20.sp else 17.sp)
                        if(word!=null && !term.english.equals(word.english,true))
                            Text("${term.english} · ${term.chinese}",fontSize=13.sp,color=Green)
                        Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                            SpeakButton("听发音") { store.speak(word?.english ?: term.english) }
                            TextButton(onClick={store.saveTerm(term)}) { Text(if(store.termKey(term) in state.saved)"移出生词本" else "加入生词本") }
                        }
                }
            }
            Text("模型回答可能有误，请结合实物核对。",color=Muted,fontSize=12.sp)
            TextButton(onClick={raw=!raw}) { Text(if(raw)"收起原始回答" else "查看原始回答",fontSize=13.sp) }
            if(raw)WordCardShell { Text(answer.raw.ifBlank { "模型没有返回内容" },fontSize=13.sp,color=Muted) }
        }
        HorizontalDivider(color=Hairline)
        TextButton(onClick={settings=!settings}) { Text(if(settings)"收起识别设置" else "识别设置",color=Muted) }
        if(settings || !state.modelReady || state.importingModel) {
            state.importProgress?.let { Text(it,fontSize=14.sp) }
            if(state.importingModel)LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick=importModel,enabled=!state.runningVlm && !state.importingModel,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("导入离线识别文件") }
            Text("首次使用时需准备离线识别文件，之后无需联网。选择离线安装套装中的模型文件夹即可导入。",fontSize=12.sp,color=Muted,lineHeight=19.sp)
            Text("看见英语 ${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=Muted)
        }
    }
}
