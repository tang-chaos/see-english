package com.codex.englishcamera

import android.content.*
import android.graphics.Bitmap
import android.os.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class OfflineVisionClient(private val context: Context) {
    suspend fun recognize(bitmap: Bitmap,progress: (String)->Unit): VlmAnswer {
        val image=withContext(Dispatchers.IO) {
            check(OfflineModel.available(context)) { "请先导入离线识别文件，或使用完整离线安装套装" }
            val ratio=minOf(1f,896f/maxOf(bitmap.width,bitmap.height))
            val scaled=Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true)
            val file=File(context.cacheDir,"vlm-${UUID.randomUUID()}.jpg")
            try { file.outputStream().use { check(scaled.compress(Bitmap.CompressFormat.JPEG,95,it)) };file }
            finally { if(scaled!==bitmap)scaled.recycle() }
        }
        try {
            return withContext(Dispatchers.Main) { bind(image,progress) }
        } finally { image.delete() }
    }

    private suspend fun bind(image: File,progress: (String)->Unit): VlmAnswer=suspendCancellableCoroutine { cont ->
        val main=Handler(Looper.getMainLooper());val done=AtomicBoolean(false)
        var remote: Messenger?=null;var bound=false
        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable
        fun cleanup(cancel: Boolean) {
            if(cancel)runCatching { remote?.send(Message.obtain(null,VisionIpc.CANCEL)) }
            main.removeCallbacks(timeout)
            if(bound) { runCatching { context.unbindService(connection) };bound=false }
        }
        fun fail(message: String) {
            if(done.compareAndSet(false,true)) { cleanup(true);if(cont.isActive)cont.resumeWithException(IllegalStateException(message)) }
        }
        timeout=Runnable { fail("识别耗时过长，已停止离线任务，请尝试更清晰的单个物品照片") }
        val receiver=Messenger(object: Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if(done.get())return
                when(message.what) {
                    VisionIpc.PROGRESS->progress(message.data.getString("text").orEmpty())
                    VisionIpc.ERROR->fail(message.data.getString("text") ?: "离线识别失败")
                    VisionIpc.RESULT->if(done.compareAndSet(false,true)) {
                        val raw=message.data.getString("raw").orEmpty()
                        cleanup(false)
                        if(cont.isActive)cont.resume(VlmAnswer(raw,parseVlmTerms(raw),message.data.getLong("elapsed"),message.data.getString("metrics") ?: "{}"))
                    }
                }
            }
        })
        connection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName?,binder: IBinder?) {
                if(done.get())return
                try {
                    remote=Messenger(binder)
                    remote!!.send(Message.obtain(null,VisionIpc.REQUEST).apply {
                        replyTo=receiver;data=Bundle().apply { putString("image",image.path);putInt("pid",Process.myPid()) }
                    })
                } catch(error: Exception) { fail("无法启动离线识别：${error.message}") }
            }
            override fun onServiceDisconnected(name: ComponentName?)=fail("离线识别进程已结束，可能是可用内存不足，请关闭其他应用后重试")
            override fun onNullBinding(name: ComponentName?)=fail("无法连接离线识别服务")
            override fun onBindingDied(name: ComponentName?)=fail("离线识别连接已结束，请重试")
        }
        cont.invokeOnCancellation { main.post { if(done.compareAndSet(false,true))cleanup(true) } }
        try {
            bound=context.bindService(Intent(context,OfflineVisionService::class.java),connection,Context.BIND_AUTO_CREATE)
            if(!bound)fail("无法启动离线识别服务") else main.postDelayed(timeout,8*60*1000L)
        } catch(error: Exception) { fail("离线识别服务不可用：${error.message}") }
    }
}
