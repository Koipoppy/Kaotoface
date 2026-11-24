package com.kaotoface.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 16 kHz mono PCM16 microphone stream. One thread reads, computes a smoothed level,
 * and forwards the raw bytes to the dual recognizers.
 */
class AudioCapture(
    private val onAudio: (ByteArray, Int) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var record: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    @Volatile var paused: Boolean = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (running.get()) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            onError("无法创建麦克风缓冲")
            return
        }
        val bufBytes = max(minBuf * 2, CHUNK_SAMPLES * 2 * 2)
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufBytes)
                .build()
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord build failed", t)
            onError("无法打开麦克风")
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onError("麦克风初始化失败")
            return
        }
        record = rec
        runCatching {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
            ns = NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true }
        }
        running.set(true)
        rec.startRecording()
        thread = Thread({ loop(rec) }, "kaotoface-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val chunk = ByteArray(CHUNK_SAMPLES * 2)
        var smoothed = 0f
        var lastLevelAt = 0L
        try {
            while (running.get()) {
                val n = rec.read(chunk, 0, chunk.size)
                if (n <= 0) continue
                if (!paused) {
                    onAudio(chunk, n)
                }
                val rms = rmsOf(chunk, n)
                val db = 20.0 * log10(max(rms, 1.0) / 32768.0)
                val target = ((db + 58.0) / 48.0).coerceIn(0.0, 1.0).toFloat()
                smoothed = if (target > smoothed) smoothed * 0.35f + target * 0.65f
                else smoothed * 0.82f + target * 0.18f
                val now = System.currentTimeMillis()
                if (now - lastLevelAt >= 70) {
                    lastLevelAt = now
                    onLevel(if (paused) 0f else smoothed)
                }
            }
        } catch (t: Throwable) {
            if (running.get()) {
                Log.e(TAG, "audio loop failed", t)
                onError("麦克风读取中断")
            }
        }
    }

    private fun rmsOf(data: ByteArray, len: Int): Double {
        if (len < 2) return 0.0
        var sum = 0.0
        var i = 0
        val limit = len and 1.inv()
        while (i < limit) {
            val lo = data[i].toInt() and 0xff
            val hi = data[i + 1].toInt()
            val s = (hi shl 8) or lo
            sum += (s * s).toDouble()
            i += 2
        }
        val count = limit / 2
        return sqrt(sum / count)
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { record?.stop() }
        thread?.join(600)
        thread = null
        runCatching { aec?.release() }
        runCatching { ns?.release() }
        aec = null
        ns = null
        runCatching { record?.release() }
        record = null
        onLevel(0f)
    }

    companion object {
        private const val TAG = "KaoAudio"
        const val SAMPLE_RATE = 16000
        const val CHUNK_SAMPLES = 1600 // 100 ms
    }
}
