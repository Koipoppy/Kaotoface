package com.kaotoface.app.core

data class AppLanguage(
    val code: String,
    val label: String,
    val ttsTag: String,
)

/**
 * Languages supported by DeepSeek ASR. The UI still has two sides; each side
 * chooses one of these languages, and the LLM translates between the pair.
 */
object Languages {
    val all = listOf(
        AppLanguage("zh", "中文", "zh-CN"),
        AppLanguage("en", "English", "en-US"),
        AppLanguage("ja", "日本語", "ja-JP"),
        AppLanguage("ko", "한국어", "ko-KR"),
        AppLanguage("yue", "粤语", "yue-HK"),
        AppLanguage("fr", "Français", "fr-FR"),
        AppLanguage("de", "Deutsch", "de-DE"),
        AppLanguage("es", "Español", "es-ES"),
        AppLanguage("ru", "Русский", "ru-RU"),
        AppLanguage("pt", "Português", "pt-PT"),
        AppLanguage("it", "Italiano", "it-IT"),
        AppLanguage("ar", "العربية", "ar-SA"),
        AppLanguage("hi", "हिन्दी", "hi-IN"),
        AppLanguage("id", "Bahasa Indonesia", "id-ID"),
        AppLanguage("vi", "Tiếng Việt", "vi-VN"),
        AppLanguage("th", "ไทย", "th-TH"),
        AppLanguage("tr", "Türkçe", "tr-TR"),
        AppLanguage("ms", "Bahasa Melayu", "ms-MY"),
        AppLanguage("fil", "Filipino", "fil-PH"),
        AppLanguage("pl", "Polski", "pl-PL"),
        AppLanguage("hu", "Magyar", "hu-HU"),
    )

    fun of(code: String): AppLanguage = all.firstOrNull { it.code == code } ?: all[0]

    fun label(code: String): String = of(code).label

    fun ttsTag(code: String): String = of(code).ttsTag

    fun isChineseLike(code: String): Boolean = code == "zh" || code == "yue"
}
