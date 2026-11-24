package com.kaotoface.app.core

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class JudgeOutcome {
    data class Success(
        val src: Lang,
        val en: String,
        val zh: String,
        val latencyMs: Long,
    ) : JudgeOutcome()

    data object NoSpeech : JudgeOutcome()
    data class Failure(val message: String) : JudgeOutcome()
}

/**
 * The semantic core of KaoToFace: given two ASR candidates of the same audio
 * segment, DeepSeek decides which one is real speech, repairs the transcript,
 * and returns a clean bilingual pair.
 */
class DeepSeekTranslator {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    fun judge(
        settings: AppSettings,
        enCandidate: String,
        zhCandidate: String,
        recent: List<Utterance>,
    ): JudgeOutcome {
        if (enCandidate.isBlank() && zhCandidate.isBlank()) return JudgeOutcome.NoSpeech
        val key = settings.apiKey.trim()
        if (key.isBlank()) return JudgeOutcome.Failure("未配置 DeepSeek API Key，请到设置中填写")

        val url = endpoint(settings.baseUrl)
        val system = systemPrompt(settings.tone)
        val user = userPrompt(enCandidate, zhCandidate, recent)
        val payload = JSONObject().apply {
            put("model", settings.model)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                },
            )
            put("max_tokens", 1200)
            put("temperature", 0.1)
            put("stream", false)
            put("response_format", JSONObject().put("type", "json_object"))
            if (isDeepSeek(settings.baseUrl)) {
                put("thinking", JSONObject().put("type", "disabled"))
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", "KaoToFace/1.0 (Android)")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        var lastError = "翻译服务暂时不可用"
        var attempt = 0
        var outcome: JudgeOutcome? = null
        while (attempt < 2 && outcome == null) {
            val started = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val content = extractContent(body)
                            ?: return JudgeOutcome.Failure("翻译服务返回空内容")
                        return parseResult(content, started, enCandidate, zhCandidate)
                    }
                    val msg = apiError(body)
                    outcome = when (resp.code) {
                        401, 403 -> JudgeOutcome.Failure("API Key 无效或没有权限")
                        402 -> JudgeOutcome.Failure("DeepSeek 账户余额不足")
                        429 -> JudgeOutcome.Failure("请求过于频繁，请稍后再试")
                        in 500..599 -> {
                            lastError = "翻译服务异常（${resp.code}）"
                            if (attempt == 0) null else JudgeOutcome.Failure(lastError)
                        }
                        else -> JudgeOutcome.Failure(msg ?: "翻译失败（HTTP ${resp.code}）")
                    }
                }
            } catch (e: IOException) {
                Log.w(TAG, "translation call failed (attempt ${attempt + 1})", e)
                lastError = "网络连接失败，请检查网络"
                if (attempt == 1) outcome = JudgeOutcome.Failure(lastError)
            } catch (t: Throwable) {
                Log.w(TAG, "translation failed", t)
                return JudgeOutcome.Failure("翻译失败：${t.message ?: "未知错误"}")
            }
            if (outcome == null) {
                attempt++
                Thread.sleep(450)
            }
        }
        return outcome ?: JudgeOutcome.Failure(lastError)
    }

    /** Reads the provider's live model catalogue; names come from the API as-is. */
    fun listModels(settings: AppSettings): Result<List<ModelInfo>> {
        val key = settings.apiKey.trim()
        if (key.isBlank()) return Result.failure(IllegalStateException("请先填写 API Key"))
        val request = Request.Builder()
            .url(modelsEndpoint(settings.baseUrl))
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("User-Agent", "KaoToFace/1.0 (Android)")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(
                        IllegalStateException(
                            when (resp.code) {
                                401, 403 -> "API Key 无效或没有权限"
                                else -> apiError(body) ?: "读取模型失败（HTTP ${resp.code}）"
                            },
                        ),
                    )
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                    ?: return Result.failure(IllegalStateException("模型列表格式异常"))
                val data = root.optJSONArray("data")
                    ?: return Result.failure(IllegalStateException("模型列表为空"))
                val models = buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val id = item.optString("id").trim()
                        if (id.isBlank()) continue
                        val name = item.optString("name").trim().ifBlank { id }
                        add(ModelInfo(id = id, name = name))
                    }
                }
                if (models.isEmpty()) {
                    Result.failure(IllegalStateException("没有可用模型"))
                } else {
                    Result.success(models)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "list models failed", t)
            Result.failure(IllegalStateException("读取模型失败：${t.message ?: "网络错误"}"))
        }
    }

    fun testConnection(settings: AppSettings): Result<Long> {
        val key = settings.apiKey.trim()
        if (key.isBlank()) return Result.failure(IllegalStateException("请先填写 API Key"))
        val payload = JSONObject().apply {
            put("model", settings.model)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", "只输出 JSON，不要解释。"))
                    .put(JSONObject().put("role", "user").put("content", """返回 {"ok":true}""")),
            )
            put("max_tokens", 200)
            put("temperature", 0)
            put("response_format", JSONObject().put("type", "json_object"))
            if (isDeepSeek(settings.baseUrl)) put("thinking", JSONObject().put("type", "disabled"))
        }
        val request = Request.Builder()
            .url(endpoint(settings.baseUrl))
            .header("Authorization", "Bearer $key")
            .header("User-Agent", "KaoToFace/1.0 (Android)")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        val started = System.currentTimeMillis()
        return try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    Result.success(System.currentTimeMillis() - started)
                } else {
                    Result.failure(IllegalStateException(apiError(body) ?: "HTTP ${resp.code}"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(IllegalStateException("连接失败：${t.message ?: "网络错误"}"))
        }
    }

    private fun parseResult(
        content: String,
        started: Long,
        enCandidate: String,
        zhCandidate: String,
    ): JudgeOutcome {
        val jsonText = stripFence(content)
        val obj = runCatching { JSONObject(jsonText) }.getOrElse {
            Log.w(TAG, "bad JSON: $content")
            return JudgeOutcome.Failure("翻译结果格式异常")
        }
        var src = obj.optString("src", "").trim().lowercase()
        var en = obj.optString("en", "").trim()
        var zh = obj.optString("zh", "").trim()
        if (src == "none" || (en.isBlank() && zh.isBlank())) return JudgeOutcome.NoSpeech
        val lang = when {
            src.startsWith("en") || src == "english" -> Lang.EN
            src.startsWith("zh") || src == "chinese" || src == "mandarin" -> Lang.ZH
            enCandidate.isBlank() -> Lang.ZH
            zhCandidate.isBlank() -> Lang.EN
            en.isNotBlank() && zh.isBlank() -> Lang.EN
            zh.isNotBlank() && en.isBlank() -> Lang.ZH
            else -> return JudgeOutcome.Failure("无法判断这句话的语言")
        }
        if (lang == Lang.EN && en.isBlank()) en = enCandidate
        if (lang == Lang.ZH && zh.isBlank()) zh = zhCandidate
        if (en.isBlank() || zh.isBlank()) return JudgeOutcome.Failure("翻译结果不完整")
        return JudgeOutcome.Success(
            src = lang,
            en = cleanup(en),
            zh = cleanup(zh),
            latencyMs = System.currentTimeMillis() - started,
        )
    }

    private fun cleanup(text: String): String {
        var t = text.trim()
        while (t.length >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("“") && t.endsWith("”")))) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }

    private fun stripFence(raw: String): String {
        var t = raw.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            val end = t.lastIndexOf("```")
            if (end >= 0) t = t.substring(0, end)
            t = t.trim()
        }
        val first = t.indexOf('{')
        val last = t.lastIndexOf('}')
        if (first >= 0 && last > first) t = t.substring(first, last + 1)
        return t
    }

    private fun extractContent(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val choices = root.optJSONArray("choices") ?: return null
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return null
        return message.optString("content", "").takeIf { it.isNotBlank() }
    }

    private fun apiError(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun endpoint(base: String): String {
        val b = base.trim().trimEnd('/')
        return when {
            b.endsWith("/chat/completions") -> b
            b.endsWith("/v1") -> "$b/chat/completions"
            else -> "$b/chat/completions"
        }
    }

    private fun modelsEndpoint(base: String): String {
        val b = base.trim().trimEnd('/')
        val root = when {
            b.endsWith("/chat/completions") -> b.removeSuffix("/chat/completions")
            else -> b
        }
        return "$root/models"
    }

    private fun isDeepSeek(base: String) = base.contains("deepseek", ignoreCase = true)

    private fun systemPrompt(tone: TranslateTone): String = """
        你是专业的实时同声传译引擎。输入是同一段现场录音的两个 ASR 候选：
        - en_candidate：英文语音模型的识别结果
        - zh_candidate：中文语音模型的识别结果
        其中只有一个候选是真正说出的连贯话语，另一个是另一种语言模型对这段语音产生的音近乱码。

        工作步骤：
        1. 选出真正连贯、语义通顺的候选（可能是英文，也可能是中文）。
        2. 整理选中的文本：结合上下文修正同音字、错别字、漏字和错误断句，补全标点，但不要改写原意、不添加信息、不添加程度词或评价；不要改变时间、数量与否定关系。例如商务会议语境中「市场船队」应纠正为「市场团队」。
        3. 翻译成另一种语言。${tone.prompt}

        只输出严格 JSON：{"src":"en"|"zh"|"none","en":"...","zh":"..."}
        两个候选都完全不成句、或只是环境噪音时才返回 "none"。保留人名、数字与专有名词。不要输出解释或 Markdown。
    """.trimIndent()

    private fun userPrompt(enCandidate: String, zhCandidate: String, recent: List<Utterance>): String = buildString {
        val ctx = recent.takeLast(2).filter { it.en.isNotBlank() && it.zh.isNotBlank() }
        if (ctx.isNotEmpty()) {
            appendLine("最近的对话（仅供理解上下文，不要翻译或重复）：")
            ctx.forEach {
                appendLine("EN: ${it.en}")
                appendLine("中: ${it.zh}")
            }
            appendLine()
        }
        appendLine("en_candidate: ${enCandidate.ifBlank { "（空）" }}")
        append("zh_candidate: ${zhCandidate.ifBlank { "（空）" }}")
    }

    companion object {
        private const val TAG = "KaoTranslate"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
