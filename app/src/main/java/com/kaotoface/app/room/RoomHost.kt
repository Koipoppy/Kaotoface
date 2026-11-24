package com.kaotoface.app.room

import android.content.Context
import android.util.Log
import com.kaotoface.app.core.Member
import com.kaotoface.app.core.Utterance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small authoritative relay on the host phone. Guests get the full timeline
 * on join; new lines are broadcast to everyone. No internet required.
 */
class RoomHost(
    context: Context,
    val code: String,
    private val hostName: String,
    private val snapshotProvider: () -> List<Utterance>,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onMembers(members: List<Member>)
        fun onGuestLine(line: Utterance)
        fun onPresence(listening: Boolean, allowGuestMic: Boolean)
        fun onClosed(reason: String)
        fun onNotice(message: String)
    }

    val port: Int = RoomWire.portFor(code)
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clients = ConcurrentHashMap<String, ClientConn>()
    private val counter = AtomicInteger(0)
    private var server: ServerSocket? = null
    private var beacon: LanBeacon? = null
    @Volatile private var running = false
    @Volatile var listening: Boolean = false
        private set
    @Volatile var allowGuestMic: Boolean = false
        private set
    private val hostMember = Member("host", hostName, isHost = true)

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(port), 24)
            s.soTimeout = 1200
            server = s
            running = true
            scope.launch { acceptLoop(s) }
            beacon = LanBeacon(appContext, code, port, hostName).also { it.start() }
            callbacks.onMembers(members())
            Log.i(TAG, "room $code listening on $port")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "cannot host room $code", t)
            runCatching { server?.close() }
            server = null
            running = false
            false
        }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running && scope.isActive) {
            try {
                val socket = server.accept()
                socket.tcpNoDelay = true
                scope.launch { handle(socket) }
            } catch (_: SocketTimeoutException) {
            } catch (t: Throwable) {
                if (running) {
                    Log.w(TAG, "accept failed", t)
                    running = false
                    callbacks.onClosed("房间服务已中断")
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        var conn: ClientConn? = null
        try {
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val first = reader.readLine() ?: return
            val hello = runCatching { JSONObject(first) }.getOrNull() ?: return
            if (hello.optString("t") != "hello") return
            val name = hello.optString("name").trim().ifBlank { "成员" }.take(16)
            val id = "g" + counter.incrementAndGet()
            val c = ClientConn(id, name, socket)
            conn = c
            clients[id] = c
            c.send(RoomWire.welcome(code, id, hostName, members(), snapshotProvider(), listening, allowGuestMic))
            callbacks.onMembers(members())
            broadcastPresence()
            socket.soTimeout = 0
            while (running) {
                val text = reader.readLine() ?: break
                val msg = runCatching { JSONObject(text) }.getOrNull() ?: continue
                when (msg.optString("t")) {
                    "ping" -> c.send(RoomWire.pong())
                    "bye" -> return
                    "line" -> {
                        if (!allowGuestMic) {
                            c.send(RoomWire.error("主持人未开放成员收音"))
                            continue
                        }
                        val obj = msg.optJSONObject("line") ?: continue
                        val line = RoomWire.lineFrom(obj) ?: continue
                        val finalized = line.copy(remote = true, speaker = name)
                        callbacks.onGuestLine(finalized)
                        broadcastLine(finalized, exceptId = id)
                    }
                }
            }
        } catch (t: Throwable) {
            if (running) Log.d(TAG, "client ended: ${t.message}")
        } finally {
            val c = conn
            if (c != null) {
                clients.remove(c.id)
                c.close()
                if (running) {
                    callbacks.onMembers(members())
                    broadcastPresence()
                }
            }
        }
    }

    fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        callbacks.onPresence(listening, allowGuestMic)
        broadcastPresence()
    }

    fun setAllowGuestMic(value: Boolean) {
        if (allowGuestMic == value) return
        allowGuestMic = value
        callbacks.onPresence(listening, allowGuestMic)
        broadcastPresence()
    }

    fun broadcastLine(line: Utterance, exceptId: String? = null) {
        val payload = RoomWire.line(line)
        clients.values.forEach { c ->
            if (c.id != exceptId) {
                runCatching { c.send(payload) }.onFailure { removeClient(c) }
            }
        }
    }

    fun broadcastClear() {
        val payload = RoomWire.clear()
        clients.values.forEach { c -> runCatching { c.send(payload) }.onFailure { removeClient(c) } }
    }

    fun notifyMembersChanged() {
        callbacks.onMembers(members())
        broadcastPresence()
    }

    private fun broadcastPresence() {
        val payload = RoomWire.presence(code, hostName, members(), listening, allowGuestMic)
        clients.values.forEach { c -> runCatching { c.send(payload) }.onFailure { removeClient(c) } }
    }

    private fun removeClient(c: ClientConn) {
        if (clients.remove(c.id) != null) {
            c.close()
            callbacks.onMembers(members())
        }
    }

    fun members(): List<Member> = buildList {
        add(hostMember)
        clients.values.sortedBy { it.id }.forEach { add(Member(it.id, it.name, false)) }
    }

    fun stop(notify: Boolean = true) {
        if (!running) return
        running = false
        if (notify) {
            val bye = RoomWire.bye("房主已结束房间")
            clients.values.forEach { runCatching { it.send(bye) } }
        }
        clients.values.forEach { it.close() }
        clients.clear()
        runCatching { server?.close() }
        server = null
        beacon?.stop()
        beacon = null
        scope.cancel()
        Log.i(TAG, "room $code stopped")
    }

    private class ClientConn(val id: String, val name: String, private val socket: Socket) {
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

        fun send(text: String) {
            synchronized(this) {
                writer.write(text)
                writer.write("\n")
                writer.flush()
            }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val TAG = "KaoHost"
    }
}
