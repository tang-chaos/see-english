package com.codex.englishcamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CameraPage(store: LearningStore, modifier: Modifier, immersive: Boolean, toggleImmersive: () -> Unit) {
    val state=store.ui
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    var showWords by remember { mutableStateOf(false) }
    var showOffline by remember { mutableStateOf(false) }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { store.importOfflineModel(it) } }
    val view=LocalView.current
    DisposableEffect(state.runningVlm) {
        val previous=view.keepScreenOn
        if(state.runningVlm)view.keepScreenOn=true
        onDispose { view.keepScreenOn=previous }
    }
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { store.importPhoto(it) } }
    val diagnosticSaver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { store.exportDiagnostics(it) } }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission=granted
        if(!granted)store.notice("开启相机权限后可实时识别，也可从相册选择照片")
    }
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver { _,event ->
            if(event==Lifecycle.Event.ON_PAUSE)store.cancelOfflineVision()
            if(event==Lifecycle.Event.ON_RESUME) {
                permission=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
                store.refreshOfflineModel()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink,Color(0xFF234D40),Ink))).semantics { contentDescription="相机取景区" }) {
        if(state.frozen) {
            state.bitmap?.let { Image(it.asImageBitmap(),"已定格的学习照片",Modifier.fillMaxSize(),contentScale=ContentScale.Fit) }
        } else if(permission) {
            CameraFeed(store,Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center).padding(horizontal=28.dp).padding(top=112.dp,bottom=180.dp)
                .verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                IconBadge(Glyph.CAMERA,Color.White.copy(alpha=.1f),Gold,76)
                Spacer(Modifier.height(24.dp))
                Text("把眼前的世界\n变成你的英语词卡",color=Color.White,fontSize=27.sp,lineHeight=38.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(18.dp))
                Text("单词、音标、中文，就在物品旁。\n识别在手机本地完成。",color=Color(0xFFCCE0D6),fontSize=15.sp,lineHeight=24.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick={launcher.launch(Manifest.permission.CAMERA)},
                    colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Ink),modifier=Modifier.heightIn(min=52.dp)) { Text("允许使用相机") }
                TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}) {
                    Text("去设置开启权限",color=Color(0xFFCCE0D6),fontSize=13.sp)
                }
            }
        }
        if(state.found.isNotEmpty() && state.bitmap!=null)DetectionOverlay(store)
        if(state.pending && (state.frozen || state.capturing)) Surface(Modifier.align(Alignment.Center).padding(28.dp),
            color=Cream,shape=RoundedCornerShape(26.dp),shadowElevation=8.dp) {
            Column(Modifier.padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                CircularProgressIndicator(color=Green,strokeWidth=3.dp,modifier=Modifier.size(32.dp))
                Text(if(state.runningVlm)state.vlmProgress ?: "模型正在识别…" else if(state.capturing)"正在拍摄清晰照片…" else "正在识别照片…",color=Ink,modifier=Modifier.padding(top=18.dp),fontSize=15.sp)
                TextButton(onClick={store.live()}) { Text("返回实时") }
            }
        }
        state.error?.let { error ->
            Surface(Modifier.align(Alignment.Center).padding(28.dp),color=Cream,shape=MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(error)
                    Button(onClick={store.live()},modifier=Modifier.padding(top=12.dp)) {
                        Text("重试相机")
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink.copy(alpha=.95f),Ink.copy(alpha=.65f),Color.Transparent)))
            .padding(horizontal=18.dp).padding(top=12.dp,bottom=14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                IconBadge(Glyph.CAMERA,Color.White.copy(alpha=.12f),Gold,34)
                Text("看见英语",Modifier.weight(1f).padding(start=10.dp),color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold)
                TextButton(onClick={store.large(!state.large)},modifier=Modifier.heightIn(min=48.dp)) {
                    Text(if(state.large)"大字 ✓" else "大字",color=if(state.large)Gold else Color.White)
                }
                TextButton(onClick=toggleImmersive,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                    Text(if(immersive)"退出全屏" else "全屏",color=Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(if(state.vlmAnswer!=null)"识别完成 · 查看右侧词卡" else if(state.frozen)"画面已定格 · 慢慢学" else "对焦、拍照，发现新单词",
                    Modifier.weight(1f),color=Color(0xFFD3E7DE),fontSize=12.sp)
                FilledTonalButton(onClick={store.refreshOfflineModel();showOffline=true},
                    colors=ButtonDefaults.filledTonalButtonColors(containerColor=Gold,contentColor=Ink),
                    contentPadding=PaddingValues(horizontal=14.dp),modifier=Modifier.heightIn(min=48.dp)) {
                    AppIcon(Glyph.SPARK,Modifier.size(16.dp),Ink);Spacer(Modifier.width(6.dp));Text("模型识别",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent,Ink.copy(alpha=.72f),Ink.copy(alpha=.96f))))
            .padding(horizontal=14.dp).padding(top=24.dp,bottom=14.dp)) {
          Surface(color=Cream.copy(alpha=.97f),shape=RoundedCornerShape(28.dp),border=BorderStroke(1.dp,Color.White.copy(alpha=.7f))) {
           Column(Modifier.padding(horizontal=14.dp,vertical=10.dp)) {
            if(state.frozen && !state.pending && state.found.isEmpty() && state.vlmAnswer==null && state.error==null) {
                Text("暂未找到可靠结果，可试试模型识别或换个角度。",color=Muted,fontSize=12.sp,modifier=Modifier.padding(top=4.dp))
            }
            state.recognitionWarning?.let { Text(it,color=Green,fontSize=12.sp,modifier=Modifier.padding(top=4.dp,bottom=2.dp)) }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("识别 ${state.vlmAnswer?.terms?.size ?: state.found.size} 个结果",Modifier.weight(1f).padding(start=4.dp),color=Ink,fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                TextButton(onClick={store.allLabels(!state.allLabels)},contentPadding=PaddingValues(horizontal=8.dp)) {
                    Text(if(state.allLabels)"全部标签 ✓" else "全部标签",color=Muted,fontSize=12.sp)
                }
                TextButton(onClick={showWords=true},enabled=state.found.isNotEmpty(),contentPadding=PaddingValues(horizontal=8.dp)) {
                    Text("单词列表",color=if(state.found.isEmpty())Muted.copy(alpha=.55f) else Green,fontSize=13.sp)
                }
            }
            if(state.frozen) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                TextButton(onClick={diagnosticSaver.launch("see-english-diagnostic.zip")},enabled=!state.pending && state.bitmap!=null) {
                    Text("导出诊断",color=Muted,fontSize=12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={photoPicker.launch(arrayOf("image/*"))},enabled=!state.pending,
                    border=BorderStroke(1.dp,Hairline),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=14.dp),
                    colors=ButtonDefaults.outlinedButtonColors(contentColor=Green),modifier=Modifier.heightIn(min=56.dp)) {
                    AppIcon(Glyph.PHOTO,modifier=Modifier.size(19.dp));Spacer(Modifier.width(6.dp));Text("相册")
                }
                Button(onClick={if(state.frozen)store.live() else store.freeze()},
                    enabled=!state.pending && (state.frozen || (permission && state.hasFrame && state.error==null)),
                    colors=ButtonDefaults.buttonColors(containerColor=Gold,contentColor=Ink),
                    shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(horizontal=12.dp),modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                    Text(if(state.pending)"正在识别…" else if(state.frozen)"继续实时识别" else "拍照定格",fontSize=16.sp,fontWeight=FontWeight.SemiBold)
                }
            }
           }
          }
        }
    }
    if(showOffline)ModalBottomSheet(onDismissRequest={showOffline=false},containerColor=Cream,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        OfflineVisionPanel(store) { modelPicker.launch(null) }
    }
    if(showWords)ModalBottomSheet(onDismissRequest={showWords=false},containerColor=Cream) {
        val words=state.found.groupBy { it.key }
        Text("本次发现 ${state.found.size} 个对象 · ${words.size} 个单词",Modifier.padding(horizontal=24.dp,vertical=12.dp),fontWeight=FontWeight.Bold,fontSize=19.sp)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=480.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(words.keys.toList(),key={it}) { key -> store.lexicon[key]?.let { word ->
                Surface(onClick={showWords=false;store.select(key)},color=Color.White,shape=MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(word.english,fontSize=if(state.large)25.sp else 21.sp,fontWeight=FontWeight.Bold)
                            Text(word.ipa,color=Green,fontSize=if(state.large)19.sp else 16.sp)
                            Text(word.chinese,fontSize=if(state.large)20.sp else 16.sp)
                        }
                        Text("${words[key]?.size} 个",color=Green,fontSize=14.sp)
                    }
                }
            } }
        }
    }
}
