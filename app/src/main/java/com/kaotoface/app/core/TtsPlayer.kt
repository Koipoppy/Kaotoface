package com.kaotoface.app.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TtsPlayer(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var failed = false
    private var pending: Pending? = null
    private var onDoneCallback: (() -> Unit)? = null

    private data class Pending(val text: String, val languageTag: String, val onDone: () -> Unit)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            failed = status != TextToSpeech.SUCCESS
            if (ready) {
                tts?.setSpeechRate(0.98f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "tts done $utteranceId")
                        val cb = onDoneCallback
                        onDoneCallback = null
                        cb?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val cb = onDoneCallback
                        onDoneCallback = null
                        cb?.invoke()
                    }
                })
                pending?.let {
                    pending = null
                    speak(it.text, it.languageTag, it.onDone)
                }
            }
        }
    }

    val isAvailable: Boolean get() = ready && !failed

    /** Returns false when the device has no usable TTS engine for this language. */
    fun speak(text: String, languageTag: String, onDone: () -> Unit): Boolean {
        if (text.isBlank()) return false
        val engine = tts ?: return false
        if (failed) return false
        if (!ready) {
            pending = Pending(text, languageTag, onDone)
            return true
        }
        val locale = Locale.forLanguageTag(languageTag)
        val res = engine.setLanguage(locale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            onDone()
            return false
        }
        onDoneCallback = onDone
        val id = UUID.randomUUID().toString()
        val code = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (code != TextToSpeech.SUCCESS) {
            onDoneCallback = null
            onDone()
            return false
        }
        return true
    }

    fun stop() {
        onDoneCallback = null
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "KaoTts"
    }
}
