package com.kaotoface.app.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Two Vosk recognizers fed the same microphone stream, one per language.
 * Neither model can know which language is being spoken, so we keep both
 * transcripts and let the LLM pick the coherent one afterwards.
 */
class VoskDual(
    private val onFinal: (Lang, String) -> Unit,
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
                    val text = textOf(en.result)
                    if (text.isNotBlank()) onFinal(Lang.EN, text)
                }
                if (zh.acceptWaveForm(bytes, length)) {
                    val text = textOf(zh.result)
                    if (text.isNotBlank()) onFinal(Lang.ZH, text)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "acceptWaveForm failed", t)
            }
        }
    }

    /** Flush an in-progress utterance to a final (used when stopping or after TTS). */
    fun finishAll() {
        if (!ready) return
        synchronized(lock) {
            try {
                enRec?.let {
                    val t = textOf(it.finalResult)
                    if (t.isNotBlank()) onFinal(Lang.EN, t)
                }
                zhRec?.let {
                    val t = textOf(it.finalResult)
                    if (t.isNotBlank()) onFinal(Lang.ZH, t)
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

    private fun textOf(json: String): String = runCatching {
        JSONObject(json).optString("text", "").trim()
    }.getOrDefault("")

    companion object {
        private const val TAG = "KaoVosk"
    }
}
