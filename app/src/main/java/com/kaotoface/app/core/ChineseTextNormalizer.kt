package com.kaotoface.app.core

/**
 * Chinese ASR returns word tokens separated by spaces and is
 * prone to homophone errors inside amounts. Normalise the display and repair
 * the observed number homophone when it is clearly inside a numeric span;
 * otherwise leave the sentence alone.
 */
object ChineseTextNormalizer {
    private const val NUMBER_CHARS = "零〇一二三四五六七八九十百千万亿两0-9"
    // "三 白酒 十元" = "三百九十元": only when 白酒 sits between number tokens.
    private val NUMBER_BAIJIU = Regex("(?<=[$NUMBER_CHARS])\\s*白酒\\s*(?=[$NUMBER_CHARS])")
    private val SPACE_BETWEEN_HAN = Regex("(?<=[\\u4E00-\\u9FFF])\\s+(?=[\\u4E00-\\u9FFF])")
    private val SPACE_BEFORE_PUNCT = Regex("\\s+([，。！？、；：])")
    private val SPACE_AFTER_PUNCT = Regex("([，。！？、；：])\\s+")

    fun normalize(text: String): String {
        var t = text.trim()
        t = t.replace(NUMBER_BAIJIU, "百九")
        t = t.replace(SPACE_BETWEEN_HAN, "")
        t = t.replace(SPACE_BEFORE_PUNCT, "$1")
        t = t.replace(SPACE_AFTER_PUNCT, "$1")
        return t
    }
}
