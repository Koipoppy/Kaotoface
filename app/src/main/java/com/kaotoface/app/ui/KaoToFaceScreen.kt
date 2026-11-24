package com.kaotoface.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kaotoface.app.core.AppSettings
import com.kaotoface.app.core.EnginePhase
import com.kaotoface.app.core.Exporter
import com.kaotoface.app.core.Lang
import com.kaotoface.app.core.SessionEngine
import com.kaotoface.app.core.SettingsStore
import com.kaotoface.app.core.UiState
import com.kaotoface.app.core.DisplayMode
import com.kaotoface.app.ui.theme.Danger
import com.kaotoface.app.ui.theme.EnAccent
import com.kaotoface.app.ui.theme.Hairline
import com.kaotoface.app.ui.theme.Han
import com.kaotoface.app.ui.theme.Ink
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.TextHi
import com.kaotoface.app.ui.theme.TextLo
import com.kaotoface.app.ui.theme.TextMid
import com.kaotoface.app.ui.theme.ZhAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Sheet { Room, Settings, Export, Records }

@Composable
fun KaoToFaceScreen(
    engine: SessionEngine,
    settingsStore: SettingsStore,
    state: UiState,
    level: Float,
    settings: AppSettings,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val sync = rememberLyricSync(
        lines = state.lines,
        layoutKey = state.mode.name + "_" + configuration.screenWidthDp + "x" + configuration.screenHeightDp,
    )
    val contentAlpha = remember { Animatable(1f) }
    LaunchedEffect(state.mode, state.enFirst) {
        contentAlpha.snapTo(0.16f)
        contentAlpha.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }

    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var modeFlash by remember { mutableStateOf<DisplayMode?>(null) }
    var roomError by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var switchingMode by remember { mutableStateOf(false) }
    val modeVeil = remember { Animatable(0f) }

    LaunchedEffect(switchingMode, state.mode) {
        if (!switchingMode) return@LaunchedEffect
        delay(500)
        modeVeil.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        switchingMode = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            engine.startSession()
        } else {
            scope.launch { snackbar.showSnackbar("需要麦克风权限才能实时聆听") }
        }
    }

    fun requestMicPermission() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    fun onMicClick() {
        if (state.phase == EnginePhase.PREPARING || state.phase == EnginePhase.LISTENING) {
            engine.stopSession()
            return
        }
        val room = state.room
        if (room != null && !room.hosting && !state.allowGuestMic) {
            scope.launch { snackbar.showSnackbar("主持人还没有开放成员收音") }
            sheet = Sheet.Room
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) engine.startSession() else requestMicPermission()
    }

    fun startDemo() {
        if (state.phase == EnginePhase.PREPARING || state.phase == EnginePhase.LISTENING) {
            scope.launch { snackbar.showSnackbar("请先停止当前聆听") }
            return
        }
        engine.startDemo()
    }

    fun doExport(
        markdown: Boolean,
        share: Boolean,
        save: Boolean,
        lines: List<com.kaotoface.app.core.Utterance> = state.lines,
        roomCode: String? = state.room?.code,
    ) {
        if (lines.isEmpty()) {
            scope.launch { snackbar.showSnackbar("还没有可以导出的内容") }
            return
        }
        scope.launch {
            val file: File? = withContext(Dispatchers.IO) {
                runCatching { Exporter.writeExport(context, lines, roomCode, markdown) }.getOrNull()
            }
            if (file == null) {
                snackbar.showSnackbar("导出失败，请重试")
                return@launch
            }
            if (save) {
                val dest = withContext(Dispatchers.IO) { Exporter.saveToDownloads(context, file) }
                snackbar.showSnackbar(if (dest != null) "已保存到 $dest" else "保存失败", duration = SnackbarDuration.Long)
            }
            if (share) {
                val ok = Exporter.share(context, file, markdown)
                if (!ok) {
                    snackbar.showSnackbar("没有可用的分享应用")
                } else {
                    val result = snackbar.showSnackbar(
                        message = "已生成 ${lines.size} 句记录",
                        actionLabel = "保存到下载",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val dest = withContext(Dispatchers.IO) { Exporter.saveToDownloads(context, file) }
                        snackbar.showSnackbar(if (dest != null) "已保存到 $dest" else "保存失败")
                    }
                }
            }
        }
    }

    fun copyAll() {
        if (state.lines.isEmpty()) {
            scope.launch { snackbar.showSnackbar("还没有可以复制的内容") }
            return
        }
        val built = Exporter.build(state.lines, state.room?.code)
        Exporter.copyText(context, built.plain)
        scope.launch { snackbar.showSnackbar("已复制 ${state.lines.size} 句记录") }
    }

    // orientation follows display mode
    LaunchedEffect(state.mode) {
        activity?.requestedOrientation = when (state.mode) {
            DisplayMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }

    // keep the table awake while a session or room is live
    DisposableEffect(state.listening, state.room?.code) {
        view.keepScreenOn = state.listening || state.room != null
        onDispose { view.keepScreenOn = false }
    }

    // in-app messages
    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        if (sheet == Sheet.Room) roomError = err
        snackbar.showSnackbar(err, withDismissAction = true, duration = SnackbarDuration.Long)
        engine.consumeError()
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice, withDismissAction = true)
        engine.consumeNotice()
    }
    LaunchedEffect(modeFlash) {
        if (modeFlash != null) {
            delay(1700)
            modeFlash = null
        }
    }

    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = com.kaotoface.app.ui.theme.InkSoft,
            title = { Text("清空当前记录？", color = TextHi, fontFamily = Han, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "当前记录的 ${state.lines.size} 句对话将被永久删除。",
                    color = TextMid,
                    fontFamily = Han,
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    engine.clearLines()
                    scope.launch { snackbar.showSnackbar("记录已清空") }
                }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KaoGlyph(KaoIcon.Trash, Danger, Modifier.size(13.dp))
                        Text("清空", color = Danger, fontFamily = Han)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KaoGlyph(KaoIcon.Close, TextLo, Modifier.size(13.dp))
                        Text("取消", color = TextLo, fontFamily = Han)
                    }
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        val sessionActive = state.listening || (state.room?.listening == true)
        val topLangEnglish = state.enFirst
        val bottomLangEnglish = !state.enFirst
        val commonPanel: @Composable (
            Boolean,
            androidx.compose.foundation.lazy.LazyListState,
            Modifier,
            Boolean,
            PanelFade,
        ) -> Unit = { english, listState, modifier, reverse, fade ->
            LyricsPanel(
                english = english,
                lines = state.lines,
                listState = listState,
                focusedIndex = sync.focusedIndex,
                fontScale = settings.fontScale,
                listening = sessionActive,
                ttsEnabled = settings.tts,
                onSpeak = { u -> engine.speak(u, if (english) Lang.EN else Lang.ZH) },
                onSyncTo = { idx -> sync.tapLine(idx) },
                modifier = modifier,
                reverseLayout = reverse,
                fadeEdge = fade,
            )
        }

        val dock: @Composable (Modifier, Boolean) -> Unit = { dockModifier, vertical ->
            ControlDock(
                modifier = dockModifier,
                vertical = vertical,
                listening = state.listening,
                preparing = state.phase == EnginePhase.PREPARING,
                progress = state.prepareProgress,
                level = level,
                mode = state.mode,
                browsing = sync.browsing,
                room = state.room,
                onMic = { onMicClick() },
                onMode = {
                    if (!switchingMode) {
                        val next = DisplayMode.entries[(state.mode.ordinal + 1) % 3]
                        switchingMode = true
                        scope.launch {
                            modeVeil.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                            engine.setMode(next)
                            modeFlash = next
                        }
                    }
                },
                onFollow = { sync.followLatest(state.lines.lastIndex) },
                onSwap = { engine.swapSides() },
                onRoom = { sheet = Sheet.Room },
                onRecords = { sheet = Sheet.Records },
                onSettings = { sheet = Sheet.Settings },
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = contentAlpha.value },
        ) {
            when (state.mode) {
                DisplayMode.LANDSCAPE -> {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        commonPanel(
                            topLangEnglish, sync.en, Modifier.weight(1f),
                            false, PanelFade.End,
                        )
                        dock(Modifier.width(92.dp).fillMaxHeight(), true)
                        commonPanel(
                            bottomLangEnglish, sync.zh, Modifier.weight(1f),
                            false, PanelFade.Start,
                        )
                    }
                }
                DisplayMode.PORTRAIT -> {
                    commonPanel(
                        topLangEnglish, sync.en, Modifier.weight(1f).fillMaxWidth(),
                        true, PanelFade.Bottom,
                    )
                    dock(Modifier.fillMaxWidth().height(112.dp), false)
                    commonPanel(
                        bottomLangEnglish, sync.zh, Modifier.weight(1f).fillMaxWidth(),
                        false, PanelFade.Top,
                    )
                }
                DisplayMode.FACE_TO_FACE -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .graphicsLayer { rotationZ = 180f },
                    ) {
                        commonPanel(
                            topLangEnglish, sync.en, Modifier.fillMaxSize(),
                            false, PanelFade.Top,
                        )
                    }
                    dock(Modifier.fillMaxWidth().height(112.dp), false)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        commonPanel(
                            bottomLangEnglish, sync.zh, Modifier.fillMaxSize(),
                            false, PanelFade.Top,
                        )
                    }
                }
            }
        }

        // mode feedback
        val flashLandscape = state.mode == DisplayMode.LANDSCAPE
        AnimatedVisibility(
            visible = modeFlash != null,
            modifier = Modifier
                .align(if (flashLandscape) Alignment.TopStart else Alignment.TopCenter)
                .padding(
                    top = if (flashLandscape) 18.dp else 64.dp,
                    start = if (flashLandscape) 16.dp else 0.dp,
                ),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val m = modeFlash ?: state.mode
            Row(
                Modifier
                    .shadow(
                        14.dp,
                        RoundedCornerShape(15.dp),
                        clip = false,
                        ambientColor = Color(0x14000000),
                        spotColor = Color(0x14000000),
                    )
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xF7FFFFFF))
                    .border(1.dp, Hairline, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ModeGlyph(m, EnAccent, Modifier.size(16.dp))
                Column {
                    Text(m.label, color = TextHi, fontFamily = Han, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(m.hint, color = TextLo, fontFamily = Han, fontSize = 8.5.sp)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF171717),
                contentColor = Color.White,
                actionColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (state.phase == EnginePhase.PREPARING) {
            PrepareOverlay(
                state = state,
                onCancel = { engine.stopSession() },
            )
        }

        if (modeVeil.value > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = modeVeil.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            )
        }
    }

    when (sheet) {
        Sheet.Records -> {
            RecordsSheet(
                records = state.records,
                onDismiss = { sheet = null },
                onShare = { record, markdown -> doExport(markdown, share = true, save = false, lines = record.lines, roomCode = null) },
                onCopy = { record ->
                    val built = Exporter.build(record.lines, null)
                    Exporter.copyText(context, built.plain)
                    scope.launch { snackbar.showSnackbar("已复制 ${record.lines.size} 句记录") }
                },
                onSave = { record ->
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            runCatching { Exporter.writeExport(context, record.lines, null, true) }.getOrNull()
                        }
                        val dest = file?.let { withContext(Dispatchers.IO) { Exporter.saveToDownloads(context, it) } }
                        snackbar.showSnackbar(if (dest != null) "已保存到 $dest" else "保存失败", duration = SnackbarDuration.Long)
                    }
                },
            )
        }
        Sheet.Room -> {
            RoomSheet(
                room = state.room,
                nickname = settings.nickname,
                onDismiss = { sheet = null; roomError = null },
                onCreate = { roomError = null; engine.createRoom() },
                onJoin = { code, ip -> roomError = null; engine.joinRoom(code, ip) },
                onLeave = { engine.leaveRoom() },
                onAllowGuestMic = { engine.setAllowGuestMic(it) },
                error = roomError,
            )
        }
        Sheet.Settings -> SettingsSheet(
            settings = settings,
            settingsStore = settingsStore,
            engine = engine,
            lineCount = state.lines.size,
            onDismiss = { sheet = null },
            onExport = { sheet = Sheet.Export },
            onDemo = { sheet = null; startDemo() },
        )
        Sheet.Export -> ExportSheet(
            count = state.lines.size,
            roomCode = state.room?.code,
            onDismiss = { sheet = null },
            onShareMarkdown = { sheet = null; doExport(markdown = true, share = true, save = false) },
            onShareText = { sheet = null; doExport(markdown = false, share = true, save = false) },
            onCopy = { sheet = null; copyAll() },
            onSave = { sheet = null; doExport(markdown = true, share = false, save = true) },
            onClear = { sheet = null; confirmClear = true },
        )
        null -> Unit
    }
}

@Composable
private fun PrepareOverlay(state: UiState, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 34.dp)
                .shadow(
                    24.dp,
                    RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = Color(0x1A000000),
                    spotColor = Color(0x1A000000),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFAFFFFFF))
                .border(1.dp, Hairline, RoundedCornerShape(24.dp))
                .padding(horizontal = 26.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.prepareProgress.coerceIn(0f, 1f) },
                    color = TextHi,
                    trackColor = Color(0x1AFFFFFF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(58.dp),
                )
                Text(
                    "${(state.prepareProgress * 100).toInt()}",
                    color = TextHi,
                    fontFamily = Jost,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                state.prepareLabel.ifBlank { "正在准备语音引擎" },
                color = TextHi,
                fontFamily = Han,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.prepareProgress < 0.70f) {
                    "首次启动会在本机解压约 130MB 的离线中英语音模型，仅需一次。"
                } else {
                    "模型加载完成，马上就好。"
                },
                color = TextLo,
                fontFamily = Han,
                fontSize = 9.5.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    KaoGlyph(KaoIcon.Close, TextLo, Modifier.size(13.dp))
                    Text("取消", color = TextLo, fontFamily = Han, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
