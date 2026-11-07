package com.codex.englishcamera

import android.graphics.Bitmap
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class LearningTest {
    @Test fun liteModelLabelsHaveCardsAndTheLargerTextDictionaryRemainsUsefulTo8B() {
        val app=RuntimeEnvironment.getApplication();val lexicon=Lexicon.load(app)
        val bytes=app.assets.open("efficientdet_lite0.tflite").use { it.readBytes() }
        assertEquals("0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb",
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) })
        java.util.zip.ZipFile(java.io.File("src/main/assets/efficientdet_lite0.tflite")).use { zip ->
            val labels=zip.getInputStream(zip.getEntry("labels.txt")).bufferedReader().use { it.readLines() }
                .filter { it.isNotBlank() && it!="???" }.toSet()
            assertEquals(80,labels.size);assertTrue(labels.all { lexicon[it]!=null })
        }
        assertEquals(519,lexicon.all.size)
        assertNotNull(lexicon["milk"]);assertNotNull(lexicon["dumbbell"])
        lexicon.all.forEach { assertTrue(it.ipa.startsWith('/') && it.ipa.endsWith('/'));assertTrue(it.chinese.isNotBlank()) }
    }
    @Test fun modelInputCleanupNeverRecyclesTheDisplayedBitmap() {
        val photo=Bitmap.createBitmap(20,30,Bitmap.Config.ARGB_8888)
        inferenceImage(photo).close()
        assertFalse(photo.isRecycled)
    }
    @Test fun upgradingFromExpandedModePreservesExistingFavoritesWithoutRequiringOldModels() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear().putString("recognitionMode","EXPANDED")
            .putStringSet("saved",setOf("cup")).putStringSet("reviewed",setOf("cup")).commit()
        val store=LearningStore(app)
        assertTrue("cup" in store.ui.saved);assertTrue("cup" in store.ui.reviewed)
        store.save("cup");assertTrue(store.ui.saved.isEmpty());assertTrue(store.ui.reviewed.isEmpty())
        clear(store)
    }
    @Test fun cancelledSnapshotCannotReplaceTheNextCameraSession() {
        val store=LearningStore(RuntimeEnvironment.getApplication())
        val started=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1)
        store.photoInference={ started.countDown();release.await(5,TimeUnit.SECONDS);done.countDown()
            DetectionBatch(listOf(Found(1,"cup",.9f,Box(.1f,.1f,.5f,.5f))),true) }
        store.frame(store.session(),Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888),emptyList())
        store.freeze();assertTrue(store.ui.frozen);assertTrue(started.await(5,TimeUnit.SECONDS))
        store.live();release.countDown();assertTrue(done.await(5,TimeUnit.SECONDS))
        repeat(20) { shadowOf(Looper.getMainLooper()).idle();Thread.sleep(5) }
        assertFalse(store.ui.frozen);assertFalse(store.ui.pending);assertNull(store.ui.bitmap);assertTrue(store.ui.found.isEmpty());assertTrue(store.ui.history.isEmpty())
        clear(store)
    }
    private fun clear(store: LearningStore) { ViewModelStore().apply { put("test",store);clear() } }
}
