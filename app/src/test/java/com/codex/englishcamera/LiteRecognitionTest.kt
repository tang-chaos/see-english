package com.codex.englishcamera

import org.junit.Assert.*
import org.junit.Test

class LiteRecognitionTest {
    private val cup=Found(1,"cup",.7f,Box(.1f,.1f,.4f,.4f))
    @Test fun repeatedLiveObservationConfirmsWithoutKeepingStaleResults() {
        val gate=ConfirmationGate()
        assertTrue(gate.update(listOf(cup),0).isEmpty())
        assertEquals(1,gate.update(listOf(cup),100).size)
        assertTrue(gate.update(emptyList(),200).isEmpty())
        assertTrue(gate.update(listOf(cup),7000).isEmpty())
    }
    @Test fun duplicateBoxesAreRemovedButDifferentObjectsAndClassesSurvive() {
        val second=cup.copy(id=2,box=Box(.6f,.1f,.9f,.4f))
        val other=cup.copy(id=3,key="bottle")
        val invalid=cup.copy(score=Float.NaN)
        assertEquals(3,suppressDuplicates(listOf(cup,cup.copy(score=.8f),second,other,invalid)).size)
    }
}
