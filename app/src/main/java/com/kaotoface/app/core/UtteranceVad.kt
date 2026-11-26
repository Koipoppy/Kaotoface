package com.kaotoface.app.core

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Local VAD for the no-Vosk build. It keeps a pre-roll of raw PCM, starts a
 * placeholder when speech crosses an adaptive energy threshold, and emits one
 * complete utterance after a silence tail. No language model is involved.
 */
class UtteranceVad(
    private val onSpeechStart: () -> Unit,
    private val onSpeechCancel: () -> Unit,
    private val onUtterance: (ByteArray) -> Unit,
) {
    private val ring = PcmRingBuffer(SAMPLE_RATE * 2 * RING_SECONDS)
    private var totalBytes = 0L
    private var speech = false
    private var speechStartByte = 0L
    private var lastVoiceByte = 0L
    private var noiseDb = -60.0

    fun feed(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        ring.append(bytes, length)
        totalBytes += length
        val db = dbOf(bytes, length)

        if (!speech) {
            if (db > max(-48.0, noiseDb + 8.0)) {
                speech = true
                speechStartByte = max(0L, totalBytes - PRE_ROLL_BYTES)
                lastVoiceByte = totalBytes
                onSpeechStart()
            } else {
                noiseDb = noiseDb * 0.98 + db * 0.02
            }
            return
        }

        if (db > max(-50.0, noiseDb + 5.0)) {
            lastVoiceByte = totalBytes
        }
        when {
            totalBytes - lastVoiceByte >= SILENCE_BYTES -> finish(lastVoiceByte)
            totalBytes - speechStartByte >= MAX_UTTERANCE_BYTES -> finish(totalBytes)
        }
    }

    fun flush() {
        if (speech) finish(totalBytes)
    }

    private fun finish(endByte: Long) {
        val start = speechStartByte
        speech = false
        if (endByte - start < MIN_SPEECH_BYTES) {
            onSpeechCancel()
            return
        }
        val pcm = ring.snapshot(start, endByte)
        if (pcm == null || pcm.size < MIN_SPEECH_BYTES) {
            onSpeechCancel()
            return
        }
        onUtterance(pcm)
    }

    private fun dbOf(bytes: ByteArray, length: Int): Double {
        val limit = length and 1.inv()
        if (limit <= 0) return -96.0
        var sum = 0.0
        var i = 0
        while (i < limit) {
            val lo = bytes[i].toInt() and 0xff
            val hi = bytes[i + 1].toInt()
            val sample = (hi shl 8) or lo
            sum += sample.toDouble() * sample
            i += 2
        }
        val rms = sqrt(sum / (limit / 2))
        return 20.0 * log10(max(rms, 1.0) / 32768.0)
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val RING_SECONDS = 25
        const val PRE_ROLL_BYTES = SAMPLE_RATE * 2 * 7 / 10 // 700ms
        const val SILENCE_BYTES = SAMPLE_RATE * 2 * 7 / 10 // 700ms
        const val MIN_SPEECH_BYTES = SAMPLE_RATE * 2 * 3 / 10 // 300ms
        const val MAX_UTTERANCE_BYTES = SAMPLE_RATE * 2 * 20 // 20s
    }
}
