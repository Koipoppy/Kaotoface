package com.kaotoface.app.room

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

object LanNet {
    fun localIpv4(): String? = runCatching {
        val ifaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
        ifaces.asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private fun broadcastTargets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        out.add(InetAddress.getByName("255.255.255.255"))
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .forEach { nic ->
                    nic.interfaceAddresses.forEach { ia ->
                        ia.broadcast?.let { out.add(it) }
                    }
                }
        }
        return out.toList()
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? = runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wifi?.createMulticastLock("kaotoface-lan")?.apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()

    fun sendBroadcast(socket: DatagramSocket, payload: String) {
        val data = payload.toByteArray()
        broadcastTargets().forEach { addr ->
            runCatching {
                socket.send(DatagramPacket(data, data.size, addr, RoomWire.DISCOVERY_PORT))
            }
        }
    }
}

/** Host-side beacon: announces room code on the LAN and answers guest probes. */
class LanBeacon(
    private val context: Context,
    private val code: String,
    private val port: Int,
    private val roomName: String,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: DatagramSocket? = null
    private var job: Job? = null
    private var lock: WifiManager.MulticastLock? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        lock = LanNet.acquireMulticastLock(context)
        socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(RoomWire.DISCOVERY_PORT))
            }
        }.getOrElse {
            Log.w(TAG, "discovery port busy, using ephemeral port", it)
            runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull()
        }
        val s = socket ?: return
        s.soTimeout = 1000
        job = scope.launch {
            var nextBeacon = 0L
            while (running && isActive) {
                val now = System.currentTimeMillis()
                if (now >= nextBeacon) {
                    nextBeacon = now + 1600
                    val ip = LanNet.localIpv4().orEmpty()
                    LanNet.sendBroadcast(s, RoomWire.beacon(code, port, roomName, ip))
                }
                try {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    s.receive(packet)
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    val obj = runCatching { JSONObject(text) }.getOrNull() ?: continue
                    if (obj.optString("t") == "kf_probe" && obj.optString("code") == code) {
                        val reply = RoomWire.beacon(code, port, roomName, LanNet.localIpv4().orEmpty())
                        val data = reply.toByteArray()
                        s.send(DatagramPacket(data, data.size, packet.address, packet.port))
                    }
                } catch (_: SocketTimeoutException) {
                } catch (t: Throwable) {
                    if (running) Log.w(TAG, "beacon loop", t)
                }
            }
        }
    }

    fun stop() {
        running = false
        scope.cancel()
        runCatching { socket?.close() }
        socket = null
        runCatching { lock?.release() }
        lock = null
    }

    private companion object {
        const val TAG = "KaoBeacon"
    }
}

/** Guest-side discovery: probes the LAN and waits for a beacon carrying the code. */
class LanFinder(
    private val context: Context,
    private val code: String,
) {
    data class Found(val host: String, val port: Int, val name: String)

    fun find(timeoutMs: Long = 7000): Found? {
        val lock = LanNet.acquireMulticastLock(context)
        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(RoomWire.DISCOVERY_PORT))
            }
        }.getOrElse {
            runCatching { DatagramSocket().apply { broadcast = true } }.getOrNull()
        } ?: run {
            lock?.release()
            return null
        }
        return try {
            socket.soTimeout = 350
            val deadline = System.currentTimeMillis() + timeoutMs
            var nextProbe = 0L
            val probe = RoomWire.probe(code)
            while (System.currentTimeMillis() < deadline) {
                val now = System.currentTimeMillis()
                if (now >= nextProbe) {
                    nextProbe = now + 650
                    LanNet.sendBroadcast(socket, probe)
                }
                try {
                    val buf = ByteArray(2048)
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val obj = runCatching {
                        JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                    }.getOrNull() ?: continue
                    val beacon = RoomWire.parseBeacon(obj, packet.address.hostAddress ?: continue) ?: continue
                    if (beacon.code == code) {
                        return Found(beacon.host, beacon.port, beacon.name)
                    }
                } catch (_: SocketTimeoutException) {
                } catch (t: Throwable) {
                    Log.w(TAG, "finder", t)
                }
            }
            null
        } finally {
            runCatching { socket.close() }
            runCatching { lock?.release() }
        }
    }

    private companion object {
        const val TAG = "KaoFinder"
    }
}
