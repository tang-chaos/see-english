package com.codex.englishcamera

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** User-saved model vocabulary is separate from the fixed detector label map. */
private val englishWhitespace=Regex("\\s+")
internal fun normalizedEnglish(english: String): String =
    Normalizer.normalize(english,Normalizer.Form.NFKC).trim().replace(englishWhitespace," ").lowercase(Locale.ROOT)
internal fun userWordKey(english: String): String = "user:" + normalizedEnglish(english)

internal fun userWord(term: VlmTerm): Word? {
    val english=term.english.trim().take(80)
    if(english.isBlank())return null
    return Word(userWordKey(english),english,"",term.chinese.trim().take(160).ifBlank { "暂无中文释义" })
}

internal fun decodeUserWords(raw: String?): MutableMap<String,Word> {
    val result=linkedMapOf<String,Word>()
    runCatching {
        val array=JSONArray(raw ?: "[]")
        for(i in 0 until array.length()) {
            val item=array.optJSONObject(i) ?: continue
            val english=item.opt("english") as? String ?: continue
            val chinese=(item.opt("chinese") as? String).orEmpty()
            userWord(VlmTerm(english,chinese))?.let { result[it.key]=it }
        }
    }
    return result
}

internal fun encodeUserWords(words: Collection<Word>)=JSONArray(words.map {
    JSONObject().put("english",it.english).put("chinese",it.chinese)
}).toString()
