package com.codex.englishcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun foundJson(items: List<Found>)=JSONArray(items.map { item ->
    JSONObject().put("key",item.key).put("score",if(item.evidence==null)item.score.toDouble() else JSONObject.NULL)
        .put("evidence",item.evidence ?: "visual_model")
        .put("box",JSONArray(listOf(item.box.left,item.box.top,item.box.right,item.box.bottom)))
})

internal fun readLearningPhoto(context: Context,uri: Uri): Bitmap {
    if(Build.VERSION.SDK_INT>=28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) { decoder,info,_ ->
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        val ratio=minOf(1f,1920f/maxOf(info.size.width,info.size.height))
        decoder.setTargetSize((info.size.width*ratio).toInt().coerceAtLeast(1),(info.size.height*ratio).toInt().coerceAtLeast(1))
        decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
    }
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,bounds) }
    check(bounds.outWidth>0 && bounds.outHeight>0)
    val options=BitmapFactory.Options().apply {
        while(maxOf(bounds.outWidth,bounds.outHeight)/inSampleSize>1920)inSampleSize*=2
    }
    val bitmap=context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it,null,options) } ?: error("Unreadable image")
    val orientation=context.contentResolver.openInputStream(uri)!!.use {
        android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)
    }
    val matrix=android.graphics.Matrix().apply {
        when(orientation) { 2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f)
            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(270f);postScale(-1f,1f)};8->setRotate(270f) }
    }
    return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true).also { if(it!==bitmap)bitmap.recycle() }
}

/** Only called after the user chooses a destination in Android's Save dialog. */
internal fun writePhotoDiagnostics(output: OutputStream,bitmap: Bitmap,found: List<Found>,trace: String?,mode: String) {
    val report=JSONObject().put("app_version",BuildConfig.VERSION_NAME).put("manufacturer",Build.MANUFACTURER)
        .put("model",Build.MODEL).put("sdk",Build.VERSION.SDK_INT).put("abis",JSONArray(Build.SUPPORTED_ABIS.toList()))
        .put("mode",mode).put("width",bitmap.width).put("height",bitmap.height)
        .put("results",foundJson(found)).put("trace",trace?.let { JSONObject(it) } ?: JSONObject())
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("report.json"));zip.write(report.toString(2).toByteArray(Charsets.UTF_8));zip.closeEntry()
        zip.putNextEntry(ZipEntry("photo.jpg"));check(bitmap.compress(Bitmap.CompressFormat.JPEG,95,zip));zip.closeEntry()
    }
}
