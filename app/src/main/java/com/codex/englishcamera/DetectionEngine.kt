package com.codex.englishcamera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import org.json.JSONObject
import java.util.Locale

/** One small COCO detector for camera responsiveness. Fine-grained photos use the separate 8B engine. */
class DetectionEngine(private val context: Context,private val lexicon: Lexicon): AutoCloseable {
    private var detector: ObjectDetector?=null
    @Synchronized fun detect(bitmap: Bitmap,detailed: Boolean=true): DetectionBatch {
        val active=detector ?: ObjectDetector.createFromOptions(context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
                .setRunningMode(RunningMode.IMAGE).setMaxResults(32).setScoreThreshold(.48f).build()
        ).also { detector=it }
        val image=inferenceImage(bitmap)
        val found=try {
            suppressDuplicates(active.detect(image).detections().mapIndexedNotNull { index,detection ->
                val category=detection.categories().maxByOrNull { it.score() } ?: return@mapIndexedNotNull null
                if(!category.score().isFinite() || category.score() !in 0f..1f)return@mapIndexedNotNull null
                val key=category.categoryName().lowercase(Locale.ROOT)
                if(lexicon[key]==null)return@mapIndexedNotNull null
                val b=detection.boundingBox()
                val box=Box((b.left/bitmap.width).coerceIn(0f,1f),(b.top/bitmap.height).coerceIn(0f,1f),
                    (b.right/bitmap.width).coerceIn(0f,1f),(b.bottom/bitmap.height).coerceIn(0f,1f))
                if(box.width<=0 || box.height<=0)null else Found(index+1,key,category.score(),box)
            })
        } finally { image.close() }
        val trace=if(detailed)JSONObject().put("pipeline","EfficientDet-Lite0 snapshot")
            .put("supported_classes",80).put("results",foundJson(found)).toString(2) else null
        return DetectionBatch(found,detailed,diagnostics=trace)
    }
    @Synchronized override fun close() { runCatching { detector?.close() };detector=null }
}

/** MPImage.close() owns its bitmap: never hand it the displayed camera frame. */
internal fun inferenceImage(bitmap: Bitmap)=BitmapImageBuilder(bitmap.copy(Bitmap.Config.ARGB_8888,false)).build()
