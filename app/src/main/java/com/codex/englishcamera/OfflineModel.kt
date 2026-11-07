package com.codex.englishcamera

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal object OfflineModel {
    const val name="Qwen3-VL-8B-Instruct-MNN"
    data class Part(val name: String,val bytes: Long,val sha256: String)
    fun parts(context: Context): List<Part> {
        val json=JSONObject(context.assets.open("vlm-model.json").bufferedReader().use { it.readText() }).getJSONArray("files")
        return (0 until json.length()).map { val p=json.getJSONObject(it)
            Part(p.getString("file"),p.getLong("bytes"),p.getString("sha256")) }
    }
    fun directory(context: Context)=File(context.getExternalFilesDir(null) ?: context.filesDir,"models/$name")
    fun available(context: Context)=runCatching { val dir=directory(context)
        parts(context).all { File(dir,it.name).let { f -> f.isFile && f.length()==it.bytes } }
    }.getOrDefault(false)

    fun verify(context: Context,progress: (String)->Unit) {
        val parts=parts(context);val dir=directory(context)
        val fingerprint=parts.joinToString("|") { p -> val f=File(dir,p.name)
            check(f.isFile && f.length()==p.bytes) { "离线模型尚未安装完整，请导入模型文件夹或使用离线安装套装" }
            "${p.name}:${p.sha256}:${f.length()}:${f.lastModified()}" }
        val prefs=context.getSharedPreferences("offline-model",0)
        if(prefs.getString("verified",null)==fingerprint)return
        parts.forEachIndexed { index,p ->
            progress("正在校验离线模型 ${index+1}/${parts.size}…")
            val digest=MessageDigest.getInstance("SHA-256")
            File(dir,p.name).inputStream().buffered().use { input ->
                val buffer=ByteArray(1024*1024)
                while(true) { val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count) }
            }
            check(digest.digest().hex()==p.sha256) { "离线模型文件校验失败：${p.name}，请重新导入" }
        }
        prefs.edit().putString("verified",fingerprint).apply()
    }

    fun import(context: Context,tree: Uri,progress: (String)->Unit) {
        val resolver=context.contentResolver
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val files=mutableMapOf<String,Uri>()
        resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor ->
            while(cursor.moveToNext())files[cursor.getString(1)]=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0))
        }
        val parts=parts(context)
        check(parts.all { files.containsKey(it.name) }) { "请选择模型文件夹本身，里面应有 config.json、llm.mnn.weight 等文件" }
        val target=directory(context);target.parentFile!!.mkdirs()
        val incoming=File(target.parentFile,".incoming-${UUID.randomUUID()}").apply { mkdirs() }
        var copied=0L;var last=0L;val total=parts.sumOf { it.bytes }
        try {
            for(part in parts) {
                val digest=MessageDigest.getInstance("SHA-256");var bytes=0L
                (resolver.openInputStream(files.getValue(part.name)) ?: error("无法读取 ${part.name}")).use { input ->
                    File(incoming,part.name).outputStream().buffered().use { output ->
                        val buffer=ByteArray(1024*1024)
                        while(true) { val count=input.read(buffer);if(count<0)break
                            bytes+=count;check(bytes<=part.bytes) { "模型文件大小不正确：${part.name}" }
                            digest.update(buffer,0,count);output.write(buffer,0,count);copied+=count
                            if(System.currentTimeMillis()-last>500) { progress("正在导入离线模型 ${copied*100/total}%…");last=System.currentTimeMillis() }
                        }
                    }
                }
                check(bytes==part.bytes && digest.digest().hex()==part.sha256) { "模型文件校验失败：${part.name}" }
            }
            val old=File(target.parentFile,".previous-${UUID.randomUUID()}")
            val hadOld=target.exists()
            check(!hadOld || target.renameTo(old)) { "无法替换原模型，请关闭识别后重试" }
            if(!incoming.renameTo(target)) { if(hadOld)old.renameTo(target);error("无法保存模型，请检查可用存储") }
            old.deleteRecursively()
            context.getSharedPreferences("offline-model",0).edit().remove("verified").apply()
        } finally { incoming.deleteRecursively() }
    }
    private fun ByteArray.hex()=joinToString("") { "%02x".format(it.toInt() and 255) }
}
