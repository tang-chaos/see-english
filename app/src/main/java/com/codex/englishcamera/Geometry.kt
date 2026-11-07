package com.codex.englishcamera

import kotlin.math.max
import kotlin.math.min

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = (right - left).coerceAtLeast(0f)
    val height get() = (bottom - top).coerceAtLeast(0f)
    val cx get() = (left + right) / 2
    val cy get() = (top + bottom) / 2
    fun iou(other: Box): Float {
        val intersection = max(0f, min(right, other.right) - max(left, other.left)) *
            max(0f, min(bottom, other.bottom) - max(top, other.top))
        val union = width * height + other.width * other.height - intersection
        return if (union > 0) intersection / union else 0f
    }
    /** Fraction of the smaller box covered by the overlap. A large surface seen at two
     *  scales has a low IoU but a high containment, so both measures are needed. */
    fun containment(other: Box): Float {
        val intersection = max(0f, min(right, other.right) - max(left, other.left)) *
            max(0f, min(bottom, other.bottom) - max(top, other.top))
        val smaller = min(width * height, other.width * other.height)
        return if (smaller > 0f) intersection / smaller else 0f
    }
    fun blend(next: Box) = Box(left * .25f + next.left * .75f, top * .25f + next.top * .75f,
        right * .25f + next.right * .75f, bottom * .25f + next.bottom * .75f)
}

data class Found(val id: Int, val key: String, val score: Float, val box: Box,val evidence: String?=null)

/** Greedy, one-to-one, same-class association. It is label stabilization, not an identity tracker. */
class Stabilizer {
    private var previous = emptyList<Found>()
    private var counter = 0
    fun update(current: List<Found>): List<Found> {
        val unused = previous.toMutableList()
        previous = current.sortedByDescending { it.score }.map { item ->
            val match = unused.filter { it.key == item.key }.maxByOrNull { it.box.iou(item.box) }
            if (match != null && match.box.iou(item.box) >= .25f) {
                unused.remove(match)
                item.copy(id = match.id, box = match.box.blend(item.box))
            } else item.copy(id = ++counter)
        }
        // No stale objects retained when a frame contains no detections.
        return previous
    }
    fun reset() { previous = emptyList() }
}

/** FIT_CENTER mapping; source dimensions must be the already-rotated image dimensions. */
fun fitRect(sourceWidth: Int, sourceHeight: Int, width: Float, height: Float): Box {
    require(sourceWidth > 0 && sourceHeight > 0 && width > 0 && height > 0)
    val scale = min(width / sourceWidth, height / sourceHeight)
    val w = sourceWidth * scale
    val h = sourceHeight * scale
    return Box((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2)
}

fun mapBox(normalized: Box, viewport: Box) = Box(viewport.left + normalized.left * viewport.width,
    viewport.top + normalized.top * viewport.height, viewport.left + normalized.right * viewport.width,
    viewport.top + normalized.bottom * viewport.height)

fun placeLabel(anchor: Box, occupied: List<Box>, width: Float, height: Float, cardWidth: Float, cardHeight: Float): Box {
    val cw = min(cardWidth, width)
    val ch = min(cardHeight, height)
    val x = anchor.left.coerceIn(0f, (width-cw).coerceAtLeast(0f))
    val ys = listOf(anchor.top-ch-6, anchor.bottom+6, anchor.top) +
        (0..8).map { it * (height-ch).coerceAtLeast(0f) / 8 }
    val candidates = ys.map { y ->
        val top = y.coerceIn(0f, (height-ch).coerceAtLeast(0f))
        Box(x, top, x+cw, top+ch)
    }
    return candidates.minWith(compareBy<Box> { candidate -> occupied.sumOf { candidate.iou(it).toDouble() } }
        .thenBy { candidate ->
            val dx=(candidate.cx-anchor.cx)/width.coerceAtLeast(1f)
            val dy=(candidate.cy-anchor.cy)/height.coerceAtLeast(1f)
            candidate.iou(anchor)*2+dx*dx+dy*dy
        })
}
