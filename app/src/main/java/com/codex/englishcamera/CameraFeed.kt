package com.codex.englishcamera

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Surface
import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

/** Preview and detector share a viewport, rotation and FIT_CENTER presentation. */
@Composable
fun CameraFeed(store: LearningStore, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val generation = store.session()
    AndroidView(factory = { context -> PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FIT_CENTER
        post { previewView = this }
    } }, modifier = modifier.onSizeChanged { previewSize=it })
    val view = previewView
    DisposableEffect(view, owner, generation, previewSize) {
        if (view == null) return@DisposableEffect onDispose { }
        val disposed = AtomicBoolean(false)
        var lastAnalysis = 0L
        val executor = Executors.newSingleThreadExecutor()
        val photoExecutor = Executors.newSingleThreadExecutor()
        var photoRequest: ((Int)->Unit)? = null
        val main = ContextCompat.getMainExecutor(view.context)
        val future = ProcessCameraProvider.getInstance(view.context)
        val rotation = view.display?.rotation ?: Surface.ROTATION_0
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()
        val analysis = ImageAnalysis.Builder().setResolutionSelector(resolution).setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val photoResolution=ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1920,1440),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build()
        val capture=ImageCapture.Builder().setResolutionSelector(photoResolution).setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
        analysis.setAnalyzer(executor) { image ->
            try {
                val now = SystemClock.elapsedRealtime()
                val cadence=320
                if (!disposed.get() && !store.ui.capturing && (store.ui.pending || now-lastAnalysis >= cadence)) {
                    lastAnalysis = now
                    val bitmap = cameraBitmap(image)
                    val detailed = store.ui.pending
                    val batch = store.engine.detect(bitmap,detailed)
                    lastAnalysis=SystemClock.elapsedRealtime()
                    main.execute { if (!disposed.get()) store.frame(generation, bitmap, batch.found, batch.refined, batch.warning) }
                }
            } catch (e: Throwable) {
                if (e !is Exception && e !is LinkageError) throw e
                android.util.Log.e("EnglishCamera", "Camera analysis failed", e)
                analysis.clearAnalyzer()
                main.execute { if (!disposed.get()) store.failure(generation, "识别暂不可用，请点“重试相机”") }
            } finally { image.close() }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { store.stopCamera() }
        }
        owner.lifecycle.addObserver(observer)
        future.addListener({
            if (!disposed.get()) try {
                val provider = future.get()
                preview.setSurfaceProvider(view.surfaceProvider)
                // No setViewPort here. A ViewPort crops every bound use case to the host
                // View's aspect ratio, so the captured photo and analysis frames would be a
                // narrow centre strip of the sensor instead of the full 4:3 frame that the
                // desktop diagnostics run on. Keeping the full frame is what makes phone and
                // desktop results comparable; PreviewView.FIT_CENTER letterboxes it instead.
                fun group(still: Boolean)=UseCaseGroup.Builder().addUseCase(preview).addUseCase(analysis).apply {
                    if(still)addUseCase(capture)
                }.build()
                var photoReady=true
                val camera=try {
                    provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,group(true))
                } catch(error: Exception) {
                    android.util.Log.w("EnglishCamera","Still capture unavailable; using analysis frames",error)
                    provider.unbind(preview,analysis,capture);photoReady=false
                    provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,group(false)).also {
                        store.notice("此设备暂不能同时拍摄清晰照片，定格将使用预览画面")
                    }
                }
                view.setOnTouchListener { _,event ->
                    if(event.action==MotionEvent.ACTION_UP && !store.ui.pending) {
                        val point=view.meteringPointFactory.createPoint(event.x,event.y)
                        camera.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point)
                            .setAutoCancelDuration(3,TimeUnit.SECONDS).build())
                        view.performClick()
                    }
                    true
                }
                if(photoReady) {
                    photoRequest={ requestGeneration ->
                        capture.targetRotation=view.display?.rotation ?: rotation
                        capture.takePicture(photoExecutor,object: ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    if(disposed.get())return
                                    val bitmap=cameraBitmap(image,maxEdge=1920)
                                    main.execute { if(disposed.get())bitmap.recycle() else store.captured(requestGeneration,bitmap) }
                                } catch(error: Exception) {
                                    android.util.Log.w("EnglishCamera","Photo decoding failed",error)
                                    main.execute { if(!disposed.get())store.captureFailed(requestGeneration) }
                                } finally { image.close() }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                android.util.Log.w("EnglishCamera","Still capture failed",exception)
                                main.execute { if(!disposed.get())store.captureFailed(requestGeneration) }
                            }
                        })
                    }
                    store.capturePhoto=photoRequest
                }
            } catch (e: Exception) {
                android.util.Log.e("EnglishCamera", "Camera binding failed", e)
                store.failure(generation, "无法打开后置相机，请检查权限或关闭占用相机的应用")
            }
        }, main)
        onDispose {
            disposed.set(true)
            if(store.capturePhoto===photoRequest)store.capturePhoto=null
            view.setOnTouchListener(null)
            owner.lifecycle.removeObserver(observer)
            analysis.clearAnalyzer()
            if (future.isDone) runCatching { future.get().unbind(preview, analysis,capture) }
            executor.shutdown()
            photoExecutor.shutdown()
        }
    }
}
