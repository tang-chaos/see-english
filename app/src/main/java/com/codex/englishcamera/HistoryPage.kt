package com.codex.englishcamera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryPage(store: LearningStore,modifier: Modifier) {
    val state=store.ui
    var clear by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        StudyBanner("共 ${state.history.size} 个历史单词","自动保存 · 重复单词只记一次",Glyph.HISTORY) {
            if(state.history.isNotEmpty())TextButton(onClick={clear=true},contentPadding=PaddingValues(start=12.dp)) { Text("清空历史",color=Gold,fontSize=12.sp) }
        }
        if(state.history.isEmpty())Column(Modifier.fillMaxWidth().padding(top=40.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            IconBadge(Glyph.HISTORY,size=72)
            Text("还没有识别历史",fontSize=21.sp,fontWeight=FontWeight.Medium,modifier=Modifier.padding(top=22.dp))
            Text("以后识别到的单词会自动出现在这里。",color=Muted,fontSize=14.sp,modifier=Modifier.padding(vertical=12.dp))
            Button(onClick={store.page(0)}) { Text("去识别") }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
            items(state.history,key={it.key}) { entry -> store.word(entry.key)?.let { word ->
                WordCardShell(onClick={store.select(word.key)}) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(word.english,fontSize=if(state.large)27.sp else 23.sp,fontWeight=FontWeight.Bold)
                                Text(word.ipa.ifBlank { "暂未收录音标" },color=Green,fontSize=if(state.large)19.sp else 15.sp,modifier=Modifier.padding(top=4.dp))
                            }
                            SpeakButton { store.speak(word.english) }
                        }
                        Text(word.chinese,fontSize=if(state.large)20.sp else 16.sp,modifier=Modifier.padding(top=4.dp))
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Text(if(entry.firstSeenAt>0)SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(Date(entry.firstSeenAt)) else "已记录",
                                modifier=Modifier.weight(1f),color=Muted,fontSize=12.sp)
                            TextButton(onClick={store.save(word.key)}) { Text(if(word.key in state.saved)"移出生词本" else "加入生词本") }
                        }
                }
            } }
        }
    }
    if(clear)AlertDialog(onDismissRequest={clear=false},containerColor=Cream,title={Text("清空识别历史？")},
        text={Text("历史记录将清除，生词本中的收藏会保留。")},
        confirmButton={TextButton(onClick={clear=false;store.clearHistory()}) { Text("确认清空") }},
        dismissButton={TextButton(onClick={clear=false}) { Text("取消") }})
}
