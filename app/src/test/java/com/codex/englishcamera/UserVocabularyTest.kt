package com.codex.englishcamera

import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UserVocabularyTest {
    private fun close(vararg stores: LearningStore) { stores.forEach { store -> ViewModelStore().apply { put("test",store);clear() } } }
    @Test fun unlistedRecognizedWordSurvivesRestartWithItsMeaningAndReviewState() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear().commit()
        val first=LearningStore(app)
        assertNull(first.lexicon["hand"])
        first.saveTerm(VlmTerm("hand","手"))
        val key=first.termKey(VlmTerm("hand","手"))
        first.review(key)
        val restored=LearningStore(app)
        assertTrue(key in restored.ui.saved);assertTrue(key in restored.ui.reviewed)
        assertEquals("hand",restored.word(key)!!.english)
        assertEquals("手",restored.word(key)!!.chinese)
        assertEquals("",restored.word(key)!!.ipa) // No invented pronunciation.
        assertEquals(519,restored.lexicon.all.size) // Model labels stay independent.
        close(first,restored)
    }
    @Test fun caseAndWhitespaceDoNotCreateDuplicateFavoritesAndRemovalKeepsOtherWords() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear().commit()
        val first=LearningStore(app)
        first.save("cup");first.saveTerm(VlmTerm("charging cable","充电线"))
        val key=first.termKey(VlmTerm(" Charging   Cable ","充电线"))
        assertTrue(key in first.ui.saved);first.review(key)
        first.saveTerm(VlmTerm(" Charging   Cable ","充电线"))
        assertEquals(setOf("cup"),first.ui.saved);assertFalse(key in first.ui.reviewed)
        val restored=LearningStore(app)
        assertEquals(setOf("cup"),restored.ui.saved)
        restored.saveTerm(VlmTerm("charging cable","充电线"))
        assertEquals(2,restored.ui.saved.size)
        close(first,restored)
    }
    @Test fun legacyFavoritesSurviveInvalidCustomDataAndBlankNamesAreRejected() {
        val app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("learning",0).edit().clear()
            .putStringSet("saved",setOf("cup","user:missing"))
            .putString("userWords","not valid json").commit()
        val store=LearningStore(app)
        assertEquals(setOf("cup"),store.ui.saved)
        store.saveTerm(VlmTerm(" ","空名称"));assertEquals(setOf("cup"),store.ui.saved)
        close(store)
    }
}
