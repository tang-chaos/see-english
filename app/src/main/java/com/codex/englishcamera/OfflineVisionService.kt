package com.codex.englishcamera

import android.app.Service
import android.content.Intent
import android.os.*
import com.alibaba.mnnllm.android.llm.GenerateProgressListener
import com.alibaba.mnnllm.android.llm.LlmSession
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

internal object VisionIpc {
    const val REQUEST=10;const val CANCEL=11;const val PROGRESS=1;const val RESULT=2;const val ERROR=3
}

/** Large native inference lives outside the camera/UI process. No network capability. */
class OfflineVisionService : Service() {
    private val executor=Executors.newSingleThreadExecutor()
    private var busy=false
    private var ownerPid=0
    private val messenger=Messenger(object: Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if(message.what==VisionIpc.CANCEL) {
                // Only terminate this explicitly separate worker process.
                if(ownerPid!=0 && ownerPid!=Process.myPid())Process.killProcess(Process.myPid())
                return
            }
            if(message.what!=VisionIpc.REQUEST)return
            val reply=message.replyTo ?: return
            if(busy) { send(reply,VisionIpc.ERROR,Bundle().apply { putString("text","上一项识别仍在结束，请稍后重试") });return }
            ownerPid=message.data.getInt("pid")
            if(ownerPid==0 || ownerPid==Process.myPid()) {
                send(reply,VisionIpc.ERROR,Bundle().apply { putString("text","离线识别进程配置不正确") });return
            }
            val image=message.data.getString("image").orEmpty()
            busy=true
            executor.execute { run(image,reply) }
        }
    })
    override fun onBind(intent: Intent?)=messenger.binder
    private fun send(target: Messenger,what: Int,data: Bundle) {
        runCatching { target.send(Message.obtain(null,what).apply { this.data=data }) }
    }
    private fun status(reply: Messenger,text: String)=send(reply,VisionIpc.PROGRESS,Bundle().apply { putString("text",text) })
    private fun run(path: String,reply: Messenger) {
        var session: LlmSession?=null;var pointer=0L
        val start=SystemClock.elapsedRealtime()
        try {
            val image=File(path).canonicalFile
            check(image.isFile && image.path.startsWith(cacheDir.canonicalPath+File.separator)) { "学习照片已失效，请重新选择" }
            OfflineModel.verify(this) { status(reply,it) }
            status(reply,"正在加载离线识别，首次使用请耐心等待…")
            session=LlmSession()
            val config=JSONObject().put("backend_type","cpu").put("thread_num",4)
                .put("precision","low").put("memory","low").put("max_new_tokens",384)
                .put("sampler_type","greedy").put("reuse_kv",false)
                .put("mllm",JSONObject().put("backend_type","cpu").put("thread_num",4).put("precision","low").put("memory","low"))
            val extra=JSONObject().put("is_r1",false).put("keep_history",false).put("mmap_dir","")
            pointer=session.initNative(File(OfflineModel.directory(this),"config.json").path,null,config.toString(),extra.toString())
            check(pointer!=0L) { "模型没有成功加载，请检查可用内存并重试" }
            status(reply,"正在理解照片，识别过程完全离线…")
            val prompt="<img>${image.path}</img>"+
                "Name the clearly visible physical objects or products. Give short English names and Chinese meanings. "+
                "Use readable package text. Treat printed figures as artwork. Identify visible body parts specifically. "+
                "Do not guess unclear objects. Return only JSON: {\"objects\":[{\"english\":\"name\",\"chinese\":\"中文\"}]}. At most 10 objects."
            val output=StringBuilder()
            val metrics=session.submitNative(pointer,prompt,false,GenerateProgressListener { text ->
                if(text!=null && text!="<eop>")output.append(text)
                output.length>8192
            })
            check(!metrics.containsKey("error")) { metrics["error"].toString() }
            send(reply,VisionIpc.RESULT,Bundle().apply {
                putString("raw",output.toString());putLong("elapsed",SystemClock.elapsedRealtime()-start)
                putString("metrics",JSONObject(metrics as Map<*,*>).toString())
            })
        } catch(error: Throwable) {
            send(reply,VisionIpc.ERROR,Bundle().apply { putString("text",error.message ?: "离线识别未完成，请关闭其他占内存应用后重试") })
        } finally {
            if(pointer!=0L)runCatching { session?.releaseNative(pointer) }
            busy=false;stopSelf()
        }
    }
    override fun onDestroy() { executor.shutdown();super.onDestroy() }
}
