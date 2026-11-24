package com.kaotoface.app.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "session.json")
    private val lock = Any()

    fun load(): List<Utterance> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("lines") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val en = o.optString("en")
                    val zh = o.optString("zh")
                    if (en.isBlank() && zh.isBlank()) continue
                    add(
                        Utterance(
                            id = o.optString("id").ifBlank { "h$i" },
                            en = en,
                            zh = zh,
                            source = Lang.fromCode(o.optString("src")) ?: Lang.EN,
                            createdAt = o.optLong("ts", System.currentTimeMillis()),
                            failed = o.optBoolean("failed", false),
                            speaker = o.optString("speaker").takeIf { it.isNotBlank() },
                            remote = true,
                        ),
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "failed to load history", it)
            emptyList()
        }
    }

    fun save(lines: List<Utterance>) = synchronized(lock) {
        runCatching {
            val arr = JSONArray()
            lines.takeLast(1000).forEach { u ->
                arr.put(
                    JSONObject().apply {
                        put("id", u.id)
                        put("en", u.en)
                        put("zh", u.zh)
                        put("src", u.source.code)
                        put("ts", u.createdAt)
                        put("failed", u.failed)
                        if (!u.speaker.isNullOrBlank()) put("speaker", u.speaker)
                    },
                )
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("savedAt", System.currentTimeMillis())
                put("lines", arr)
            }
            val tmp = File(file.parentFile, "session.json.tmp")
            tmp.writeText(root.toString())
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }.onFailure { Log.w(TAG, "failed to save history", it) }
    }

    fun clear() = synchronized(lock) {
        runCatching { file.delete() }
    }

    private companion object {
        const val TAG = "KaoHistory"
    }
}
