package com.codex.englishcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal val Green = Color(0xFF16634E)
internal val Ink = Color(0xFF172C27)
internal val Cream = Color(0xFFF8F7F2)
internal val Mint = Color(0xFFE1EEE7)
internal val Gold = Color(0xFFF5CE75)
private val palette = lightColorScheme(primary = Green, onPrimary = Color.White,
    background = Cream, surface = Cream, surfaceVariant = Mint, onSurface = Ink,
    secondary = Green, onSecondary = Color.White, onSurfaceVariant = Muted,
    secondaryContainer = Mint, onSecondaryContainer = Green, outline = Muted, outlineVariant = Hairline)

@Composable
fun EnglishCameraApp(store: LearningStore) {
    val state = store.ui
    var immersive by rememberSaveable { mutableStateOf(false) }
    val activity=LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=state.page!=0
                isAppearanceLightNavigationBars=!(state.page==0 && immersive)
            }
        }
    }
    MaterialTheme(colorScheme = palette, shapes = Shapes(small=RoundedCornerShape(12.dp),
        medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(28.dp))) {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); store.notice(null) } }
        LaunchedEffect(Unit) { while (true) { delay(800); store.expire() } }
        LaunchedEffect(state.pending, state.frozen) {
            if (state.pending && !state.frozen) {
                delay(20000)
                if (store.ui.pending && !store.ui.frozen) {
                    store.failure(store.session(), "没有取得可定格画面，请重试相机")
                }
            }
        }
        BackHandler(enabled = state.selected != null || state.frozen || state.pending || state.page != 0 || immersive) {
            when {
                state.selected != null -> store.select(null)
                state.page != 0 -> store.page(0)
                immersive -> immersive=false
                else -> store.live()
            }
        }
        Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if(!(state.page==0 && immersive))NavigationBar(containerColor = Color.White, tonalElevation = 0.dp,
                modifier=Modifier.clip(RoundedCornerShape(topStart=24.dp,topEnd=24.dp))) {
                listOf(Triple(0,"相机识别",Glyph.CAMERA),Triple(2,"识别历史",Glyph.HISTORY),Triple(1,"生词本",Glyph.BOOK)).forEach { (index,title,icon) ->
                    NavigationBarItem(selected = state.page == index, onClick = { store.page(index) },
                        colors=NavigationBarItemDefaults.colors(selectedIconColor=Green,selectedTextColor=Green,
                            indicatorColor=Mint,unselectedIconColor=Muted,unselectedTextColor=Muted),
                        icon = { AppIcon(icon,Modifier.size(24.dp),if(state.page==index)Green else Muted) },
                        label = { Text(title,fontSize=12.sp,fontWeight=if(state.page==index)FontWeight.SemiBold else FontWeight.Normal) }, alwaysShowLabel = true)
                }
            }
        }) { padding ->
            if(state.page==0) {
                CameraPage(store,Modifier.fillMaxSize().padding(padding),immersive) { immersive=!immersive }
            } else Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if(state.page==2)"识别历史" else "我的生词本",
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(if(state.page==2)"记录每一次遇见的新单词" else "把遇见的单词，变成认识的单词", color = Muted, fontSize = 12.sp,modifier=Modifier.padding(top=6.dp))
                    }
                    FilterChip(selected = state.large, onClick = { store.large(!state.large) },
                        label = { Text("大字", fontSize = 14.sp) }, modifier = Modifier.heightIn(min = 48.dp))
                }
                if(state.page==2)HistoryPage(store,Modifier.weight(1f)) else SavedPage(store, Modifier.weight(1f))
            }
        }
        state.selected?.let { key -> store.word(key)?.let { WordDialog(store, it) } }
    }
}

@Composable
internal fun DetectionOverlay(store: LearningStore) {
    val state = store.ui
    val bitmap = state.bitmap ?: return
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        if (width <= 0 || height <= 0) return@BoxWithConstraints
        val viewport = fitRect(bitmap.width, bitmap.height, width, height)
        val safeTop=minOf(with(density){112.dp.toPx()}*density.fontScale,height/3)
        val safeBottom=minOf(with(density){if(state.frozen)268.dp.toPx() else 148.dp.toPx()}*density.fontScale,height/3)
        val safeHeight=(height-safeTop-safeBottom).coerceAtLeast(1f)
        val visible = if (state.allLabels) state.found else state.found.distinctBy { it.key }.take(6)
        val occupied = mutableListOf<Box>()
        val labels = visible.mapNotNull { item ->
            val box = mapBox(item.box, viewport)
            val word = store.word(item.key)
            val wordLength=word?.english.orEmpty().length
            val cardWidth = with(density) { (when {
                wordLength>20 -> if(state.large)246.dp else 216.dp
                wordLength>14 -> if(state.large)214.dp else 184.dp
                else -> if(state.large)174.dp else 148.dp
            }).toPx() }
            val cardHeight = with(density) { (if(item.score<.65f || item.evidence!=null) {
                if(state.large)128.dp else 110.dp
            } else if(state.large)106.dp else 92.dp).toPx() } * density.fontScale
            val shifted=Box(box.left,box.top-safeTop,box.right,box.bottom-safeTop)
            val position = placeLabel(shifted, occupied, width, safeHeight, cardWidth, cardHeight)
            // A crowded/large-text viewport may have no room for another complete card.
            // Keep its tappable object box and its word-list entry instead of covering another card.
            if(occupied.any { it.iou(position)>0f })return@mapNotNull null
            occupied += position
            val label=Box(position.left,position.top+safeTop,position.right,position.bottom+safeTop)
            Triple(item, box, label) to word
        }
        val visibleIds=labels.map { it.first.first.id }.toSet()
        Canvas(Modifier.fillMaxSize()) {
            state.found.filter { it.id !in visibleIds }.forEach { item ->
                val b=mapBox(item.box,viewport)
                drawRoundRect(Gold.copy(alpha=.65f),Offset(b.left,b.top),Size(b.width,b.height),CornerRadius(8f),style=Stroke(2f))
            }
            labels.forEach { (geometry, _) ->
                val (_, b, label) = geometry
                drawRoundRect(Gold, Offset(b.left, b.top), Size(b.width,b.height), CornerRadius(10f), style = Stroke(3f))
                drawLine(Gold, Offset(b.cx,b.cy), Offset(label.cx,label.cy), strokeWidth = 2f)
            }
        }
        if(state.frozen)state.found.filter { it.id !in visibleIds }.sortedByDescending { it.box.width*it.box.height }.forEach { item ->
            val box=mapBox(item.box,viewport)
            val word=store.word(item.key)
            if(word!=null)Box(Modifier.offset { IntOffset(box.left.roundToInt(),box.top.roundToInt()) }
                .size(with(density) { box.width.toDp() },with(density) { box.height.toDp() })
                .clickable { store.select(item.key) }.semantics { contentDescription="${word.english}物品框，点击查看词卡" })
        }
        labels.forEach { (geometry, word) ->
            val (item, _, position) = geometry
            if (word != null) Surface(onClick = { store.select(word.key) }, color = Cream.copy(alpha = .97f),
                shape = RoundedCornerShape(18.dp), border=androidx.compose.foundation.BorderStroke(1.dp,Color.White.copy(alpha=.7f)),
                shadowElevation=3.dp, modifier = Modifier.offset { IntOffset(position.left.roundToInt(), position.top.roundToInt()) }
                    .width(with(density) { position.width.toDp() })
                    .semantics { contentDescription = "${word.english}，${word.ipa}，${word.chinese}，点击学习" }) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if(item.evidence!=null || item.score<.65f)Text(item.evidence ?: "可能是",color=Green,fontSize=if(state.large)12.sp else 10.sp)
                    Text(word.english, fontSize = if (state.large) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(word.ipa.ifBlank { "暂未收录音标" }, fontSize = if (state.large) 16.sp else 13.sp, color = Green, maxLines = 1)
                    Text(word.chinese, fontSize = if (state.large) 17.sp else 14.sp, color = Ink, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WordDialog(store: LearningStore, word: Word) {
    val state = store.ui
    AlertDialog(onDismissRequest = { store.select(null) }, containerColor = Cream,
        title = { Text(word.english, fontWeight = FontWeight.Bold, fontSize = if (state.large) 34.sp else 29.sp) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(word.ipa.ifBlank { "暂未收录音标" }, color = Green, fontSize = if (state.large) 23.sp else 20.sp)
            Text(word.chinese, fontSize = if (state.large) 23.sp else 20.sp)
            Text("识别结果供学习参考；不准确时请换角度或在词表中查找。", color = Color(0xFF5A7067), fontSize = 12.sp)
            Button(onClick = { store.speak(word.english) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("听单词发音") }
            OutlinedButton(onClick = { store.save(word.key) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (word.key in state.saved) "移出生词本" else "加入生词本")
            }
            if (word.key in state.saved) TextButton(onClick = { store.review(word.key) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (word.key in state.reviewed) "重新练习" else "我记住了")
            }
        } },
        confirmButton = { TextButton(onClick = { store.select(null) }) { Text("完成") } })
}

@Composable
private fun SavedPage(store: LearningStore, modifier: Modifier) {
    val state = store.ui
    var hideChinese by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        StudyBanner("已收藏 ${state.saved.size} 个单词","已标记记住 ${state.reviewed.intersect(state.saved).size} 个 · 点击词卡继续学习",Glyph.BOOK)
        if (state.saved.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(Glyph.BOOK,size=72)
                Text("把今天遇见的单词留下来",Modifier.padding(top=22.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("点击词卡中的“加入生词本”，随时回来复习。", Modifier.padding(vertical = 12.dp),fontSize=13.sp, color = Muted)
                Button(onClick = { store.page(0) }) { Text("去发现单词") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom=10.dp),verticalAlignment = Alignment.CenterVertically) {
                Text("遮住中文，试着回忆", Modifier.weight(1f), fontSize = 15.sp)
                Switch(checked = hideChinese, onCheckedChange = { hideChinese = it },
                    colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Green,
                        uncheckedThumbColor=Muted,uncheckedTrackColor=Mint,uncheckedBorderColor=Hairline),
                    modifier = Modifier.semantics { contentDescription = "遮住中文练习" })
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(state.saved.mapNotNull { store.word(it) }.sortedBy { it.english }, key = { it.key }) { word ->
                    WordCardShell(onClick={store.select(word.key)}) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(word.english, fontSize = if (state.large) 27.sp else 23.sp, fontWeight = FontWeight.Bold)
                                Text(word.ipa.ifBlank { "暂未收录音标" }, color = Green, fontSize = if (state.large) 19.sp else 15.sp,modifier=Modifier.padding(top=4.dp))
                                Text(if (hideChinese) "点击词卡揭晓" else word.chinese, fontSize = if (state.large) 20.sp else 16.sp,modifier=Modifier.padding(top=8.dp))
                            }
                            SpeakButton { store.speak(word.english) }
                        }
                        if (word.key in state.reviewed) Text("✓ 已记住", color = Green, fontSize = 12.sp,modifier=Modifier.padding(top=6.dp))
                    }
                }
            }
        }
    }
}
