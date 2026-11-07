package com.codex.englishcamera

data class DetectionBatch(val found: List<Found>,val refined: Boolean,
                          val warning: String?=null,val diagnostics: String?=null)

/** MediaPipe already performs model NMS; also reject invalid or repeated display boxes. */
fun suppressDuplicates(items: List<Found>,threshold: Float=.5f,limit: Int=32): List<Found> {
    val kept=mutableListOf<Found>()
    items.filter { it.score.isFinite() && it.score in 0f..1f && it.box.width>0 && it.box.height>0 }
        .sortedByDescending { it.score }.forEach { item ->
            if(kept.none { it.key==item.key && (it.box.iou(item.box)>threshold || it.box.containment(item.box)>=.85f) })kept+=item
        }
    return kept.take(limit).mapIndexed { index,item -> item.copy(id=index+1) }
}

/** Confirm moderate-confidence live labels across observations, without retaining stale boxes. */
class ConfirmationGate {
    private val recent=ArrayDeque<Pair<Long,List<Found>>>()
    fun update(current: List<Found>,nowMs: Long=System.nanoTime()/1_000_000): List<Found> {
        while(recent.isNotEmpty() && nowMs-recent.first().first>6000)recent.removeFirst()
        val confirmed=current.filter { item -> item.score>=.85f || recent.any { (_,items) ->
            items.any { it.key==item.key && it.box.iou(item.box)>=.3f }
        } }
        recent.addLast(nowMs to current)
        while(recent.size>2)recent.removeFirst()
        return confirmed
    }
    fun reset() { recent.clear() }
}
