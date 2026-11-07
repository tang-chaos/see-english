package com.codex.englishcamera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/** The annotated photo here is an explicit UI fixture, not a native model inference test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h851dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InterfaceTest {
    @Test fun permissionPhotoCardAndVocabularyScreensRenderAndRespond() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            val view = activity.window.decorView
            fun layout() {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                view.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1702,View.MeasureSpec.EXACTLY))
                view.layout(0,0,786,1702)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
            }
            fun compose(root: View): View? {
                if (root.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return root
                if (root is ViewGroup) for(i in 0 until root.childCount) compose(root.getChildAt(i))?.let { return it }
                return null
            }
            layout()
            fun roots(): List<View> {
                val klass=Class.forName("android.view.WindowManagerGlobal")
                val global=klass.getDeclaredMethod("getInstance").invoke(null)
                val field=klass.getDeclaredField("mViews").apply { isAccessible=true }
                @Suppress("UNCHECKED_CAST") val windows=field.get(global) as List<View>
                return (listOf(view)+windows+listOfNotNull(ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView)).distinct().reversed()
            }
            fun nodes(merged: Boolean) = roots().mapNotNull { compose(it) }.flatMap {
                    (it.javaClass.getMethod("getSemanticsOwner").invoke(it) as SemanticsOwner).getAllSemanticsNodes(mergingEnabled = merged)
                }
            fun texts() = nodes(false).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
            fun click(label: String) {
                val node = nodes(true).firstOrNull {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true && it.config.getOrNull(SemanticsActions.OnClick) != null
                } ?: error("No clickable '$label'; visible text: ${texts()}")
                assertTrue(node.config[SemanticsActions.OnClick].action!!.invoke())
                layout()
            }
            fun screenshot(name: String, modal: Boolean=false) {
                layout()
                val bitmap = Bitmap.createBitmap(786,1702,Bitmap.Config.ARGB_8888)
                val target=if(modal)roots().firstOrNull { it!==view && compose(it)!=null } ?: view else view
                if(modal) {
                    target.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1702,View.MeasureSpec.EXACTLY))
                    target.layout(0,0,786,1702)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
                }
                target.draw(Canvas(bitmap))
                val file = File("build/verification/$name.png").apply { parentFile?.mkdirs() }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                assertTrue(file.length() > 1000)
            }
            assertTrue(texts().toString(), texts().contains("允许使用相机"))
            assertTrue(texts().contains("相机识别"))
            assertTrue(texts().contains("识别历史"))
            assertEquals(1,texts().count { it=="模型识别" })
            val removed=listOf("相机学习","使用指南","示例","先体验示例照片","读字识词","查看文字","只读文字","广识更多 · 试用","重新广识","8B离线","8B精识别","8B词卡")
            assertTrue(texts().none { it in removed })
            screenshot("23-beautified-permission")
            val photo = Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888).apply {
                Canvas(this).apply {
                    drawColor(android.graphics.Color.rgb(225,235,225))
                    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    paint.color=android.graphics.Color.rgb(85,135,115);drawRect(60f,390f,370f,600f,paint)
                    paint.color=android.graphics.Color.rgb(220,190,120);drawRect(320f,210f,525f,600f,paint)
                    paint.color=android.graphics.Color.rgb(115,165,150);drawRect(503f,145f,726f,600f,paint)
                    paint.color=android.graphics.Color.rgb(190,180,155);drawRect(725f,395f,910f,600f,paint)
                    paint.color=android.graphics.Color.DKGRAY;paint.textSize=28f
                    drawText("UI layout fixture - no model inference",28f,52f,paint)
                }
            }
            activity.store.ui = activity.store.ui.copy(frozen=true, bitmap=photo, hasFrame=true,
                found=listOf(Found(1,"cat",.95f,Box(.064f,.65f,.37f,1f)),
                    Found(2,"dog",.94f,Box(.32f,.36f,.525f,1f)),
                    Found(3,"dog",.93f,Box(.503f,.245f,.726f,1f)),
                    Found(4,"cat",.92f,Box(.725f,.665f,.91f,1f))))
            screenshot("02-frozen-ui-fixture")
            assertTrue(texts().toString(),texts().contains("cat"))
            val hiddenBox=nodes(true).firstOrNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("物品框，点击查看词卡") }==true &&
                    node.config.getOrNull(SemanticsActions.OnClick)!=null
            } ?: error("Detected objects beyond the visible vocabulary cards must remain accessible")
            assertTrue(hiddenBox.config[SemanticsActions.OnClick].action!!.invoke())
            assertNotNull(activity.store.ui.selected)
            activity.store.select(null);layout()
            val camera=nodes(false).first { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("相机取景区")==true }.boundsInRoot
            assertTrue("Camera must occupy at least 75% of screen height: $camera",camera.height>=1702*.75f)
            assertTrue("Camera must span screen width: $camera",camera.width>=780f)
            File("build/verification/camera-layout.json").writeText("{\"viewport_width_px\":${camera.width},\"viewport_height_px\":${camera.height},\"screen_height_px\":1702}")
            click("全屏")
            assertFalse(texts().contains("生词本"))
            screenshot("03-camera-immersive")
            click("退出全屏")
            click("单词列表")
            assertTrue(texts().toString(),texts().any { it.contains("2 个单词") })
            click("cat")
            click("加入生词本")
            assertTrue("cat" in activity.store.ui.saved)
            assertTrue(texts().contains("听单词发音"))
            assertTrue(texts().contains("移出生词本"))
            assertTrue(texts().none { it in listOf("展开简单例句","收起例句","听例句") })
            screenshot("26-word-card-without-example",modal=true)
            activity.store.select(null)
            activity.store.notice(null)
            layout()
            val landscapeState=activity.store.ui
            val portrait=Bitmap.createBitmap(photo,550,0,450,600)
            activity.store.ui=activity.store.ui.copy(bitmap=portrait,
                found=listOf(Found(1,"dog",.94f,Box(.12f,.245f,.72f,1f))))
            screenshot("25-beautified-portrait-camera-ui-fixture")
            // Deliberately drawn layout fixture, not an OCR or model prediction example.
            val packageFixture=Bitmap.createBitmap(480,640,Bitmap.Config.ARGB_8888)
            Canvas(packageFixture).apply {
                drawColor(android.graphics.Color.rgb(237,235,224))
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                paint.color=android.graphics.Color.WHITE;drawRect(120f,160f,360f,500f,paint)
                paint.color=android.graphics.Color.rgb(50,128,180);drawRect(120f,160f,360f,215f,paint)
                paint.color=android.graphics.Color.DKGRAY;paint.textSize=33f;drawText("MILK",185f,320f,paint)
                paint.textSize=22f;drawText("250 mL",195f,430f,paint)
                paint.textSize=17f;drawText("UI layout fixture",15f,30f,paint)
            }
            activity.store.ui=activity.store.ui.copy(bitmap=packageFixture,found=listOf(Found(1,"milk",0f,Box(.25f,.25f,.75f,.79f),evidence="包装文字")))
            layout()
            assertTrue(texts().contains("包装文字"));assertTrue(texts().contains("milk"))
            screenshot("10-package-text-ui-fixture")
            assertTrue(texts().none { it in removed })
            // Explicit model-output fixture through the production result callback; not native inference.
            activity.store.offlineInference={ _,_->VlmAnswer("fixture",listOf(VlmTerm("hand","手"),VlmTerm("cat","猫")),1000) }
            activity.store.offlineVision()
            repeat(50) { layout();if(activity.store.ui.pending)Thread.sleep(10) }
            assertFalse(activity.store.ui.pending)
            assertEquals(2,activity.store.ui.history.size)
            assertFalse("user:hand" in activity.store.ui.saved)
            assertEquals(1,texts().count { it=="模型识别" })
            click("模型识别")
            activity.store.ui=activity.store.ui.copy(modelReady=true);layout()
            click("收起识别设置")
            assertFalse(texts().contains("导入离线识别文件"))
            assertFalse(texts().contains("fixture"))
            click("查看原始回答")
            assertTrue(texts().contains("fixture"))
            click("收起原始回答")
            screenshot("24-beautified-model-panel-ui-fixture",modal=true)
            assertTrue(texts().contains("hand"));assertTrue(texts().contains("暂未收录音标"))
            click("加入生词本")
            assertTrue("user:hand" in activity.store.ui.saved)
            activity.store.page(2);layout()
            assertTrue(texts().contains("hand"));assertTrue(texts().contains("手"))
            assertTrue(texts().contains("共 2 个历史单词"))
            screenshot("22-beautified-history-ui-fixture")
            click("清空历史");click("取消")
            assertEquals(2,activity.store.ui.history.size)
            click("清空历史");click("确认清空")
            assertTrue(activity.store.ui.history.isEmpty())
            activity.store.notice(null)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
            activity.store.page(1);layout()
            assertTrue(texts().contains("hand"));assertTrue(texts().contains("cat"))
            screenshot("21-beautified-favorites-ui-fixture")
            activity.store.page(0);layout()
            activity.store.ui=landscapeState.copy(saved=activity.store.ui.saved,reviewed=activity.store.ui.reviewed)
            layout()
            click("生词本")
            assertTrue(texts().toString(),texts().contains("cat"))
            screenshot("03-vocabulary")
            click("大字")
            screenshot("04-large-vocabulary")
            assertTrue(texts().none { it in removed })
            click("相机识别")
            screenshot("20-beautified-camera-ui-fixture")
            // Opt-in documentation rendering: genuine desktop outputs through the production
            // callback and Compose screens. This is not Android native-model inference.
            if(System.getenv("SEE_ENGLISH_RENDER_DOCS")=="1") {
                fun captureWindows(name: String) {
                    layout()
                    val output=Bitmap.createBitmap(786,1702,Bitmap.Config.ARGB_8888)
                    val canvas=Canvas(output)
                    roots().asReversed().forEach { root ->
                        root.measure(View.MeasureSpec.makeMeasureSpec(786,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1702,View.MeasureSpec.EXACTLY))
                        root.layout(0,0,786,1702)
                    }
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                    roots().asReversed().forEach { it.draw(canvas) }
                    File("build/verification/$name.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG,100,it) }
                }
                listOf("desk","kitchen").forEach { scene ->
                    activity.store.ui=LearningState(page=0)
                    activity.store.clearHistory();activity.store.notice(null)
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));layout()
                    val record=org.json.JSONObject(File("../docs/media/$scene-result.json").readText())
                    val original=BitmapFactory.decodeFile(File("../docs/media/$scene-original.jpg").absolutePath)
                    val raw=record.getString("output")
                    val terms=parseVlmTerms(raw)
                    assertTrue(terms.isNotEmpty())
                    val elapsed=((record.getDouble("load_seconds")+record.getDouble("inference_seconds"))*1000).toLong()
                    activity.store.ui=LearningState(frozen=true,bitmap=original,hasFrame=true,modelReady=true)
                    activity.store.offlineInference={ _,_->VlmAnswer(raw,terms,elapsed) }
                    activity.store.offlineVision()
                    repeat(50) { layout();if(activity.store.ui.pending)Thread.sleep(10) }
                    assertFalse(activity.store.ui.pending)
                    assertEquals(terms,activity.store.ui.vlmAnswer!!.terms)
                    assertTrue(activity.store.ui.found.isEmpty()) // Do not invent VLM boxes.
                    activity.store.notice(null)
                    captureWindows("doc-$scene-camera-replay")
                    click("模型识别")
                    activity.store.ui=activity.store.ui.copy(modelReady=true);layout()
                    captureWindows("doc-$scene-model-replay")
                    activity.store.page(2);layout()
                    val word=terms.mapNotNull { it.word(activity.store.lexicon) }.firstOrNull {
                        it.key==if(scene=="desk")"laptop" else "avocado"
                    } ?: terms.mapNotNull { it.word(activity.store.lexicon) }.first()
                    activity.store.select(word.key);layout()
                    assertTrue(texts().contains(word.english));assertTrue(texts().contains(word.ipa))
                    captureWindows("doc-$scene-word-replay")
                    activity.store.select(null);layout()
                    File("build/verification/doc-$scene-selection.json").writeText(org.json.JSONObject()
                        .put("key",word.key).put("english",word.english).put("ipa",word.ipa).put("chinese",word.chinese)
                        .put("desktop_inference_replay",true).put("physical_device",false).toString(2))
                }
            }
        } finally { controller.pause().stop().destroy() }
    }
}
