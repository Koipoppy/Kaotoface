package com.kaotoface.app.core

/**
 * Deterministic client-side fallback for long speech. Vosk/DeepSeek can return
 * one unpunctuated paragraph; this splits it into sentences/clauses small
 * enough to read and gives every chunk a terminal mark. The LLM translates
 * each chunk separately, so sentence boundaries no longer depend on the model.
 */
object TextSegmenter {
    private const val ZH_MAX = 32
    private const val EN_MAX = 180
    private val ZH_MARKERS = listOf(
        "但是", "不过", "所以", "因为", "如果", "然后", "另外", "此外",
        "同时", "并且", "而且", "例如", "包括", "以及",
    )

    fun split(text: String, lang: Lang): List<String> {
        val source = text.trim()
        if (source.isEmpty()) return emptyList()
        val strong = splitStrong(source)
        val chunks = mutableListOf<String>()
        for (part in strong) splitLong(part, lang, chunks)
        return chunks
            .map { punctuate(it, lang) }
            .filter { it.isNotBlank() }
    }

    fun punctuate(text: String, lang: Lang): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        if (lang == Lang.ZH) {
            if (t.last() !in "。！？!?…") {
                if (t.last() in "，,、；;：:") t = t.dropLast(1)
                t += "。"
            }
        } else {
            if (t.last() !in ".!?…") {
                if (t.last() in ",;:") t = t.dropLast(1)
                t += "."
            }
        }
        return t
    }

    private fun splitStrong(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        for (i in text.indices) {
            if (text[i] in "。！？!?；;\n") {
                val part = text.substring(start, i + 1).trim()
                if (part.isNotEmpty()) parts += part
                start = i + 1
            }
        }
        if (start < text.length) {
            val tail = text.substring(start).trim()
            if (tail.isNotEmpty()) parts += tail
        }
        return parts
    }

    private fun splitLong(text: String, lang: Lang, out: MutableList<String>) {
        var rest = text.trim()
        // Leave one character for the terminal punctuation added below.
        val max = if (lang == Lang.ZH) ZH_MAX - 1 else EN_MAX - 1
        while (rest.length > max) {
            val cut = if (lang == Lang.ZH) zhCut(rest, max) else enCut(rest, max)
            val at = if (cut > 0) cut else max
            out += rest.substring(0, at).trim()
            rest = rest.substring(at).trim()
        }
        if (rest.isNotEmpty()) out += rest
    }

    private fun zhCut(text: String, max: Int): Int {
        val limit = minOf(max, text.length - 1)
        var soft = -1
        for (i in 1..limit) {
            if (text[i - 1] in "，,、：:") soft = i
        }
        if (soft >= max / 2) return soft
        var marker = -1
        for (m in ZH_MARKERS) {
            val idx = text.lastIndexOf(m, limit)
            if (idx > marker && idx >= max / 2) marker = idx
        }
        if (marker > 0) return marker
        return max
    }

    private fun enCut(text: String, max: Int): Int {
        val limit = minOf(max, text.length - 1)
        val space = text.lastIndexOf(' ', limit)
        if (space >= max / 2) return space
        val punct = text.lastIndexOfAny(charArrayOf(',', ';', ':', '，', '；', '：'), limit)
        if (punct >= max / 2) return punct + 1
        return max
    }
}
