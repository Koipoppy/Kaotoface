package com.kaotoface.app.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {
    data class Built(val markdown: String, val plain: String, val count: Int)

    fun build(lines: List<Utterance>, roomCode: String?): Built {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val count = lines.size
        val duration = if (lines.size >= 2) {
            ((lines.last().createdAt - lines.first().createdAt) / 1000).coerceAtLeast(0)
        } else 0L
        val durText = String.format(Locale.US, "%02d:%02d", duration / 60, duration % 60)
        val roomText = if (roomCode.isNullOrBlank()) "" else " · 房间 $roomCode"
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        val speakers = lines.mapNotNull { it.speaker?.takeIf(String::isNotBlank) }.distinct()
        val showSpeaker = speakers.size > 1

        val md = buildString {
            appendLine("# 双语讨论记录")
            appendLine()
            appendLine("> $now · $count 句 · $durText$roomText")
            if (lines.isEmpty()) {
                appendLine()
                appendLine("_这次对话还没有内容。_")
            }
            appendLine()
            lines.forEachIndexed { index, u ->
                appendLine("---")
                appendLine()
                val speaker = if (showSpeaker) u.speaker?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "" else ""
                appendLine("**%02d · %s%s**  ".format(index + 1, timeFmt.format(Date(u.createdAt)), speaker))
                appendLine("**EN**  ${u.en.ifBlank { "—" }}  ")
                appendLine("**中**  ${u.zh.ifBlank { "—" }}")
                appendLine()
            }
        }

        val plain = buildString {
            appendLine("双语讨论记录")
            appendLine("$now · $count 句 · $durText$roomText")
            appendLine("=".repeat(46))
            appendLine()
            lines.forEachIndexed { index, u ->
                val speaker = if (showSpeaker) u.speaker?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: "" else ""
                appendLine("[${timeFmt.format(Date(u.createdAt))}] #%02d%s".format(index + 1, speaker))
                appendLine("  EN  ${u.en.ifBlank { "—" }}")
                appendLine("  中  ${u.zh.ifBlank { "—" }}")
                appendLine()
            }
            if (lines.isEmpty()) appendLine("（没有记录）")
            appendLine("— 双语对照记录")
        }
        return Built(md, plain, count)
    }

    fun writeExport(context: Context, lines: List<Utterance>, roomCode: String?, markdown: Boolean): File {
        val built = build(lines, roomCode)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "Discussion_$stamp.${if (markdown) "md" else "txt"}")
        file.writeText(if (markdown) built.markdown else built.plain)
        return file
    }

    fun share(context: Context, file: File, markdown: Boolean): Boolean {
        return try {
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val mime = if (markdown) "text/markdown" else "text/plain"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "双语讨论记录")
                putExtra(Intent.EXTRA_TEXT, "双语逐句对照讨论记录")
                clipData = ClipData.newRawUri("bilingual-notes", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享讨论记录").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (t: Throwable) {
            Log.w("KaoExport", "share failed", t)
            false
        }
    }

    fun copyText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("双语讨论记录", text))
    }

    /** Returns a human readable destination description, or null on failure. */
    fun saveToDownloads(context: Context, file: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                    put(MediaStore.Downloads.MIME_TYPE, if (file.extension == "md") "text/markdown" else "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BilingualNotes")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "下载/BilingualNotes/${file.name}"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KaoToFace")
                dir.mkdirs()
                val out = File(dir, file.name)
                file.copyTo(out, overwrite = true)
                out.absolutePath
            }
        } catch (t: Throwable) {
            Log.w("KaoExport", "save failed", t)
            null
        }
    }
}
