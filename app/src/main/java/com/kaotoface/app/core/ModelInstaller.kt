package com.kaotoface.app.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Copies the bundled Vosk models out of the APK once, so the recognizer can be
 * constructed from a real directory. Models live in assets/vosk/{en,zh}.
 */
object ModelInstaller {
    private const val ASSET_ROOT = "vosk"
    private const val VERSION = "1"

    data class Dirs(val en: File, val zh: File)

    fun dirs(context: Context): Dirs {
        val root = File(context.filesDir, "voice")
        return Dirs(File(root, "en"), File(root, "zh"))
    }

    private fun marker(context: Context) = File(context.filesDir, "voice/.version")

    fun isInstalled(context: Context): Boolean {
        val d = dirs(context)
        return marker(context).readTextOrNull() == VERSION &&
            File(d.en, "am/final.mdl").exists() &&
            File(d.zh, "am/final.mdl").exists()
    }

    suspend fun ensure(context: Context, onProgress: (Float, String) -> Unit = { _, _ -> }): Dirs =
        withContext(Dispatchers.IO) {
            val dirs = dirs(context)
            if (isInstalled(context)) return@withContext dirs

            onProgress(0f, "正在准备语音模型")
            val enFiles = listAssets(context, "$ASSET_ROOT/en")
            val zhFiles = listAssets(context, "$ASSET_ROOT/zh")
            val total = (enFiles.size + zhFiles.size).coerceAtLeast(1)
            var done = 0

            dirs.en.deleteRecursively()
            dirs.zh.deleteRecursively()
            dirs.en.mkdirs()
            dirs.zh.mkdirs()

            suspend fun copyAll(assetPrefix: String, relFiles: List<String>, dest: File, label: String) {
                relFiles.forEach { rel ->
                    coroutineContext.ensureActive()
                    val out = File(dest, rel)
                    out.parentFile?.mkdirs()
                    context.assets.open("$assetPrefix/$rel").use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    done++
                    onProgress(done.toFloat() / total, label)
                }
            }

            copyAll("$ASSET_ROOT/en", enFiles, dirs.en, "正在准备英文语音模型")
            copyAll("$ASSET_ROOT/zh", zhFiles, dirs.zh, "正在准备中文语音模型")

            marker(context).writeText(VERSION)
            onProgress(1f, "语音模型就绪")
            dirs
        }

    private fun listAssets(context: Context, prefix: String): List<String> {
        val out = ArrayList<String>()
        val stack = ArrayDeque(listOf(""))
        while (stack.isNotEmpty()) {
            val rel = stack.removeLast()
            val path = if (rel.isEmpty()) prefix else "$prefix/$rel"
            val children = context.assets.list(path).orEmpty()
            if (children.isEmpty()) {
                if (rel.isNotEmpty()) out.add(rel)
            } else {
                children.forEach { child ->
                    val childRel = if (rel.isEmpty()) child else "$rel/$child"
                    val childPath = "$prefix/$childRel"
                    if (context.assets.list(childPath).orEmpty().isEmpty()) {
                        out.add(childRel)
                    } else {
                        stack.addLast(childRel)
                    }
                }
            }
        }
        return out
    }

    private fun File.readTextOrNull(): String? = runCatching { if (exists()) readText() else null }.getOrNull()
}
