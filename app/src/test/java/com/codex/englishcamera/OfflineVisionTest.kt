package com.codex.englishcamera

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class OfflineVisionTest {
    private fun idleUntil(done: ()->Boolean) {
        repeat(200) { shadowOf(Looper.getMainLooper()).idle();if(done())return;Thread.sleep(10) }
        assertTrue("Timed out waiting for state",done())
    }
    @Test fun realDesktopMilkAnswerMapsToExistingWordWithoutMakingUpBoxes() {
        val record=JSONObject(javaClass.getResource("/vlm-package-name.json")!!.readText())
        val raw=record.getString("output")
        val app=RuntimeEnvironment.getApplication();val store=LearningStore(app)
        store.ui=store.ui.copy(frozen=true,bitmap=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888),
            found=listOf(Found(1,"stop sign",.9f,Box(0f,0f,1f,1f))))
        store.offlineInference={ _,progress -> progress("test record replay")
            VlmAnswer(raw,parseVlmTerms(raw),record.getDouble("inference_seconds").toLong()*1000) }
        store.offlineVision();idleUntil { !store.ui.pending }
        assertEquals("skim milk",store.ui.vlmAnswer!!.terms.single().english)
        assertEquals("milk",store.ui.vlmAnswer!!.terms.single().word(store.lexicon)!!.key)
        assertTrue(store.ui.found.isEmpty());assertNull(store.ui.vlmError)
        ViewModelStore().apply { put("test",store);clear() }
    }
    @Test fun missingModelReportsAnErrorWithoutLoadingNativeCodeOrLosingThePhoto() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        val photo=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888)
        store.ui=store.ui.copy(frozen=true,bitmap=photo)
        store.offlineVision();idleUntil { !store.ui.pending }
        assertFalse(store.ui.runningVlm);assertNotNull(store.ui.vlmError)
        assertSame(photo,store.ui.bitmap)
        ViewModelStore().apply { put("test",store);clear() }
    }
    @Test fun returningToLiveCancelsTheOldRecognitionAndRejectsStaleFrames() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        store.ui=store.ui.copy(frozen=true,bitmap=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888))
        var started=false;var cancelled=false
        store.offlineInference={ _,_->suspendCancellableCoroutine { c ->
            started=true;c.invokeOnCancellation { cancelled=true }
        } }
        store.offlineVision();idleUntil { started }
        val old=store.session();store.live();idleUntil { cancelled }
        store.frame(old,Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888),listOf(Found(1,"stop sign",.9f,Box(0f,0f,1f,1f))))
        assertFalse(store.ui.runningVlm);assertFalse(store.ui.pending)
        assertNull(store.ui.vlmAnswer);assertTrue(store.ui.found.isEmpty());assertTrue(store.ui.history.isEmpty())
        ViewModelStore().apply { put("test",store);clear() }
    }
    @Test fun malformedAndTruncatedRepliesAreNotInventedIntoVocabulary() {
        assertTrue(parseVlmTerms("no clear object").isEmpty())
        assertTrue(parseVlmTerms("{\"objects\":[{\"english\":\"milk\"").isEmpty())
        assertTrue(parseVlmTerms("{\"objects\":[]}").isEmpty())
        assertEquals(listOf("hand"),parseVlmTerms("```json\n{\"objects\":[{\"english\":\"hand\",\"chinese\":\"手\"}]}\n```").map { it.english })
    }
    @Test fun nativeWorkerIsNotExportedAndRunsOutsideTheUiProcess() {
        val app=RuntimeEnvironment.getApplication()
        @Suppress("DEPRECATION") val service=app.packageManager.getServiceInfo(ComponentName(app,OfflineVisionService::class.java),0)
        assertEquals(app.packageName+":offline_vision",service.processName)
        assertFalse(service.exported)
        @Suppress("DEPRECATION") val permissions=app.packageManager.getPackageInfo(app.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
    }
}
