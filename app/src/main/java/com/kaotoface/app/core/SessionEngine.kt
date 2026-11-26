package com.kaotoface.app.core

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.kaotoface.app.room.LanFinder
import com.kaotoface.app.room.RoomClient
import com.kaotoface.app.room.RoomHost
import com.kaotoface.app.room.RoomWire
import com.kaotoface.app.service.TranslationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Owns the whole live session: microphone, dual Vosk streams, LLM adjudication,
 * local room relay and the persisted timeline.
 */
class SessionEngine(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(
        UiState(
            mode = settingsStore.state.value.mode,
            enFirst = settingsStore.state.value.enFirst,
            lines = emptyList(),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** Mic/playback level, kept out of UiState so 14 Hz updates don't recompose the lyric trees. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val translator = DeepSeekTranslator()
    private val history = HistoryStore(context)
    private val recordsStore = RecordsStore(context)
    private val recordLock = Any()
    private var records: List<DiscussionRecord> = emptyList()
    private var currentRecordId: String? = null
    private val segments = Channel<Segment>(Channel.UNLIMITED)
    private val groupQueue = Channel<GroupSnapshot>(Channel.UNLIMITED)

    private var prepJob: Job? = null
    private var demoJob: Job? = null
    private var debounceJob: Job? = null
    private var audio: AudioCapture? = null
    private var dual: VoskDual? = null
    private var tts: TtsPlayer? = null
    private var roomHost: RoomHost? = null
    private var roomClient: RoomClient? = null
    private var joinJob: Job? = null
    private var lastJudgeSignature: String = ""
    private var lastJudgeAt: Long = 0L

    // Streaming Vosk candidates. The live line is the currently spoken text;
    // frozen lines stay in UiState.pending until DeepSeek returns.
    private var liveEn = ""
    private var liveZh = ""
    private var groupEn = ""
    private var groupZh = ""
    private var groupConfEn = 0f
    private var groupConfZh = 0f
    private var groupId: String? = null
    private var groupStartedAt = 0L
    private var lastPartialAt = 0L

    // Raw PCM history lets the final pass restart before the first Vosk partial.
    private val pcmRing = PcmRingBuffer(AudioCapture.SAMPLE_RATE * 2 * RING_SECONDS)
    private val groupLock = Any()
    private var utteranceStartByte = -1L
    private var utteranceEndByte = -1L

    init {
        val stored = recordsStore.load()
        if (stored.isNotEmpty()) {
            records = stored
            // On launch show the latest record that actually has content; an
            // empty record from a cancelled listen stays in the list.
            val latest = stored.filter { it.lines.isNotEmpty() }.maxByOrNull { it.createdAt }
                ?: stored.maxByOrNull { it.createdAt }
            currentRecordId = latest?.id
            _state.update {
                it.copy(
                    lines = latest?.lines ?: emptyList(),
                    records = stored,
                    currentRecordId = latest?.id,
                )
            }
        } else {
            // One-time migration of the old single-timeline history.
            val legacy = history.load()
            if (legacy.isNotEmpty()) {
                val at = legacy.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis()
                val record = DiscussionRecord(
                    id = UUID.randomUUID().toString(),
                    name = recordsStore.defaultName(emptyList(), at),
                    createdAt = at,
                    lines = legacy,
                )
                records = listOf(record)
                currentRecordId = record.id
                recordsStore.save(records)
                history.clear()
                _state.update {
                    it.copy(
                        lines = legacy,
                        records = records,
                        currentRecordId = record.id,
                    )
                }
            }
        }
        scope.launch {
            settingsStore.state.collect { s ->
                _state.update { st -> st.copy(mode = s.mode, enFirst = s.enFirst) }
            }
        }
        scope.launch {
            for (snapshot in groupQueue) {
                try {
                    processGroup(snapshot)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "group failed", t)
                    removePending(snapshot.id)
                    _state.update { it.copy(error = "翻译流程出错：${t.message ?: "未知错误"}") }
                }
            }
        }
        scope.launch {
            for (segment in segments) {
                try {
                    processSegment(segment)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "segment failed", t)
                    removePending(segment.id)
                    _state.update { it.copy(error = "翻译流程出错：${t.message ?: "未知错误"}") }
                }
            }
        }
    }

    // ------------------------------------------------------------------ session

    fun startSession() {
        val phase = _state.value.phase
        if (phase == EnginePhase.PREPARING || phase == EnginePhase.LISTENING) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(error = "请先授予麦克风权限") }
            return
        }
        roomClient?.let {
            if (!_state.value.allowGuestMic) {
                _state.update { it.copy(error = "主持人还没有开放成员收音") }
                return
            }
        }
        if (_state.value.room == null) beginRecord() else clearTransient()
        _state.update {
            it.copy(
                phase = EnginePhase.PREPARING,
                prepareProgress = 0f,
                prepareLabel = "正在准备语音引擎",
                error = null,
                demo = false,
            )
        }
        TranslationService.start(context)
        prepJob = scope.launch {
            try {
                val dirs = ModelInstaller.ensure(context) { p, label ->
                    _state.update { it.copy(prepareProgress = p * 0.72f, prepareLabel = label) }
                }
                ensureRecognizers(dirs) { p, label ->
                    _state.update { it.copy(prepareProgress = 0.72f + p * 0.28f, prepareLabel = label) }
                }
                coroutineContext.ensureActive()
                val recognizers = dual ?: error("语音模型未就绪")
                val capture = AudioCapture(
                    onAudio = { bytes, len ->
                        pcmRing.append(bytes, len)
                        recognizers.feed(bytes, len)
                    },
                    onLevel = { level -> _level.value = level },
                    onError = { message ->
                        _state.update { it.copy(error = message) }
                        scope.launch { stopSession() }
                    },
                )
                capture.start()
                audio = capture
                _state.update {
                    it.copy(
                        phase = EnginePhase.LISTENING,
                        prepareProgress = 1f,
                        prepareLabel = "",
                    )
                }
                roomHost?.setListening(true)
                _state.update { it.copy(guestMicOn = roomClient != null) }
                Log.i(TAG, "session listening")
            } catch (ce: CancellationException) {
                _state.update { it.copy(phase = EnginePhase.IDLE, prepareLabel = "") }
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "failed to start session", t)
                _state.update {
                    it.copy(
                        phase = EnginePhase.ERROR,
                        prepareLabel = "",
                        error = "启动失败：${t.message ?: t.javaClass.simpleName}",
                    )
                }
                TranslationService.stop(context)
            }
        }
    }

    fun stopSession() {
        prepJob?.cancel()
        prepJob = null
        demoJob?.cancel()
        demoJob = null
        clearTransient()
        audio?.stop()
        audio = null
        tts?.stop()
        _level.value = 0f
        _state.update {
            it.copy(
                phase = EnginePhase.IDLE,
                prepareProgress = 0f,
                prepareLabel = "",
                demo = false,
                guestMicOn = false,
            )
        }
        roomHost?.setListening(false)
        TranslationService.stop(context)
        Log.i(TAG, "session stopped")
    }

    private suspend fun ensureRecognizers(
        dirs: ModelInstaller.Dirs,
        onProgress: (Float, String) -> Unit,
    ) {
        val current = dual
        if (current?.ready == true) {
            onProgress(1f, "语音引擎就绪")
            return
        }
        current?.release()
        val fresh = VoskDual(onPartial = ::onPartial, onFinal = ::onFinal)
        fresh.load(dirs.en, dirs.zh, onProgress)
        dual = fresh
    }

    private fun onPartial(lang: Lang, text: String) {
        val clean = if (lang == Lang.ZH) ChineseTextNormalizer.normalize(text) else text.trim()
        if (clean.isEmpty()) return
        synchronized(groupLock) {
            if (lang == Lang.EN) liveEn = clean else liveZh = clean
            // The first partial marks the utterance. Keep the ring position a
            // little before it so a fresh decode can recover the swallowed onset.
            if (groupId == null && utteranceStartByte < 0) {
                val total = pcmRing.totalBytes()
                utteranceStartByte = (total - PRE_ROLL_BYTES).coerceAtLeast(0)
            }
            // The group is already frozen and has a target buffer; don't let a
            // trailing partial rewrite it. The next utterance starts a new live line.
            if (groupId != null) return
        }
        val choice = chooseSource(liveEn, liveZh, 0f, 0f, preferredSource()) ?: return
        val current = _state.value.live
        if (current != null && current.source == choice.first && current.text == choice.second) return
        val now = System.currentTimeMillis()
        if (now - lastPartialAt < 90) return
        lastPartialAt = now
        val line = current?.copy(source = choice.first, text = choice.second)
            ?: PendingLine(
                id = UUID.randomUUID().toString(),
                source = choice.first,
                text = choice.second,
                createdAt = now,
            )
        _state.update { it.copy(live = line) }
    }

    private fun onFinal(lang: Lang, text: String, confidence: Float) {
        val clean = if (lang == Lang.ZH) ChineseTextNormalizer.normalize(text) else text.trim()
        if (clean.isEmpty()) return
        val now = System.currentTimeMillis()
        var firstId: String? = null
        var firstCreatedAt = now
        var choice: Pair<Lang, String>? = null
        synchronized(groupLock) {
            if (lang == Lang.EN) {
                groupEn = appendFinal(groupEn, clean, Lang.EN)
                groupConfEn = confidence
            } else {
                groupZh = appendFinal(groupZh, clean, Lang.ZH)
                groupConfZh = confidence
            }
            if (utteranceStartByte < 0) {
                utteranceStartByte = (pcmRing.totalBytes() - PRE_ROLL_BYTES).coerceAtLeast(0)
            }
            utteranceEndByte = pcmRing.totalBytes()
            if (groupId == null) {
                groupStartedAt = now
                val live = _state.value.live
                firstId = live?.id ?: UUID.randomUUID().toString()
                firstCreatedAt = live?.createdAt ?: now
                groupId = firstId
                liveEn = ""
                liveZh = ""
            }
            choice = chooseSource(groupEn, groupZh, groupConfEn, groupConfZh, preferredSource())
        }
        val chosen = choice ?: return
        val id = firstId ?: groupId ?: return
        if (firstId != null) {
            val pendingLine = PendingLine(
                id = id,
                source = chosen.first,
                text = chosen.second,
                createdAt = firstCreatedAt,
            )
            _state.update {
                it.copy(
                    live = null,
                    pending = it.pending + pendingLine,
                )
            }
        } else {
            // The other Vosk stream finalised within the same window. Keep one
            // buffer row and grow its source candidate.
            _state.update { st ->
                st.copy(
                    pending = st.pending.map {
                        if (it.id == id) it.copy(source = chosen.first, text = chosen.second) else it
                    },
                )
            }
        }
        scheduleDebounce()
    }

    private fun appendFinal(existing: String, text: String, lang: Lang): String = when {
        existing.isBlank() -> text
        text.isBlank() -> existing
        existing.endsWith(text) -> existing
        text.startsWith(existing) -> text
        lang == Lang.EN -> "$existing $text"
        else -> existing + text
    }

    private fun scheduleDebounce() {
        val waitMs = synchronized(groupLock) {
            if (groupId == null) return
            val elapsed = System.currentTimeMillis() - groupStartedAt
            if (elapsed >= GROUP_MAX_MS) 0L else GROUP_IDLE_MS
        }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(waitMs)
            endGroup()
        }
    }

    private suspend fun endGroup() {
        val snapshot = synchronized(groupLock) {
            val id = groupId ?: return
            val s = GroupSnapshot(
                id = id,
                en = groupEn,
                zh = groupZh,
                enConfidence = groupConfEn,
                zhConfidence = groupConfZh,
                startByte = utteranceStartByte,
                endByte = utteranceEndByte,
            )
            groupId = null
            groupEn = ""
            groupZh = ""
            groupConfEn = 0f
            groupConfZh = 0f
            groupStartedAt = 0L
            utteranceStartByte = -1L
            utteranceEndByte = -1L
            liveEn = ""
            liveZh = ""
            debounceJob = null
            s
        }
        // Re-decode can take different amounts of time per sentence; a single
        // consumer keeps the timeline in speech order.
        groupQueue.send(snapshot)
    }

    private suspend fun processGroup(snapshot: GroupSnapshot) {
        val pcm = if (snapshot.startByte >= 0 && snapshot.endByte > snapshot.startByte) {
            pcmRing.snapshot(snapshot.startByte, snapshot.endByte)
        } else null
        val decoded = if (pcm != null && pcm.size >= MIN_DECODE_BYTES) {
            try {
                dual?.decodeUtterance(pcm)
            } catch (t: Throwable) {
                Log.w(TAG, "utterance re-decode failed", t)
                null
            }
        } else null

        var en = snapshot.en
        var zh = snapshot.zh
        var enConf = snapshot.enConfidence
        var zhConf = snapshot.zhConfidence
        if (decoded != null) {
            if (decoded.en.text.isNotBlank()) {
                en = decoded.en.text
                enConf = decoded.en.confidence
            }
            if (decoded.zh.text.isNotBlank()) {
                zh = ChineseTextNormalizer.normalize(decoded.zh.text)
                zhConf = decoded.zh.confidence
            }
            Log.i(
                TAG,
                "group stream=[${snapshot.en} | ${snapshot.zh}] " +
                    "redecode=[${decoded.en.text} | ${decoded.zh.text}]",
            )
        }
        if (en.isBlank() && zh.isBlank()) {
            removePending(snapshot.id)
            return
        }
        chooseSource(en, zh, enConf, zhConf, preferredSource())?.let { chosen ->
            _state.update { st ->
                st.copy(
                    pending = st.pending.map {
                        if (it.id == snapshot.id) it.copy(source = chosen.first, text = chosen.second) else it
                    },
                )
            }
        }
        segments.send(Segment(en, zh, snapshot.id))
    }

    private fun preferredSource(): Lang? =
        _state.value.pending.lastOrNull()?.source ?: _state.value.lines.lastOrNull()?.source

    /**
     * Vosk gives us two candidates for every chunk. At final time we use its
     * word confidence to choose which one is likely real speech; the script
     * ratio and the previous line are fallbacks (partials have no confidence).
     * DeepSeek still makes the authoritative choice.
     */
    private fun chooseSource(
        en: String,
        zh: String,
        enConfidence: Float,
        zhConfidence: Float,
        preferred: Lang?,
    ): Pair<Lang, String>? {
        val e = en.trim()
        val z = zh.trim()
        if (e.isEmpty() && z.isEmpty()) return null
        if (z.isEmpty()) return Lang.EN to e
        if (e.isEmpty()) return Lang.ZH to z
        val eCjk = countCjk(e)
        val zCjk = countCjk(z)
        val eLatin = countLatin(e)
        val zLatin = countLatin(z)
        // A real amount/number is strong evidence. "standby to syria" must not
        // beat "390元" merely because it is a fluent-looking English phrase.
        val zhAmount = LanguageHeuristics.hasAmount(z)
        val enAmount = LanguageHeuristics.hasAmount(e)
        if (zhAmount && !enAmount) return Lang.ZH to z
        if (enAmount && !zhAmount) return Lang.EN to e
        if (enConfidence > zhConfidence + 0.08f) return Lang.EN to e
        if (zhConfidence > enConfidence + 0.08f) return Lang.ZH to z
        // Current-sentence script evidence comes before the previous line's
        // language. Otherwise a Chinese sentence after English is forced back
        // into English even when the Chinese candidate is clearly CJK.
        if (zCjk > eCjk + 1) return Lang.ZH to z
        if (eLatin > zLatin + 1) return Lang.EN to e
        if (preferred == Lang.EN) return Lang.EN to e
        if (preferred == Lang.ZH) return Lang.ZH to z
        return if (z.length >= e.length) Lang.ZH to z else Lang.EN to e
    }

    private fun countCjk(text: String): Int = text.count {
        it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF || it.code in 0xF900..0xFAFF
    }

    private fun countLatin(text: String): Int = text.count {
        it in 'A'..'Z' || it in 'a'..'z'
    }

    private fun removePending(id: String) {
        _state.update { st ->
            if (st.pending.none { it.id == id }) st
            else st.copy(pending = st.pending.filterNot { it.id == id })
        }
    }

    private fun clearTransient() {
        debounceJob?.cancel()
        debounceJob = null
        synchronized(groupLock) {
            groupId = null
            groupEn = ""
            groupZh = ""
            groupConfEn = 0f
            groupConfZh = 0f
            groupStartedAt = 0L
            utteranceStartByte = -1L
            utteranceEndByte = -1L
            liveEn = ""
            liveZh = ""
        }
        pcmRing.clear()
        _state.update { it.copy(pending = emptyList(), live = null) }
    }

    private suspend fun processSegment(segment: Segment) {
        val settings = settingsStore.state.value
        _state.update { it.copy(translating = it.translating + 1) }
        try {
            val recent = _state.value.lines.takeLast(2)
            val outcome = withContext(Dispatchers.IO) {
                translator.judge(settings, segment.en, segment.zh, recent)
            }
            when (outcome) {
                is JudgeOutcome.Success -> {
                    var firstAdded = false
                    outcome.lines.forEachIndexed { index, translated ->
                        if (isDuplicate(translated.en, translated.zh)) {
                            Log.d(TAG, "duplicate line ignored")
                            return@forEachIndexed
                        }
                        val id = if (!firstAdded) segment.id else "${segment.id}-$index"
                        val line = Utterance(
                            id = id,
                            en = translated.en,
                            zh = translated.zh,
                            source = outcome.src,
                            createdAt = System.currentTimeMillis() + index,
                            speaker = settings.nickname,
                        )
                        // The first sentence replaces the pending row in the
                        // same state update, preserving its key for the animation.
                        addLine(
                            line,
                            remote = false,
                            replacingPendingId = if (!firstAdded) segment.id else null,
                        )
                        roomHost?.broadcastLine(line)
                        roomClient?.sendLine(line)
                        firstAdded = true
                        Log.i(TAG, "line ${outcome.src}: ${line.en} / ${line.zh} (${outcome.latencyMs}ms)")
                    }
                    if (!firstAdded) removePending(segment.id)
                }
                JudgeOutcome.NoSpeech -> removePending(segment.id)
                is JudgeOutcome.Failure -> {
                    removePending(segment.id)
                    if (outcome.message.contains("API Key") || outcome.message.contains("余额") || outcome.message.contains("权限")) {
                        _state.update { it.copy(error = outcome.message) }
                    } else {
                        _state.update { it.copy(notice = "这一句未翻译：${outcome.message}") }
                    }
                }
            }
        } finally {
            _state.update { it.copy(translating = (it.translating - 1).coerceAtLeast(0)) }
        }
    }

    private fun isDuplicate(en: String, zh: String): Boolean {
        val signature = normalize(en) + "|" + normalize(zh)
        val now = System.currentTimeMillis()
        if (signature == lastJudgeSignature && now - lastJudgeAt < 6000) return true
        lastJudgeSignature = signature
        lastJudgeAt = now
        val last = _state.value.lines.lastOrNull() ?: return false
        if (now - last.createdAt > 5000) return false
        val a = normalize(en)
        val b = normalize(zh)
        val la = normalize(last.en)
        val lb = normalize(last.zh)
        fun similar(x: String, y: String): Boolean {
            if (x.isBlank() || y.isBlank()) return false
            if (x == y) return true
            val short = if (x.length <= y.length) x else y
            val long = if (x.length <= y.length) y else x
            return short.length >= 6 && long.contains(short)
        }
        return (a.isNotEmpty() && similar(a, la)) || (b.isNotEmpty() && similar(b, lb))
    }

    private fun normalize(s: String): String = s.lowercase()
        .replace(Regex("[\\s\\p{Punct}，。！？、；：“”‘’（）《》〈〉·…—～]"), "")

    fun addRemoteLine(line: Utterance) {
        addLine(line, remote = true)
    }

    private fun addLine(line: Utterance, remote: Boolean, replacingPendingId: String? = null) {
        var added = false
        _state.update { st ->
            if (st.lines.any { it.id == line.id }) st
            else {
                added = true
                st.copy(
                    lines = st.lines + line.copy(remote = remote),
                    pending = if (replacingPendingId == null) st.pending
                    else st.pending.filterNot { it.id == replacingPendingId },
                )
            }
        }
        if (added) {
            val snapshot = _state.value.lines
            scope.launch {
                history.save(snapshot)
                val id = currentRecordId
                if (id != null && _state.value.room == null) {
                    synchronized(recordLock) {
                        if (records.any { it.id == id }) {
                            records = records.map {
                                if (it.id == id) it.copy(lines = snapshot) else it
                            }
                            recordsStore.save(records)
                        }
                    }
                    _state.update { it.copy(records = records) }
                }
            }
        }
    }

    private fun beginRecord(): DiscussionRecord {
        clearTransient()
        synchronized(recordLock) {
            val now = System.currentTimeMillis()
            val record = DiscussionRecord(
                id = UUID.randomUUID().toString(),
                name = recordsStore.defaultName(records, now),
                createdAt = now,
            )
            records = records + record
            currentRecordId = record.id
            recordsStore.save(records)
            _state.update {
                it.copy(
                    lines = emptyList(),
                    records = records,
                    currentRecordId = record.id,
                )
            }
            return record
        }
    }

    fun clearLines() {
        if (roomClient != null) {
            _state.update { it.copy(notice = "只有房主可以清空房间记录") }
            return
        }
        clearTransient()
        _state.update { it.copy(lines = emptyList()) }
        val id = currentRecordId
        if (id != null && _state.value.room == null) {
            synchronized(recordLock) {
                records = records.map {
                    if (it.id == id) it.copy(lines = emptyList()) else it
                }
                recordsStore.save(records)
            }
            _state.update { it.copy(records = records) }
        }
        history.clear()
        roomHost?.broadcastClear()
    }

    // ------------------------------------------------------------------- controls

    fun cycleMode() {
        val next = DisplayMode.entries[(DisplayMode.entries.indexOf(_state.value.mode) + 1) % DisplayMode.entries.size]
        settingsStore.setMode(next)
        _state.update { it.copy(mode = next) }
    }

    fun setMode(mode: DisplayMode) {
        settingsStore.setMode(mode)
        _state.update { it.copy(mode = mode) }
    }

    fun swapSides() {
        val next = !_state.value.enFirst
        settingsStore.setEnFirst(next)
        _state.update { it.copy(enFirst = next) }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun speak(line: Utterance, lang: Lang) {
        if (!settingsStore.state.value.tts) return
        val text = line.text(lang)
        if (text.isBlank()) return
        val player = tts ?: TtsPlayer(context).also { tts = it }
        audio?.paused = true
        val accepted = player.speak(text, lang) {
            audio?.paused = false
            dual?.reset()
        }
        if (!accepted) {
            audio?.paused = false
            _state.update { it.copy(notice = "系统的语音合成暂不支持该语言") }
        }
    }

    fun testApi(onResult: (Result<Long>) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                translator.testConnection(settingsStore.state.value)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    fun fetchModels(onResult: (Result<List<ModelInfo>>) -> Unit) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                translator.listModels(settingsStore.state.value)
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    // ----------------------------------------------------------------------- demo

    fun startDemo() {
        val phase = _state.value.phase
        if (phase == EnginePhase.PREPARING || phase == EnginePhase.LISTENING) return
        if (roomClient != null) {
            _state.update { it.copy(notice = "旁听房间时不能播放演示对话") }
            return
        }
        if (_state.value.room == null) beginRecord()
        _state.update {
            it.copy(
                phase = EnginePhase.PREPARING,
                prepareProgress = 0f,
                prepareLabel = "正在准备演示",
                error = null,
                demo = true,
            )
        }
        demoJob = scope.launch {
            try {
                val dirs = ModelInstaller.ensure(context) { p, label ->
                    _state.update { it.copy(prepareProgress = p * 0.7f, prepareLabel = label) }
                }
                ensureRecognizers(dirs) { p, label ->
                    _state.update { it.copy(prepareProgress = 0.7f + p * 0.3f, prepareLabel = label) }
                }
                coroutineContext.ensureActive()
                val recognizers = dual ?: error("语音模型未就绪")
                val wav = withContext(Dispatchers.IO) {
                    context.assets.open("demo/conversation.wav").use { parseWav(it.readBytes()) }
                } ?: error("演示音频不可用")
                if (wav.sampleRate != AudioCapture.SAMPLE_RATE || wav.channels != 1 || wav.bits != 16) {
                    error("演示音频格式不正确")
                }
                _state.update {
                    it.copy(
                        phase = EnginePhase.LISTENING,
                        prepareProgress = 1f,
                        prepareLabel = "",
                        demo = true,
                    )
                }
                roomHost?.setListening(true)
                var offset = 0
                val chunkSize = AudioCapture.CHUNK_SAMPLES * 2
                while (offset < wav.pcm.size && isActive) {
                    val n = minOf(chunkSize, wav.pcm.size - offset)
                    val chunk = wav.pcm.copyOfRange(offset, offset + n)
                    pcmRing.append(chunk, n)
                    recognizers.feed(chunk, n)
                    _level.value = pcmLevel(chunk)
                    offset += n
                    delay(100)
                }
                repeat(14) {
                    if (!isActive) return@repeat
                    val silence = ByteArray(chunkSize)
                    pcmRing.append(silence, silence.size)
                    recognizers.feed(silence, silence.size)
                    delay(100)
                }
                recognizers.finishAll()
                delay(900)
                var waited = 0
                while (_state.value.translating > 0 && waited < 12000 && isActive) {
                    delay(250)
                    waited += 250
                }
                delay(500)
                roomHost?.setListening(false)
                _state.update {
                    it.copy(
                        phase = EnginePhase.IDLE,
                        demo = false,
                        notice = "演示结束 · 记录已保留",
                    )
                }
            } catch (ce: CancellationException) {
                roomHost?.setListening(false)
                _state.update { it.copy(phase = EnginePhase.IDLE, demo = false) }
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "demo failed", t)
                roomHost?.setListening(false)
                _state.update {
                    it.copy(
                        phase = EnginePhase.ERROR,
                        demo = false,
                        error = "演示失败：${t.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------------------- rooms

    private val hostCallbacks = object : RoomHost.Callbacks {
        override fun onMembers(members: List<Member>) {
            _state.update { st ->
                st.room?.let { st.copy(room = it.copy(members = members)) } ?: st
            }
        }

        override fun onGuestLine(line: Utterance) {
            addLine(line, remote = true)
        }

        override fun onPresence(listening: Boolean, allowGuestMic: Boolean) {
            _state.update { st ->
                st.room?.let { st.copy(room = it.copy(listening = listening, allowGuestMic = allowGuestMic), allowGuestMic = allowGuestMic) } ?: st
            }
        }

        override fun onClosed(reason: String) {
            roomHost = null
            _state.update { it.copy(room = null, notice = reason) }
        }

        override fun onNotice(message: String) {
            _state.update { it.copy(notice = message) }
        }
    }

    private val clientCallbacks = object : RoomClient.Callbacks {
        override fun onWelcome(
            selfId: String,
            hostName: String,
            members: List<Member>,
            lines: List<Utterance>,
            listening: Boolean,
            allowGuestMic: Boolean,
        ) {
            _state.update { st ->
                st.copy(
                    lines = lines,
                    allowGuestMic = allowGuestMic,
                    room = st.room?.copy(
                        joining = false,
                        connected = true,
                        members = members,
                        listening = listening,
                        allowGuestMic = allowGuestMic,
                        hostName = hostName,
                        selfId = selfId,
                    ),
                )
            }
            if (lines.isNotEmpty()) scope.launch { history.save(lines) }
        }

        override fun onPresence(
            hostName: String,
            members: List<Member>,
            listening: Boolean,
            allowGuestMic: Boolean,
        ) {
            _state.update { st ->
                st.copy(
                    allowGuestMic = allowGuestMic,
                    room = st.room?.copy(
                        members = members,
                        listening = listening,
                        allowGuestMic = allowGuestMic,
                        hostName = hostName,
                    ),
                )
            }
        }

        override fun onLine(line: Utterance) {
            addLine(line, remote = true)
        }

        override fun onClear() {
            _state.update { it.copy(lines = emptyList()) }
            scope.launch { history.clear() }
        }

        override fun onClosed(reason: String) {
            val wasListening = _state.value.phase == EnginePhase.LISTENING
            roomClient = null
            _state.update {
                it.copy(
                    room = null,
                    notice = "房间已断开：$reason",
                    guestMicOn = false,
                )
            }
            if (wasListening) stopSession()
        }

        override fun onNotice(message: String) {
            _state.update { it.copy(notice = message) }
        }
    }

    fun createRoom() {
        if (roomHost != null || roomClient != null) return
        val nickname = settingsStore.state.value.nickname
        scope.launch {
            var created: RoomHost? = null
            var chosen = ""
            for (i in 0 until 12) {
                val candidate = (1000..9999).random().toString()
                val host = RoomHost(
                    context = context,
                    code = candidate,
                    hostName = nickname,
                    snapshotProvider = { _state.value.lines },
                    callbacks = hostCallbacks,
                )
                if (host.start()) {
                    created = host
                    chosen = candidate
                    break
                }
            }
            if (created == null) {
                _state.update { it.copy(error = "无法创建房间，请检查网络或重试") }
                return@launch
            }
            roomHost = created
            created.setListening(_state.value.phase == EnginePhase.LISTENING)
            _state.update {
                it.copy(
                    room = RoomUi(
                        code = chosen,
                        hosting = true,
                        members = created.members(),
                        connected = true,
                        listening = _state.value.phase == EnginePhase.LISTENING,
                        hostName = nickname,
                        hostAddress = com.kaotoface.app.room.LanNet.localIpv4(),
                    ),
                    notice = "房间已创建 · 暗号 $chosen",
                )
            }
        }
    }

    fun joinRoom(code: String, manualIp: String? = null) {
        val clean = code.trim()
        if (clean.length != 4 || clean.any { !it.isDigit() }) {
            _state.update { it.copy(error = "请输入四位数字暗号") }
            return
        }
        if (roomHost != null || roomClient != null) leaveRoom()
        joinJob?.cancel()
        _state.update {
            it.copy(
                room = RoomUi(code = clean, hosting = false, joining = true),
                error = null,
            )
        }
        joinJob = scope.launch {
            try {
                val found = if (manualIp.isNullOrBlank()) {
                    LanFinder(context, clean).find(7000)
                } else {
                    val ip = manualIp.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
                    LanFinder.Found(ip, RoomWire.portFor(clean), "")
                }
                if (found == null) {
                    _state.update {
                        it.copy(room = null, error = "未发现房间 · 请确认在同一 Wi-Fi 下，或手动填写主机 IP")
                    }
                    return@launch
                }
                val client = RoomClient(
                    code = clean,
                    host = found.host,
                    guestName = settingsStore.state.value.nickname,
                    portOverride = found.port,
                    callbacks = clientCallbacks,
                )
                roomClient = client
                if (!client.connect()) {
                    roomClient = null
                    _state.update { it.copy(room = null, error = "无法连接房间 ${found.host}:${found.port}") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        room = it.room?.copy(
                            hostAddress = found.host,
                            hostName = found.name,
                            joining = true,
                        ),
                        notice = "正在加入房间 $clean…",
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "join failed", t)
                _state.update { it.copy(room = null, error = "加入房间失败：${t.message ?: "网络错误"}") }
            }
        }
    }

    fun leaveRoom() {
        joinJob?.cancel()
        joinJob = null
        val wasInRoom = roomHost != null || roomClient != null
        if (wasInRoom && _state.value.phase == EnginePhase.LISTENING) stopSession()
        roomHost?.stop(true)
        roomHost = null
        roomClient?.stop(true)
        roomClient = null
        _state.update { it.copy(room = null, guestMicOn = false) }
    }

    fun setAllowGuestMic(value: Boolean) {
        val host = roomHost ?: return
        host.setAllowGuestMic(value)
        _state.update { st ->
            st.copy(
                allowGuestMic = value,
                room = st.room?.copy(allowGuestMic = value),
            )
        }
    }

    fun shutdown() {
        stopSession()
        leaveRoom()
        dual?.release()
        dual = null
        tts?.shutdown()
        tts = null
        scope.cancel()
    }

    // -------------------------------------------------------------------- helpers

    private fun pcmLevel(pcm: ByteArray): Float {
        if (pcm.size < 2) return 0f
        var sum = 0.0
        var i = 0
        val limit = pcm.size and 1.inv()
        while (i < limit) {
            val lo = pcm[i].toInt() and 0xff
            val hi = pcm[i + 1].toInt()
            val s = (hi shl 8) or lo
            sum += (s * s).toDouble()
            i += 2
        }
        val rms = sqrt(sum / (limit / 2))
        val db = 20.0 * log10(max(rms, 1.0) / 32768.0)
        return ((db + 58.0) / 48.0).coerceIn(0.0, 1.0).toFloat()
    }

    private data class Segment(val en: String, val zh: String, val id: String)

    private data class GroupSnapshot(
        val id: String,
        val en: String,
        val zh: String,
        val enConfidence: Float,
        val zhConfidence: Float,
        val startByte: Long,
        val endByte: Long,
    )
    private data class DemoWav(val pcm: ByteArray, val sampleRate: Int, val channels: Int, val bits: Int)

    private fun parseWav(bytes: ByteArray): DemoWav? {
        if (bytes.size < 12) return null
        if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
        var sampleRate = 0
        var channels = 0
        var bits = 0
        var pcm: ByteArray? = null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4)
            val size = (bytes[offset + 4].toInt() and 0xff) or
                ((bytes[offset + 5].toInt() and 0xff) shl 8) or
                ((bytes[offset + 6].toInt() and 0xff) shl 16) or
                ((bytes[offset + 7].toInt() and 0xff) shl 24)
            val body = offset + 8
            if (size <= 0 || body + size > bytes.size) break
            when (id) {
                "fmt " -> {
                    channels = (bytes[body + 2].toInt() and 0xff) or ((bytes[body + 3].toInt() and 0xff) shl 8)
                    sampleRate = (bytes[body + 4].toInt() and 0xff) or
                        ((bytes[body + 5].toInt() and 0xff) shl 8) or
                        ((bytes[body + 6].toInt() and 0xff) shl 16) or
                        ((bytes[body + 7].toInt() and 0xff) shl 24)
                    bits = (bytes[body + 14].toInt() and 0xff) or ((bytes[body + 15].toInt() and 0xff) shl 8)
                }
                "data" -> pcm = bytes.copyOfRange(body, body + size)
            }
            offset = body + size + (size and 1)
        }
        val data = pcm ?: return null
        return DemoWav(data, sampleRate, channels, bits)
    }

    companion object {
        private const val TAG = "KaoEngine"
        private const val RING_SECONDS = 12
        private const val GROUP_IDLE_MS = 700L
        private const val GROUP_MAX_MS = 2500L
        private const val PRE_ROLL_BYTES = AudioCapture.SAMPLE_RATE * 2 * 8 / 10 // 800ms
        private const val MIN_DECODE_BYTES = AudioCapture.SAMPLE_RATE * 2 / 4 // 250ms
    }
}
