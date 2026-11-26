package com.kaotoface.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavCodecTest {
    @Test
    fun wrapsPcm16AsRiffWave() {
        val pcm = ByteArray(32000) { 1 }
        val wav = WavCodec.wrapPcm16(pcm, 16000, 1)
        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + pcm.size, header.getInt(4))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(1, header.getShort(22).toInt())
        assertEquals(16000, header.getInt(24))
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(pcm.size, header.getInt(40))
    }
}
