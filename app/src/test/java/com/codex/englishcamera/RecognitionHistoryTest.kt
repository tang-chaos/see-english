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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class RecognitionHistoryTest {
    private fun store(): LearningStore {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear().commit()
        return LearningStore(app)
    }
    private fun close(vararg stores: LearningStore) { stores.forEach { store -> ViewModelStore().apply { put("test",store);clear() } } }
    private fun idleUntil(done: ()->Boolean) {
        repeat(200) { shadowOf(Looper.getMainLooper()).idle();if(done())return;Thread.sleep(10) }
        assertTrue(done())
    }
    @Test fun realtimeOnlyRecordsConfirmedDisplayedWordsAndIgnoresStaleFrames() {
        val store=store();val photo=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888)
        val cup=Found(1,"cup",.7f,Box(.1f,.1f,.5f,.5f));val generation=store.session()
        store.frame(generation,photo,listOf(cup));assertTrue(store.ui.history.isEmpty())
        store.frame(generation,photo,listOf(cup));assertEquals(listOf("cup"),store.ui.history.map { it.key })
        val timestamp=store.ui.history.single().firstSeenAt
        store.frame(generation,photo,listOf(cup));assertEquals(timestamp,store.ui.history.single().firstSeenAt)
        store.live();store.frame(generation,photo,listOf(cup.copy(key="cat",score=.99f)))
        assertEquals(listOf("cup"),store.ui.history.map { it.key });assertTrue(store.ui.saved.isEmpty())
        close(store)
    }
    @Test fun successfulSnapshotAutomaticallyRecordsItsWords() {
        val store=store();val photo=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888)
        store.photoInference={ DetectionBatch(listOf(Found(1,"dumbbell",.9f,Box(.1f,.1f,.5f,.5f))),true) }
        store.frame(store.session(),photo,emptyList());store.freeze();idleUntil { !store.ui.pending }
        assertEquals(listOf("dumbbell"),store.ui.history.map { it.key });assertTrue(store.ui.saved.isEmpty())
        close(store)
    }
    @Test fun photoUnderstandingRecordsUnknownWordsAndPersistsWithoutFavoritingThem() {
        val store=store();store.ui=store.ui.copy(frozen=true,bitmap=Bitmap.createBitmap(30,40,Bitmap.Config.ARGB_8888))
        store.offlineInference={ _,_-> VlmAnswer("fixture",listOf(VlmTerm("hand","手"),VlmTerm(" HAND ","手"),VlmTerm("cup","杯子")),1) }
        store.offlineVision();idleUntil { !store.ui.pending }
        assertEquals(2,store.ui.history.size);assertTrue(store.ui.saved.isEmpty())
        val restored=LearningStore(RuntimeEnvironment.getApplication())
        assertEquals(store.ui.history,restored.ui.history)
        assertEquals("手",restored.word("user:hand")!!.chinese);assertEquals("",restored.word("user:hand")!!.ipa)
        close(store,restored)
    }
    @Test fun duplicatesKeepFirstTimestampAndNewWordsAppearBeforeOlderEntries() {
        val store=store()
        store.recordRecognizedTerms(listOf(VlmTerm("cup","杯子"),VlmTerm("charging cable","充电线")),1000)
        val initial=store.ui.history
        store.recordRecognizedTerms(listOf(VlmTerm("CUP","杯子"),VlmTerm("ＣＵＰ","杯子"),VlmTerm(" Charging   Cable ","充电线")),2000)
        assertEquals(initial,store.ui.history)
        store.recordRecognizedTerms(listOf(VlmTerm("book","书")),3000)
        assertEquals("book",store.ui.history.first().key);assertEquals(3,store.ui.history.size)
        assertEquals(1000L,store.ui.history.last().firstSeenAt)
        close(store)
    }
    @Test fun clearingHistoryKeepsFavoritesAndRemovingFavoritesDoesNotChangeHistory() {
        val store=store();val hand=VlmTerm("hand","手")
        store.recordRecognizedTerms(listOf(hand));store.saveTerm(hand);store.review("user:hand")
        store.clearHistory();assertTrue(store.ui.history.isEmpty());assertTrue("user:hand" in store.ui.saved)
        val restored=LearningStore(RuntimeEnvironment.getApplication())
        assertTrue(restored.ui.history.isEmpty());assertEquals("手",restored.word("user:hand")!!.chinese)
        assertTrue("user:hand" in restored.ui.reviewed)
        restored.recordRecognizedTerms(listOf(hand));restored.saveTerm(hand)
        assertTrue(restored.ui.saved.isEmpty());assertEquals(listOf("user:hand"),restored.ui.history.map { it.key })
        close(store,restored)
    }
    @Test fun upgradingDoesNotInventHistoryFromOldFavoritesAndBadHistoryIsIgnored() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear().putStringSet("saved",setOf("cup"))
            .putString("history","invalid json").commit()
        val store=LearningStore(app)
        assertEquals(setOf("cup"),store.ui.saved);assertTrue(store.ui.history.isEmpty())
        store.recordRecognizedTerms(listOf(VlmTerm(" ","")));assertTrue(store.ui.history.isEmpty())
        close(store)
    }
}
