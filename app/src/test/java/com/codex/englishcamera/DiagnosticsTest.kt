package com.codex.englishcamera

import android.graphics.Bitmap
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiagnosticsTest {
    @Test fun diagnosticContainsTheCorrespondingPhotoAndModelEvidence() {
        val bitmap=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888)
        val output=ByteArrayOutputStream()
        writePhotoDiagnostics(output,bitmap,listOf(Found(1,"box",.8f,Box(.1f,.2f,.3f,.4f))),"""{"common_check":"passed"}""","EXPANDED")
        val entries=mutableMapOf<String,ByteArray>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while(true) { val entry=zip.nextEntry ?: break;entries[entry.name]=zip.readBytes() }
        }
        assertEquals(setOf("report.json","photo.jpg"),entries.keys)
        val report=JSONObject(entries.getValue("report.json").toString(Charsets.UTF_8))
        assertEquals(BuildConfig.VERSION_NAME,report.getString("app_version"))
        assertEquals("passed",report.getJSONObject("trace").getString("common_check"))
        val decoded=android.graphics.BitmapFactory.decodeByteArray(entries.getValue("photo.jpg"),0,entries.getValue("photo.jpg").size)
        assertEquals(20,decoded.width);assertEquals(30,decoded.height)
        assertEquals("box",report.getJSONArray("results").getJSONObject(0).getString("key"))
    }
}
