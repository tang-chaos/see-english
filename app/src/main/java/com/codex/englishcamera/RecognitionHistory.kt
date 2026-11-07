package com.codex.englishcamera

import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val key: String,val firstSeenAt: Long)

internal fun decodeHistory(raw: String?): List<HistoryEntry> = runCatching {
    val array=JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { index ->
        val item=array.optJSONObject(index) ?: return@mapNotNull null
        val key=(item.opt("key") as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        HistoryEntry(key,item.optLong("firstSeenAt",0).coerceAtLeast(0))
    }.distinctBy { it.key }
}.getOrDefault(emptyList())

internal fun encodeHistory(entries: List<HistoryEntry>)=JSONArray(entries.map {
    JSONObject().put("key",it.key).put("firstSeenAt",it.firstSeenAt)
}).toString()
