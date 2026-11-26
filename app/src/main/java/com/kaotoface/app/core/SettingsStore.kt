package com.kaotoface.app.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kaotoface_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    private fun read(): AppSettings {
        val def = AppSettings()
        return AppSettings(
            apiKey = prefs.getString(KEY_API, def.apiKey) ?: def.apiKey,
            baseUrl = prefs.getString(KEY_BASE, def.baseUrl) ?: def.baseUrl,
            model = prefs.getString(KEY_MODEL, def.model) ?: def.model,
            tone = TranslateTone.fromKey(prefs.getString(KEY_TONE, def.tone.key)),
            fontScale = prefs.getFloat(KEY_FONT, def.fontScale).coerceIn(0.82f, 1.25f),
            tts = prefs.getBoolean(KEY_TTS, def.tts),
            nickname = prefs.getString(KEY_NAME, null) ?: AppSettings.defaultNickname,
            mode = DisplayMode.fromKey(prefs.getString(KEY_MODE, def.mode.key)),
            enFirst = prefs.getBoolean(KEY_EN_FIRST, def.enFirst),
            langA = Languages.of(prefs.getString(KEY_LANG_A, def.langA) ?: def.langA).code,
            langB = Languages.of(prefs.getString(KEY_LANG_B, def.langB) ?: def.langB).code,
        )
    }

    private fun edit(block: (android.content.SharedPreferences.Editor) -> Unit) {
        val e = prefs.edit()
        block(e)
        e.apply()
    }

    fun setApiKey(value: String) {
        edit { it.putString(KEY_API, value.trim()) }
        _state.update { it.copy(apiKey = value.trim()) }
    }

    fun setBaseUrl(value: String) {
        val v = value.trim().ifBlank { AppSettings.DEFAULT_BASE_URL }.trimEnd('/')
        edit { it.putString(KEY_BASE, v) }
        _state.update { it.copy(baseUrl = v) }
    }

    fun setModel(value: String) {
        val v = value.trim().ifBlank { AppSettings.DEFAULT_MODEL }
        edit { it.putString(KEY_MODEL, v) }
        _state.update { it.copy(model = v) }
    }

    fun setTone(value: TranslateTone) {
        edit { it.putString(KEY_TONE, value.key) }
        _state.update { it.copy(tone = value) }
    }

    fun setFontScale(value: Float) {
        val v = value.coerceIn(0.82f, 1.25f)
        edit { it.putFloat(KEY_FONT, v) }
        _state.update { it.copy(fontScale = v) }
    }

    fun setTts(value: Boolean) {
        edit { it.putBoolean(KEY_TTS, value) }
        _state.update { it.copy(tts = value) }
    }

    fun setNickname(value: String) {
        val v = value.trim().ifBlank { "我的设备" }.take(16)
        edit { it.putString(KEY_NAME, v) }
        _state.update { it.copy(nickname = v) }
    }

    fun setMode(value: DisplayMode) {
        edit { it.putString(KEY_MODE, value.key) }
        _state.update { it.copy(mode = value) }
    }

    fun setEnFirst(value: Boolean) {
        edit { it.putBoolean(KEY_EN_FIRST, value) }
        _state.update { it.copy(enFirst = value) }
    }

    fun setLangA(value: String) {
        val v = Languages.of(value).code
        val current = _state.value
        // Picking the language already used on the other side swaps the pair
        // instead of leaving both sides in the same language.
        val other = if (v == current.langB) current.langA else current.langB
        edit {
            it.putString(KEY_LANG_A, v)
            it.putString(KEY_LANG_B, other)
        }
        _state.update { it.copy(langA = v, langB = other) }
    }

    fun setLangB(value: String) {
        val v = Languages.of(value).code
        val current = _state.value
        val other = if (v == current.langA) current.langB else current.langA
        edit {
            it.putString(KEY_LANG_B, v)
            it.putString(KEY_LANG_A, other)
        }
        _state.update { it.copy(langB = v, langA = other) }
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_BASE = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_TONE = "tone"
        const val KEY_FONT = "font_scale"
        const val KEY_TTS = "tts"
        const val KEY_NAME = "nickname"
        const val KEY_MODE = "mode"
        const val KEY_EN_FIRST = "en_first"
        const val KEY_LANG_A = "lang_a"
        const val KEY_LANG_B = "lang_b"
    }
}
