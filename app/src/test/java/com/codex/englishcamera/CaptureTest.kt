package com.codex.englishcamera

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CaptureTest {
    @Test fun captureWaitsForStillPhotoInsteadOfFreezingAnOldAnalysisFrame() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        var requested=-1
        store.capturePhoto={ generation -> requested=generation }
        val original=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888)
        store.frame(store.session(),original,emptyList())
        store.freeze()
        assertEquals(store.session(),requested)
        assertTrue(store.ui.capturing);assertTrue(store.ui.pending);assertFalse(store.ui.frozen)
        val later=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888)
        store.frame(requested,later,listOf(Found(1,"cup",.9f,Box(.1f,.1f,.3f,.3f))))
        assertSame(original,store.ui.bitmap);assertTrue(store.ui.found.isEmpty())
        store.captureFailed(requested)
        assertFalse(store.ui.pending);assertFalse(store.ui.capturing)
        assertNotNull(store.ui.message)
        clear(store)
    }
    @Test fun aCancelledStillCannotReplaceTheNewLiveSession() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        store.capturePhoto={}
        store.frame(store.session(),Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888),emptyList())
        val generation=store.session();store.freeze();store.live()
        val stale=Bitmap.createBitmap(60,80,Bitmap.Config.ARGB_8888)
        store.captured(generation,stale)
        assertTrue(stale.isRecycled)
        assertFalse(store.ui.frozen);assertFalse(store.ui.pending);assertFalse(store.ui.capturing)
        assertNull(store.ui.bitmap)
        clear(store)
    }
    @Test fun successfulStillRunsRecognitionOnTheNewPhoto() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        val still=Bitmap.createBitmap(160,120,Bitmap.Config.ARGB_8888)
        val done=java.util.concurrent.CountDownLatch(1)
        store.photoInference={ bitmap ->
            assertSame(still,bitmap);done.countDown()
            DetectionBatch(listOf(Found(1,"cup",.9f,Box(.1f,.1f,.6f,.7f))),true)
        }
        store.capturePhoto={ generation -> store.captured(generation,still) }
        store.frame(store.session(),Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888),emptyList())
        store.freeze()
        assertTrue(store.ui.frozen);assertFalse(store.ui.capturing);assertSame(still,store.ui.bitmap)
        assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS))
        repeat(100) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            if(store.ui.pending)Thread.sleep(10)
        }
        assertFalse(store.ui.pending);assertEquals("cup",store.ui.found.single().key)
        assertSame(still,store.ui.bitmap)
        clear(store)
    }
    @Test fun capturedPixelsAreCroppedAndRotatedWithTheSameGeometryAsAnalysis() {
        val source=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.BLACK)
        source.setPixel(1,0,Color.RED);source.setPixel(2,0,Color.GREEN)
        source.setPixel(1,1,Color.BLUE);source.setPixel(2,1,Color.YELLOW)
        val result=orientCameraBitmap(source,Rect(1,0,3,2),90,1920)
        assertEquals(2,result.width);assertEquals(2,result.height)
        assertEquals(Color.BLUE,result.getPixel(0,0));assertEquals(Color.RED,result.getPixel(1,0))
        assertEquals(Color.YELLOW,result.getPixel(0,1));assertEquals(Color.GREEN,result.getPixel(1,1))
        assertFalse(result.isRecycled)
    }
    @Test fun largePhotoIsLimitedWithoutChangingItsAspectRatio() {
        val source=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
        val result=orientCameraBitmap(source,Rect(0,0,400,200),90,120)
        assertEquals(60,result.width);assertEquals(120,result.height)
        assertFalse(result.isRecycled)
    }
    private fun clear(store: LearningStore) {
        androidx.lifecycle.ViewModelStore().apply { put("capture-test",store);clear() }
    }
}
