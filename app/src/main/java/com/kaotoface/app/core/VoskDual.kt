package com.kaotoface.app.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

data class VoskCandidate(
    val text: String,
    val confidence: Float,
)

data class VoskUtterance(
    val en: VoskCandidate,
    val zh: VoskCandidate,
)

/**
 * Two Vosk recognizers fed the same microphone stream, one per language.
 * Neither model can know which language is being spoken, so we keep both
 * transcripts and let the LLM pick the coherent one afterwards.
 */
class VoskDual(
    private val onPartial: (Lang, String) -> Unit,
    private val onFinal: (Lang, String, Float) -> Unit,
) {
    private val lock = Any()
    private var enModel: Model? = null
    private var zhModel: Model? = null
    private var enRec: Recognizer? = null
    private var zhRec: Recognizer? = null
    @Volatile var ready: Boolean = false
        private set

    suspend fun load(enDir: File, zhDir: File, onProgress: (Float, String) -> Unit) {
        withContext(Dispatchers.IO) {
            onProgress(0f, "正在唤醒英文耳朵")
            val t0 = System.currentTimeMillis()
            val en = Model(enDir.absolutePath)
            onProgress(0.5f, "正在唤醒中文耳朵")
            val zh = Model(zhDir.absolutePath)
            onProgress(0.9f, "正在校准听觉")
            synchronized(lock) {
                enModel = en
                zhModel = zh
                enRec = Recognizer(en, AudioCapture.SAMPLE_RATE.toFloat()).also {
                    it.setWords(true)
                    it.setMaxAlternatives(0)
                }
                zhRec = Recognizer(zh, AudioCapture.SAMPLE_RATE.toFloat()).also {
                    it.setWords(true)
                    it.setMaxAlternatives(0)
                }
                ready = true
            }
            Log.i(TAG, "models loaded in ${System.currentTimeMillis() - t0} ms")
            onProgress(1f, "准备就绪")
        }
    }

    fun feed(bytes: ByteArray, length: Int) {
        if (!ready) return
        synchronized(lock) {
            val en = enRec ?: return
            val zh = zhRec ?: return
            try {
                if (en.acceptWaveForm(bytes, length)) {
                    val (text, confidence) = resultOf(en.result)
                    if (text.isNotBlank()) onFinal(Lang.EN, text, confidence)
                } else {
                    val partial = partialOf(en.partialResult)
                    if (partial.isNotBlank()) onPartial(Lang.EN, partial)
                }
                if (zh.acceptWaveForm(bytes, length)) {
                    val (text, confidence) = resultOf(zh.result)
                    if (text.isNotBlank()) onFinal(Lang.ZH, text, confidence)
                } else {
                    val partial = partialOf(zh.partialResult)
                    if (partial.isNotBlank()) onPartial(Lang.ZH, partial)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "acceptWaveForm failed", t)
            }
        }
    }

    /**
     * Decode a buffered utterance from scratch with fresh recognizers. The
     * streaming recognizers cannot rewind, but the PCM ring can: this is what
     * recovers the first word even if Vosk's streaming endpoint swallowed it.
     */
    suspend fun decodeUtterance(pcm: ByteArray): VoskUtterance? = withContext(Dispatchers.IO) {
        val refs = synchronized(lock) { enModel to zhModel }
        val enModelRef = refs.first ?: return@withContext null
        val zhModelRef = refs.second ?: return@withContext null
        var enRec: Recognizer? = null
        var zhRec: Recognizer? = null
        try {
            enRec = Recognizer(enModelRef, AudioCapture.SAMPLE_RATE.toFloat()).also {
                it.setWords(true)
                it.setMaxAlternatives(0)
            }
            zhRec = Recognizer(zhModelRef, AudioCapture.SAMPLE_RATE.toFloat()).also {
                it.setWords(true)
                it.setMaxAlternatives(0)
            }
            var offset = 0
            while (offset < pcm.size) {
                val count = minOf(3200, pcm.size - offset)
                val chunk = pcm.copyOfRange(offset, offset + count)
                enRec.acceptWaveForm(chunk, count)
                zhRec.acceptWaveForm(chunk, count)
                offset += count
            }
            val en = resultOf(enRec.finalResult)
            val zh = resultOf(zhRec.finalResult)
            VoskUtterance(
                en = VoskCandidate(en.first, en.second),
                zh = VoskCandidate(zh.first, zh.second),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "utterance re-decode failed", t)
            null
        } finally {
            runCatching { enRec?.close() }
            runCatching { zhRec?.close() }
        }
    }

    /** Flush an in-progress utterance to a final (used when stopping or after TTS). */
    fun finishAll() {
        if (!ready) return
        synchronized(lock) {
            try {
                enRec?.let {
                    val (text, confidence) = resultOf(it.finalResult)
                    if (text.isNotBlank()) onFinal(Lang.EN, text, confidence)
                }
                zhRec?.let {
                    val (text, confidence) = resultOf(it.finalResult)
                    if (text.isNotBlank()) onFinal(Lang.ZH, text, confidence)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "finishAll failed", t)
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            runCatching { enRec?.reset() }
            runCatching { zhRec?.reset() }
        }
    }

    fun release() {
        synchronized(lock) {
            ready = false
            runCatching { enRec?.close() }
            runCatching { zhRec?.close() }
            runCatching { enModel?.close() }
            runCatching { zhModel?.close() }
            enRec = null
            zhRec = null
            enModel = null
            zhModel = null
        }
    }

    private fun partialOf(json: String): String = runCatching {
        JSONObject(json).optString("partial", "").trim()
    }.getOrDefault("")

    private fun resultOf(json: String): Pair<String, Float> = runCatching {
        val obj = JSONObject(json)
        val text = obj.optString("text", "").trim()
        val words = obj.optJSONArray("result")
        var sum = 0.0
        var count = 0
        if (words != null) {
            for (i in 0 until words.length()) {
                val word = words.optJSONObject(i) ?: continue
                val conf = word.optDouble("conf", Double.NaN)
                if (!conf.isNaN()) {
                    sum += conf
                    count++
                }
            }
        }
        text to (if (count > 0) (sum / count).toFloat() else 0f)
    }.getOrDefault("" to 0f)

    companion object {
        private const val TAG = "KaoVosk"
    }
}
