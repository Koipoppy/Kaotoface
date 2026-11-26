package com.kaotoface.app.core

enum class Lang(val code: String) {
    EN("en"),
    ZH("zh");

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }
    }
}

enum class DisplayMode(val key: String, val label: String, val hint: String) {
    LANDSCAPE("landscape", "横屏并列", "两个人坐在同一侧，左右对照"),
    PORTRAIT("portrait", "竖屏并列", "两个人坐在同一侧，上下对照"),
    FACE_TO_FACE("face_to_face", "面对面", "两个人坐在对侧，屏幕上下分开"), ;

    companion object {
        fun fromKey(key: String?): DisplayMode = entries.firstOrNull { it.key == key } ?: LANDSCAPE
    }
}

enum class TranslateTone(val key: String, val label: String, val prompt: String) {
    NATURAL("natural", "自然口语", "译文自然、口语化，符合面对面交流的语气，可以保留适度的语气词。"),
    CONCISE("concise", "简洁直接", "译文简洁直接，删去口头冗余，但不要丢失信息。"),
    FORMAL("formal", "商务正式", "译文正式、专业，适合商务会议与正式讨论。");

    companion object {
        fun fromKey(key: String?): TranslateTone = entries.firstOrNull { it.key == key } ?: NATURAL
    }
}

data class Utterance(
    val id: String,
    val en: String,
    val zh: String,
    val source: Lang,
    val createdAt: Long = System.currentTimeMillis(),
    val failed: Boolean = false,
    val speaker: String? = null,
    val remote: Boolean = false,
) {
    fun text(lang: Lang): String = if (lang == Lang.EN) en else zh
}

data class DiscussionRecord(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lines: List<Utterance> = emptyList(),
)

/**
 * A speech utterance that has no ASR text yet. It lives only in the UI and in
 * memory: it is never written to a record or broadcast to a room. The target
 * language side is blank on purpose, so the lyric panel can show a buffer there
 * while DeepSeek proofreads and translates.
 */
data class PendingLine(
    val id: String,
    val source: Lang,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** No ASR text yet; the UI draws gray skeleton blocks instead. */
    val skeleton: Boolean = false,
) {
    fun asUtterance(): Utterance = Utterance(
        id = id,
        en = if (source == Lang.EN) text else "",
        zh = if (source == Lang.ZH) text else "",
        source = source,
        createdAt = createdAt,
    )
}

data class Member(
    val id: String,
    val name: String,
    val isHost: Boolean,
)

data class RoomUi(
    val code: String,
    val hosting: Boolean,
    val members: List<Member> = emptyList(),
    val connected: Boolean = false,
    val joining: Boolean = false,
    val listening: Boolean = false,
    val allowGuestMic: Boolean = false,
    val hostAddress: String? = null,
    val hostName: String = "",
    val selfId: String = "",
) {
    val memberCount: Int get() = members.size.coerceAtLeast(if (hosting) 1 else 0)
}

enum class EnginePhase { IDLE, PREPARING, LISTENING, ERROR }

data class UiState(
    val phase: EnginePhase = EnginePhase.IDLE,
    val prepareProgress: Float = 0f,
    val prepareLabel: String = "",
    val lines: List<Utterance> = emptyList(),
    /** Frozen utterances waiting for DeepSeek ASR; not part of records. */
    val pending: List<PendingLine> = emptyList(),
    /** The current in-progress utterance; not part of records. */
    val live: PendingLine? = null,
    val translating: Int = 0,
    val mode: DisplayMode = DisplayMode.LANDSCAPE,
    val enFirst: Boolean = true,
    val room: RoomUi? = null,
    val error: String? = null,
    val notice: String? = null,
    val guestMicOn: Boolean = false,
    val allowGuestMic: Boolean = false,
    val demo: Boolean = false,
    val records: List<DiscussionRecord> = emptyList(),
    val currentRecordId: String? = null,
) {
    val listening: Boolean get() = phase == EnginePhase.LISTENING
    val busy: Boolean get() = phase == EnginePhase.PREPARING
}

/** One model reported by the provider's /models endpoint. */
data class ModelInfo(
    val id: String,
    val name: String = id,
)

data class AppSettings(
    val apiKey: String = DEFAULT_API_KEY,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val tone: TranslateTone = TranslateTone.NATURAL,
    val fontScale: Float = 1f,
    val tts: Boolean = false,
    val nickname: String = "我的设备",
    val mode: DisplayMode = DisplayMode.LANDSCAPE,
    val enFirst: Boolean = true,
) {
    companion object {
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        val defaultNickname: String
            get() = "我的设备"
    }
}
