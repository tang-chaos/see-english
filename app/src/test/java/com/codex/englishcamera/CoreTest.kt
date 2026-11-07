package com.codex.englishcamera

import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun fitCenterAccountsForLetterboxing() {
        val viewport = fitRect(400, 300, 300f, 600f)
        assertEquals(Box(0f,187.5f,300f,412.5f), viewport)
        assertEquals(Box(150f,300f,300f,412.5f), mapBox(Box(.5f,.5f,1f,1f), viewport))
    }
    @Test fun portraitAndLandscapeCoordinatesStayOnImage() {
        listOf(300 to 400, 400 to 300, 1600 to 900).forEach { (w,h) ->
            val viewport = fitRect(w,h,393f,420f)
            assertTrue(viewport.left >= 0 && viewport.top >= 0 && viewport.right <= 393 && viewport.bottom <= 420)
            assertEquals(viewport, mapBox(Box(0f,0f,1f,1f), viewport))
        }
    }
    @Test fun nearbySameClassObjectsHaveDifferentStableIds() {
        val tracker = Stabilizer()
        val a = Found(0,"cup",.9f,Box(.1f,.1f,.3f,.3f))
        val b = Found(0,"cup",.8f,Box(.6f,.1f,.8f,.3f))
        val first = tracker.update(listOf(a,b))
        val next = tracker.update(listOf(b.copy(box=Box(.61f,.1f,.81f,.3f)),a))
        assertEquals(2,next.map { it.id }.toSet().size)
        assertEquals(first.first().id, next.first { it.box.left < .5 }.id)
        assertEquals(first.last().id, next.first { it.box.left > .5 }.id)
        assertTrue(tracker.update(emptyList()).isEmpty())
    }
    @Test fun changedClassNeverInheritsPreviousIdentity() {
        val tracker = Stabilizer()
        val a = Found(0,"cup",.9f,Box(.1f,.1f,.3f,.3f))
        val first = tracker.update(listOf(a)).single()
        val next = tracker.update(listOf(a.copy(key="bottle"))).single()
        assertNotEquals(first.id,next.id)
    }
    @Test fun labelsRemainInsideSmallViewportAndAvoidOccupiedSpace() {
        val first = placeLabel(Box(90f,90f,100f,100f),emptyList(),100f,100f,70f,30f)
        val second = placeLabel(Box(90f,90f,100f,100f),listOf(first),100f,100f,70f,30f)
        for (box in listOf(first,second)) {
            assertTrue(box.left >= 0 && box.top >= 0 && box.right <= 100 && box.bottom <= 100)
        }
        assertEquals(0f, first.iou(second), 0.001f)
    }
    @Test fun vocabularyRejectsMissingPronunciationsAndDuplicateKeys() {
        assertThrows(IllegalArgumentException::class.java) { Lexicon.parse("cup|cup||杯子") }
        assertThrows(IllegalArgumentException::class.java) { Lexicon.parse("cup|cup|/kʌp/|杯子\ncup|cup|/kʌp/|杯子") }
    }
}
