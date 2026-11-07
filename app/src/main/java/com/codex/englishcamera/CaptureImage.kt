package com.codex.englishcamera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

/** The bitmap returned by toBitmap is owned here; ImageProxy remains owned by its callback. */
internal fun cameraBitmap(image: ImageProxy,maxEdge: Int=Int.MAX_VALUE): Bitmap =
    orientCameraBitmap(image.toBitmap(),image.cropRect,image.imageInfo.rotationDegrees,maxEdge)

internal fun orientCameraBitmap(source: Bitmap,crop: Rect,rotation: Int,maxEdge: Int): Bitmap {
    require(maxEdge>0 && crop.width()>0 && crop.height()>0)
    val cropped=Bitmap.createBitmap(source,crop.left,crop.top,crop.width(),crop.height())
    val rotated=Bitmap.createBitmap(cropped,0,0,cropped.width,cropped.height,
        Matrix().apply { postRotate(rotation.toFloat()) },true)
    if(source!==cropped && source!==rotated)source.recycle()
    if(cropped!==rotated)cropped.recycle()
    val edge=maxOf(rotated.width,rotated.height)
    if(edge<=maxEdge)return rotated
    val scale=maxEdge.toFloat()/edge
    val limited=Bitmap.createScaledBitmap(rotated,(rotated.width*scale).roundToInt().coerceAtLeast(1),
        (rotated.height*scale).roundToInt().coerceAtLeast(1),true)
    if(limited!==rotated)rotated.recycle()
    return limited
}
