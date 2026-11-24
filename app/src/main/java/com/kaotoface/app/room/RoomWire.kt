package com.kaotoface.app.room

import com.kaotoface.app.core.Lang
import com.kaotoface.app.core.Member
import com.kaotoface.app.core.Utterance
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One JSON object per line over TCP. Keep messages compact. */
object RoomWire {
    const val VERSION = 1
    const val PORT_BASE = 30000
    const val DISCOVERY_PORT = 19999

    fun portFor(code: String): Int = PORT_BASE + (code.toIntOrNull() ?: 0)

    fun hello(name: String): String = JSONObject()
        .put("t", "hello")
        .put("ver", VERSION)
        .put("name", name)
        .toString()

    fun welcome(
        room: String,
        selfId: String,
        hostName: String,
        members: List<Member>,
        lines: List<Utterance>,
        listening: Boolean,
        allowGuestMic: Boolean,
    ): String = JSONObject()
        .put("t", "welcome")
        .put("room", room)
        .put("self", selfId)
        .put("hostName", hostName)
        .put("members", membersJson(members))
        .put("lines", linesJson(lines))
        .put("listening", listening)
        .put("guestMic", allowGuestMic)
        .toString()

    fun presence(
        room: String,
        hostName: String,
        members: List<Member>,
        listening: Boolean,
        allowGuestMic: Boolean,
    ): String = JSONObject()
        .put("t", "presence")
        .put("room", room)
        .put("hostName", hostName)
        .put("members", membersJson(members))
        .put("listening", listening)
        .put("guestMic", allowGuestMic)
        .toString()

    fun line(line: Utterance): String = JSONObject()
        .put("t", "line")
        .put("line", lineJson(line))
        .toString()

    fun clear(): String = JSONObject().put("t", "clear").toString()

    fun ping(): String = JSONObject().put("t", "ping").toString()

    fun pong(): String = JSONObject().put("t", "pong").toString()

    fun bye(reason: String): String = JSONObject().put("t", "bye").put("reason", reason).toString()

    fun error(message: String): String = JSONObject().put("t", "error").put("message", message).toString()

    fun beacon(code: String, port: Int, name: String, ip: String): String = JSONObject()
        .put("t", "kf")
        .put("v", VERSION)
        .put("code", code)
        .put("port", port)
        .put("name", name)
        .put("ip", ip)
        .toString()

    fun probe(code: String): String = JSONObject()
        .put("t", "kf_probe")
        .put("code", code)
        .toString()

    fun lineJson(u: Utterance): JSONObject = JSONObject()
        .put("id", u.id.ifBlank { UUID.randomUUID().toString() })
        .put("en", u.en)
        .put("zh", u.zh)
        .put("src", u.source.code)
        .put("ts", u.createdAt)
        .apply { if (!u.speaker.isNullOrBlank()) put("speaker", u.speaker) }

    fun lineFrom(o: JSONObject): Utterance? {
        val en = o.optString("en")
        val zh = o.optString("zh")
        if (en.isBlank() && zh.isBlank()) return null
        return Utterance(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            en = en,
            zh = zh,
            source = Lang.fromCode(o.optString("src")) ?: Lang.EN,
            createdAt = o.optLong("ts", System.currentTimeMillis()),
            speaker = o.optString("speaker").takeIf { it.isNotBlank() },
            remote = true,
        )
    }

    fun membersJson(members: List<Member>): JSONArray = JSONArray().apply {
        members.forEach { m ->
            put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("host", m.isHost),
            )
        }
    }

    fun membersFrom(arr: JSONArray?): List<Member> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                add(Member(id, o.optString("name").ifBlank { "成员" }, o.optBoolean("host", false)))
            }
        }
    }

    fun linesJson(lines: List<Utterance>): JSONArray = JSONArray().apply {
        lines.takeLast(500).forEach { put(lineJson(it)) }
    }

    fun linesFrom(arr: JSONArray?): List<Utterance> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { lineFrom(it) }?.let { add(it) }
            }
        }
    }

    data class Beacon(val code: String, val port: Int, val name: String, val ip: String, val host: String)

    fun parseBeacon(o: JSONObject, fromHost: String): Beacon? {
        if (o.optString("t") != "kf") return null
        val code = o.optString("code")
        val port = o.optInt("port", 0)
        if (code.isBlank() || port <= 0) return null
        return Beacon(code, port, o.optString("name"), o.optString("ip"), fromHost)
    }
}
