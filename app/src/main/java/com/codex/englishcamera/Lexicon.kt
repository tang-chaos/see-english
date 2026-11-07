package com.codex.englishcamera

import android.content.Context

data class Word(val key: String, val english: String, val ipa: String, val chinese: String)

class Lexicon(words: List<Word>) {
    val all = words.sortedBy { it.english }
    private val index = words.associateBy { it.key }
    init { require(index.size == words.size) { "Duplicate vocabulary keys" } }
    operator fun get(key: String): Word? = index[key]
    companion object {
        fun parse(text: String): Lexicon = Lexicon(text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val cells = line.split('|')
            require(cells.size == 4 && cells.all { it.isNotBlank() }) { "Invalid vocabulary row: $line" }
            Word(cells[0], cells[1], cells[2], cells[3])
        }.toList())
        fun load(context: Context) = parse(listOf("words.txt", "words-extra.txt", "words-wide.txt").joinToString("\n") {
            context.assets.open(it).bufferedReader().use { reader -> reader.readText() }
        })
    }
}
