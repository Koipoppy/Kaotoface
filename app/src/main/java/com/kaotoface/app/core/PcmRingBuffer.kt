package com.kaotoface.app.core

/**
 * Absolute-byte PCM history used to re-decode a whole utterance. The microphone
 * feed is continuous, so the pre-roll before the first speech trigger is still
 * available here even though the streaming recognizer already consumed it.
 */
class PcmRingBuffer(capacityBytes: Int) {
    private val data = ByteArray(capacityBytes.coerceAtLeast(2) and -2)
    private var write = 0
    private var size = 0
    private var total = 0L

    @Synchronized
    fun append(bytes: ByteArray, length: Int) {
        if (length <= 0) return
        var offset = 0
        while (offset < length) {
            val count = minOf(length - offset, data.size - write)
            System.arraycopy(bytes, offset, data, write, count)
            write = (write + count) % data.size
            size = minOf(data.size, size + count)
            total += count
            offset += count
        }
    }

    @Synchronized
    fun totalBytes(): Long = total

    /**
     * Returns audio in [startByte, endByte), clamped to what is still buffered.
     * A null result means the requested beginning has already been overwritten
     * or the interval is empty.
     */
    @Synchronized
    fun snapshot(startByte: Long, endByte: Long): ByteArray? {
        val oldest = (total - size).coerceAtLeast(0L)
        val from = maxOf(startByte, oldest)
        val to = minOf(endByte, total)
        if (to <= from) return null
        val count = (to - from).toInt()
        if (count <= 0) return null
        val out = ByteArray(count)
        val base = (write - size + data.size * 2) % data.size
        for (i in 0 until count) {
            out[i] = data[(base + (from - oldest).toInt() + i) % data.size]
        }
        return out
    }

    @Synchronized
    fun clear() {
        write = 0
        size = 0
        total = 0L
    }
}
