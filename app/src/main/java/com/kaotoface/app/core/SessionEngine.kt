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
import java.util.concurrent.ConcurrentHashMap
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
    private val pending = ConcurrentHashMap<Lang, String>()

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
            for (segment in segments) {
                try {
                    processSegment(segment)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "segment failed", t)
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
        if (_state.value.room == null) beginRecord()
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
                    onAudio = { bytes, len -> recognizers.feed(bytes, len) },
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
        val fresh = VoskDual { lang, text -> onFinal(lang, text) }
        fresh.load(dirs.en, dirs.zh, onProgress)
        dual = fresh
    }

    private fun onFinal(lang: Lang, text: String) {
        val clean = text.trim()
        if (clean.length < 1) return
        pending[lang] = clean
        if (debounceJob == null) {
            debounceJob = scope.launch {
                delay(700)
                val en = pending.remove(Lang.EN).orEmpty()
                val zh = pending.remove(Lang.ZH).orEmpty()
                debounceJob = null
                if (en.isBlank() && zh.isBlank()) return@launch
                segments.send(Segment(en, zh))
            }
        }
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
                    if (isDuplicate(outcome.en, outcome.zh)) {
                        Log.d(TAG, "duplicate segment ignored")
                        return
                    }
                    val line = Utterance(
                        id = UUID.randomUUID().toString(),
                        en = outcome.en,
                        zh = outcome.zh,
                        source = outcome.src,
                        createdAt = System.currentTimeMillis(),
                        speaker = settings.nickname,
                    )
                    addLine(line, remote = false)
                    roomHost?.broadcastLine(line)
                    roomClient?.sendLine(line)
                    Log.i(TAG, "line ${outcome.src}: ${outcome.en} / ${outcome.zh} (${outcome.latencyMs}ms)")
                }
                JudgeOutcome.NoSpeech -> Unit
                is JudgeOutcome.Failure -> {
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

    private fun addLine(line: Utterance, remote: Boolean) {
        var added = false
        _state.update { st ->
            if (st.lines.any { it.id == line.id }) st
            else {
                added = true
                st.copy(lines = st.lines + line.copy(remote = remote))
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
                    recognizers.feed(chunk, n)
                    _level.value = pcmLevel(chunk)
                    offset += n
                    delay(100)
                }
                repeat(14) {
                    if (!isActive) return@repeat
                    val silence = ByteArray(chunkSize)
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

    private data class Segment(val en: String, val zh: String)
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
    }
}
