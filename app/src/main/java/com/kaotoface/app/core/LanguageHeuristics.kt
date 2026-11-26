package com.kaotoface.app.core

/**
 * Cheap but deterministic evidence that the bilingual judge should not ignore.
 * A fluent-looking English mishearing such as "standby to syria" must not beat
 * a Chinese candidate that actually contains the spoken amount ("390元").
 */
object LanguageHeuristics {
    private val ZH_CURRENCY = Regex("(元|块|美元|人民币|角|分)")
    private val ZH_NUMERAL = Regex("[零〇一二三四五六七八九十百千万亿两]")
    private val EN_CURRENCY = Regex("(?i)\\b(yuan|rmb|dollars?|cents?|euros?)\\b")
    private val EN_NUMERAL = Regex(
        "(?i)\\b(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
            "thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|" +
            "forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand|million|billion)\\b",
    )

    fun hasAmount(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.any { it.isDigit() }) return true
        if (ZH_CURRENCY.containsMatchIn(t) && ZH_NUMERAL.containsMatchIn(t)) return true
        if (EN_CURRENCY.containsMatchIn(t) && EN_NUMERAL.containsMatchIn(t)) return true
        return false
    }
}
