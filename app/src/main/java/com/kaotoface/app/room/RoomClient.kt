package com.kaotoface.app.room

import android.util.Log
import com.kaotoface.app.core.Member
import com.kaotoface.app.core.Utterance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class RoomClient(
    val code: String,
    private val host: String,
    private val guestName: String,
    private val portOverride: Int? = null,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onWelcome(
            selfId: String,
            hostName: String,
            members: List<Member>,
            lines: List<Utterance>,
            listening: Boolean,
            allowGuestMic: Boolean,
        )

        fun onPresence(hostName: String, members: List<Member>, listening: Boolean, allowGuestMic: Boolean)
        fun onLine(line: Utterance)
        fun onClear()
        fun onClosed(reason: String)
        fun onNotice(message: String)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var pingJob: Job? = null
    @Volatile private var running = false
    @Volatile var selfId: String = ""
        private set

    val port: Int = portOverride ?: RoomWire.portFor(code)

    fun connect(): Boolean {
        if (running) return true
        return try {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), 6000)
            s.soTimeout = 0
            socket = s
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            running = true
            send(RoomWire.hello(guestName))
            scope.launch { readLoop(s) }
            pingJob = scope.launch {
                while (running && isActive) {
                    delay(20000)
                    runCatching { send(RoomWire.ping()) }
                }
            }
            Log.i(TAG, "connected to $host:$port")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "connect failed", t)
            running = false
            closeQuietly()
            false
        }
    }

    private fun readLoop(s: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            while (running) {
                val text = reader.readLine() ?: break
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: continue
                when (msg.optString("t")) {
                    "welcome" -> {
                        selfId = msg.optString("self")
                        callbacks.onWelcome(
                            selfId,
                            msg.optString("hostName"),
                            RoomWire.membersFrom(msg.optJSONArray("members")),
                            RoomWire.linesFrom(msg.optJSONArray("lines")),
                            msg.optBoolean("listening", false),
                            msg.optBoolean("guestMic", false),
                        )
                    }
                    "presence" -> callbacks.onPresence(
                        msg.optString("hostName"),
                        RoomWire.membersFrom(msg.optJSONArray("members")),
                        msg.optBoolean("listening", false),
                        msg.optBoolean("guestMic", false),
                    )
                    "line" -> msg.optJSONObject("line")?.let { RoomWire.lineFrom(it) }?.let(callbacks::onLine)
                    "clear" -> callbacks.onClear()
                    "pong" -> Unit
                    "error" -> callbacks.onNotice(msg.optString("message").ifBlank { "房间服务返回错误" })
                    "bye" -> {
                        val reason = msg.optString("reason").ifBlank { "房主已关闭房间" }
                        running = false
                        callbacks.onClosed(reason)
                        return
                    }
                }
            }
            if (running) {
                running = false
                callbacks.onClosed("与房间的连接已断开")
            }
        } catch (t: Throwable) {
            if (running) {
                running = false
                Log.w(TAG, "read loop ended", t)
                callbacks.onClosed("与房间的连接已断开")
            }
        }
    }

    fun sendLine(line: Utterance) {
        if (!running) return
        runCatching { send(RoomWire.line(line)) }.onFailure {
            Log.w(TAG, "send line failed", it)
        }
    }

    fun stop(notify: Boolean = true) {
        if (!running) {
            closeQuietly()
            return
        }
        if (notify) runCatching { send(RoomWire.bye("成员离开")) }
        running = false
        pingJob?.cancel()
        pingJob = null
        closeQuietly()
        scope.cancel()
    }

    private fun send(text: String) {
        val w = writer ?: return
        synchronized(this) {
            w.write(text)
            w.write("\n")
            w.flush()
        }
    }

    private fun closeQuietly() {
        runCatching { writer?.close() }
        writer = null
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val TAG = "KaoClient"
    }
}
