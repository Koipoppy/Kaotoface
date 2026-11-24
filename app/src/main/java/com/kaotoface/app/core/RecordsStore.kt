package com.kaotoface.app.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists every listening session as its own named discussion record. */
class RecordsStore(context: Context) {
    private val file = File(context.filesDir, "records.json")
    private val lock = Any()

    fun load(): List<DiscussionRecord> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("records") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id").ifBlank { "r$i" }
                    val name = o.optString("name").ifBlank { defaultName(this, o.optLong("createdAt", System.currentTimeMillis())) }
                    val createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    add(
                        DiscussionRecord(
                            id = id,
                            name = name,
                            createdAt = createdAt,
                            lines = readLines(o.optJSONArray("lines")),
                        ),
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "failed to load records", it)
            emptyList()
        }
    }

    fun save(records: List<DiscussionRecord>) = synchronized(lock) {
        runCatching {
            val arr = JSONArray()
            records.forEach { record ->
                arr.put(
                    JSONObject().apply {
                        put("id", record.id)
                        put("name", record.name)
                        put("createdAt", record.createdAt)
                        put("lines", writeLines(record.lines))
                    },
                )
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("savedAt", System.currentTimeMillis())
                put("records", arr)
            }
            val tmp = File(file.parentFile, "records.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "failed to save records", it) }
    }

    /** "2026-11-24 · 01" — date plus the next sequence number for that date. */
    fun defaultName(existing: List<DiscussionRecord>, at: Long = System.currentTimeMillis()): String {
        val date = day(at)
        val used = existing.count { day(it.createdAt) == date }
        return "$date · ${"%02d".format(used + 1)}"
    }

    /** A readable date/time for the record row, e.g. "11-24 21:35". */
    fun stamp(at: Long): String = synchronized(lock) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))
    }

    private fun day(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(at))

    private fun readLines(arr: JSONArray?): List<Utterance> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val en = o.optString("en")
                val zh = o.optString("zh")
                if (en.isBlank() && zh.isBlank()) continue
                add(
                    Utterance(
                        id = o.optString("id").ifBlank { "l$i" },
                        en = en,
                        zh = zh,
                        source = Lang.fromCode(o.optString("src")) ?: Lang.EN,
                        createdAt = o.optLong("ts", System.currentTimeMillis()),
                        failed = o.optBoolean("failed", false),
                        speaker = o.optString("speaker").takeIf { it.isNotBlank() },
                        remote = o.optBoolean("remote", false),
                    ),
                )
            }
        }
    }

    private fun writeLines(lines: List<Utterance>): JSONArray {
        val arr = JSONArray()
        lines.takeLast(2000).forEach { u ->
            arr.put(
                JSONObject().apply {
                    put("id", u.id)
                    put("en", u.en)
                    put("zh", u.zh)
                    put("src", u.source.code)
                    put("ts", u.createdAt)
                    put("failed", u.failed)
                    if (!u.speaker.isNullOrBlank()) put("speaker", u.speaker)
                    put("remote", u.remote)
                },
            )
        }
        return arr
    }

    private companion object {
        const val TAG = "KaoRecords"
    }
}
