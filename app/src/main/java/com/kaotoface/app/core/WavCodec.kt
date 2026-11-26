package com.kaotoface.app.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal PCM16 mono WAV wrapper for the DeepSeek ASR multipart upload. */
object WavCodec {
    fun wrapPcm16(pcm: ByteArray, sampleRate: Int = 16000, channels: Int = 1): ByteArray {
        val dataSize = pcm.size
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        return header.array() + pcm
    }
}
