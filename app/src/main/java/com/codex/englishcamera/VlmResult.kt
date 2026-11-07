package com.codex.englishcamera

import org.json.JSONObject
import java.util.Locale

data class VlmTerm(val english: String, val chinese: String)
data class VlmAnswer(val raw: String, val terms: List<VlmTerm>, val elapsedMs: Long, val metrics: String = "{}")

internal fun parseVlmTerms(raw: String): List<VlmTerm> {
    // Keep malformed/truncated output visible, but never manufacture missing objects.
    val start=raw.indexOf('{');val end=raw.lastIndexOf('}')
    if(start<0 || end<start)return emptyList()
    return runCatching {
        val root=JSONObject(raw.substring(start,end+1))
        val array=root.optJSONArray("objects")
        val objects=if(array!=null)(0 until array.length()).mapNotNull { array.optJSONObject(it) } else listOf(root)
        objects.mapNotNull {
            val en=((it.opt("english") as? String) ?: (it.opt("name") as? String)).orEmpty().trim().take(80)
            val zh=(it.opt("chinese") as? String).orEmpty().trim().take(80)
            if(en.isBlank())null else VlmTerm(en,zh)
        }.distinctBy { it.english.lowercase(Locale.ROOT) }.take(12)
    }.getOrDefault(emptyList())
}

internal fun VlmTerm.word(lexicon: Lexicon): Word? {
    val name=normalizedEnglish(english)
    val alias=mapOf("skim milk" to "milk", "milk carton" to "milk", "carton of milk" to "milk",
        "books" to "book", "dumbbells" to "dumbbell", "weighing scale" to "scale",
        "sofa" to "couch", "power outlet" to "socket", "electrical outlet" to "socket")
    return lexicon[name] ?: lexicon[alias[name].orEmpty()] ?:
        lexicon.all.firstOrNull { it.english.equals(name,true) } ?:
        if(name.endsWith('s'))lexicon[name.dropLast(1)] else null
}
