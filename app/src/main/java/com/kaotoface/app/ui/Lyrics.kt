package com.kaotoface.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaotoface.app.core.DisplayMode
import com.kaotoface.app.core.Lang
import com.kaotoface.app.core.Utterance
import com.kaotoface.app.ui.theme.EnAccent
import com.kaotoface.app.ui.theme.EnWash
import com.kaotoface.app.ui.theme.Hairline
import com.kaotoface.app.ui.theme.Han
import com.kaotoface.app.ui.theme.Ink
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.TextHi
import com.kaotoface.app.ui.theme.TextLo
import com.kaotoface.app.ui.theme.TextMid
import com.kaotoface.app.ui.theme.ZhAccent
import com.kaotoface.app.ui.theme.ZhWash
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// -------------------------------------------------------------------------- sync

class LyricSync internal constructor(
    val en: LazyListState,
    val zh: LazyListState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val autoFollowState: androidx.compose.runtime.MutableState<Boolean>,
    private val browsingState: androidx.compose.runtime.MutableState<Boolean>,
    private val focusState: androidx.compose.runtime.MutableState<Int>,
) {
    private var settleJob: Job? = null

    /** Positive in landscape: the active line sits above the fixed control bar. */
    var focusBias: Float = 0f

    val autoFollow: Boolean get() = autoFollowState.value
    val browsing: Boolean get() = browsingState.value

    /** The one authoritative current line. Both panels highlight this index. */
    val focusedIndex: Int get() = focusState.value

    fun tapLine(index: Int) {
        autoFollowState.value = false
        browsingState.value = true
        settleOn(index)
    }

    fun followLatest(lastIndex: Int) {
        if (lastIndex < 0) return
        autoFollowState.value = true
        browsingState.value = false
        settleOn(lastIndex)
    }

    /**
     * Highlight now and scroll at the same time: the row grows while it travels,
     * both animations use the same duration, so the gesture feels immediate.
     */
    fun settleOn(index: Int) {
        focusState.value = index
        settleJob?.cancel()
        settleJob = scope.launch {
            coroutineScope {
                launch { en.centerOn(index, correct = false, bias = focusBias) }
                launch { zh.centerOn(index, correct = false, bias = focusBias) }
            }
        }
    }

    /** Re-centre the current line without changing which line is highlighted. */
    fun recenter(index: Int) {
        if (index < 0) return
        settleJob?.cancel()
        settleJob = scope.launch {
            coroutineScope {
                launch { en.centerOn(index, correct = true, bias = focusBias) }
                launch { zh.centerOn(index, correct = true, bias = focusBias) }
            }
        }
    }
}

/**
 * Scrolls so the requested row sits exactly on the viewport's centre line.
 * Estimates are corrected from the real measured row geometry, which matters
 * because the active row changes size and Chinese/English lines wrap differently.
 */
private suspend fun LazyListState.centerOn(index: Int, correct: Boolean, bias: Float) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        animateScrollToItem(index)
        delay(40)
    }
    val info = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f - bias
    val delta = (info.offset + info.size / 2f) - viewportCenter
    if (abs(delta) > 0.5f) {
        // Matches the row's scale tween: both start and finish together.
        animateScrollBy(delta, tween(340, easing = FastOutSlowInEasing))
    }
    if (correct) {
        delay(60)
        val settled = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f - bias
        val correction = (settled.offset + settled.size / 2f) - center
        if (abs(correction) > 0.5f) {
            animateScrollBy(correction, tween(160, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
fun rememberLyricSync(lines: List<Utterance>, layoutKey: Any? = null, focusBias: Float = 0f): LyricSync {
    val enState = rememberLazyListState()
    val zhState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val autoFollow = remember { mutableStateOf(true) }
    val browsing = remember { mutableStateOf(false) }
    val focus = remember { mutableStateOf(-1) }
    val sync = remember { LyricSync(enState, zhState, scope, autoFollow, browsing, focus) }
    sync.focusBias = focusBias
    val enDragged by enState.interactionSource.collectIsDraggedAsState()
    val zhDragged by zhState.interactionSource.collectIsDraggedAsState()
    var enWasDragged by remember { mutableStateOf(false) }
    var zhWasDragged by remember { mutableStateOf(false) }

    LaunchedEffect(lines.size, layoutKey) {
        if (lines.isEmpty()) {
            focus.value = -1
        } else if (autoFollow.value) {
            // New lines slide into the centre first; the highlight follows.
            sync.settleOn(lines.lastIndex)
        } else {
            // Rotation or a display-mode change re-lays-out the panels; keep the browsed line centred.
            val idx = focus.value
            if (idx < 0) sync.settleOn(lines.lastIndex)
            else sync.recenter(idx.coerceIn(0, lines.lastIndex))
        }
    }

    LaunchedEffect(enDragged) {
        if (enDragged) {
            enWasDragged = true
            autoFollow.value = false
            browsing.value = true
        } else if (enWasDragged) {
            enWasDragged = false
            snapshotFlow { enState.isScrollInProgress }.first { !it }
            val idx = enState.centerIndex(sync.focusBias)
            if (idx >= 0) {
                focus.value = idx
                zhState.centerOn(idx, correct = true, bias = sync.focusBias)
            }
        }
    }

    LaunchedEffect(zhDragged) {
        if (zhDragged) {
            zhWasDragged = true
            autoFollow.value = false
            browsing.value = true
        } else if (zhWasDragged) {
            zhWasDragged = false
            snapshotFlow { zhState.isScrollInProgress }.first { !it }
            val idx = zhState.centerIndex(sync.focusBias)
            if (idx >= 0) {
                focus.value = idx
                enState.centerOn(idx, correct = true, bias = sync.focusBias)
            }
        }
    }

    return sync
}

fun LazyListState.centerIndex(bias: Float = 0f): Int {
    val info = layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return -1
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f - bias
    return info.visibleItemsInfo.minByOrNull { item ->
        abs((item.offset + item.size / 2f) - center)
    }?.index ?: -1
}

// ---------------------------------------------------------------------- ambient

@Composable
fun AmbientBackground(
    mode: DisplayMode,
    enFirst: Boolean,
    level: Float,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ambientDrift",
    )
    val landscape = mode == DisplayMode.LANDSCAPE
    val targetEnX = if (landscape) (if (enFirst) 0.24f else 0.76f) else 0.40f
    val targetEnY = if (landscape) 0.52f else (if (enFirst) 0.28f else 0.72f)
    val targetZhX = if (landscape) (if (enFirst) 0.76f else 0.24f) else 0.60f
    val targetZhY = if (landscape) 0.52f else (if (enFirst) 0.72f else 0.28f)
    val enX by animateFloatAsState(targetEnX, tween(900, easing = FastOutSlowInEasing), label = "enX")
    val enY by animateFloatAsState(targetEnY, tween(900, easing = FastOutSlowInEasing), label = "enY")
    val zhX by animateFloatAsState(targetZhX, tween(900, easing = FastOutSlowInEasing), label = "zhX")
    val zhY by animateFloatAsState(targetZhY, tween(900, easing = FastOutSlowInEasing), label = "zhY")
    val breathe = if (listening) 0.78f + level * 0.22f else 0.72f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val longSide = max(w, h)
        drawRect(
            Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFF7F7F7), Color(0xFFFFFFFF)),
            ),
        )
        val wobbleX = (drift - 0.5f) * 0.045f
        val wobbleY = (drift - 0.5f) * 0.035f
        val enPos = Offset(w * (enX + wobbleX), h * (enY + wobbleY))
        val zhPos = Offset(w * (zhX - wobbleX), h * (zhY - wobbleY))
        val radius = longSide * 0.72f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(EnWash.copy(alpha = breathe), EnWash.copy(alpha = breathe * 0.45f), Color.Transparent),
                center = enPos,
                radius = radius,
            ),
            center = enPos,
            radius = radius,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ZhWash.copy(alpha = breathe * 0.92f), ZhWash.copy(alpha = breathe * 0.40f), Color.Transparent),
                center = zhPos,
                radius = radius * 0.94f,
            ),
            center = zhPos,
            radius = radius * 0.94f,
        )
        // keep the reading centre airy and bright
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0x66FFFFFF), Color(0x26FFFFFF), Color.Transparent),
                center = Offset(w / 2f, h / 2f),
                radius = longSide * 0.58f,
            ),
        )
        // fade the corners to paper white
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x55FFFFFF), Color(0xE0FFFFFF)),
                center = Offset(w / 2f, h / 2f),
                radius = longSide * 0.82f,
            ),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.60f), Color.Transparent, Color.White.copy(alpha = 0.60f)),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x0C000000), Color.Transparent),
                startY = 0f,
                endY = (longSide * 0.012f).coerceAtLeast(1f),
            ),
            size = Size(w, (longSide * 0.012f).coerceAtLeast(1f)),
        )
    }
}

// ---------------------------------------------------------------------- lyrics

enum class PanelFade { None, Top, Bottom, Start, End }

@Composable
fun LyricsPanel(
    english: Boolean,
    lines: List<Utterance>,
    pendingIds: Set<String> = emptySet(),
    liveId: String? = null,
    listState: LazyListState,
    focusedIndex: Int,
    fontScale: Float,
    listening: Boolean,
    ttsEnabled: Boolean,
    onSpeak: (Utterance) -> Unit,
    onSyncTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    fadeEdge: PanelFade = PanelFade.None,
    bias: Dp = 0.dp,
) {
    val accent = if (english) EnAccent else ZhAccent
    BoxWithConstraints(modifier.fillMaxSize()) {
        val centerPad = ((maxHeight / 2) - 34.dp).coerceAtLeast(20.dp)
        val topPad = (centerPad - bias).coerceAtLeast(0.dp)
        val bottomPad = centerPad + bias
        Box(Modifier.fillMaxSize()) {
            if (lines.isEmpty()) {
                EmptyState(
                    english = english,
                    accent = accent,
                    listening = listening,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    reverseLayout = reverseLayout,
                ) {
                    itemsIndexed(lines, key = { _, u -> u.id }) { index, u ->
                        val distance = if (focusedIndex < 0) 3 else abs(index - focusedIndex)
                        val isActive = index == focusedIndex
                        val isPending = u.id in pendingIds
                        val isLive = u.id == liveId
                        val text = u.text(if (english) Lang.EN else Lang.ZH)
                        LyricRow(
                            text = text,
                            english = english,
                            active = isActive,
                            distance = distance,
                            fontScale = fontScale,
                            accent = accent,
                            ttsHint = ttsEnabled && isActive && !isPending,
                            placeholder = isPending && text.isBlank(),
                            provisional = isPending,
                            live = isLive,
                            maxLines = if (isLive) 1 else 5,
                            modifier = Modifier.animateItem(),
                            onClick = {
                                if (isActive && ttsEnabled && !isPending) onSpeak(u)
                                else onSyncTo(index)
                            },
                        )
                    }
                }
            }
            // Fade the text into the white centre bar instead of cutting it.
            when (fadeEdge) {
                PanelFade.Top -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Ink, Ink.copy(alpha = 0.82f), Color.Transparent),
                            ),
                        ),
                )
                PanelFade.Bottom -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Ink.copy(alpha = 0.82f), Ink),
                            ),
                        ),
                )
                PanelFade.Start -> Box(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Ink, Ink.copy(alpha = 0.82f), Color.Transparent),
                            ),
                        ),
                )
                PanelFade.End -> Box(
                    Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Ink.copy(alpha = 0.82f), Ink),
                            ),
                        ),
                )
                PanelFade.None -> Unit
            }
        }
    }
}

@Composable
private fun LyricRow(
    text: String,
    english: Boolean,
    active: Boolean,
    distance: Int,
    fontScale: Float,
    accent: Color,
    ttsHint: Boolean,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    provisional: Boolean = false,
    live: Boolean = false,
    maxLines: Int = 5,
    onClick: () -> Unit,
) {
    val amount by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        label = "activeAmount",
    )
    // Every row is laid out at the active size. The visual scale changes, not the
    // text metrics, so highlighting can never re-wrap a sentence or move its row.
    val layoutSp = if (english) 26.5f else 25.5f
    val minScale = if (english) 0.66f else 0.725f
    val scale = minScale + (1f - minScale) * amount
    val baseAlpha = when (distance) {
        0 -> 0.94f
        1 -> 0.42f
        2 -> 0.24f
        3 -> 0.13f
        else -> 0.07f
    }
    val alpha = (baseAlpha * (1f - amount) + amount).coerceIn(0f, 1f)
    val annotated = bilingualText(text)
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    val textStyle = TextStyle(
        fontSize = (layoutSp * fontScale).sp,
        lineHeight = (layoutSp * 1.34f * fontScale).sp,
        letterSpacing = if (english) 0.2.sp else 0.5.sp,
    )
    val strokePx = with(LocalDensity.current) { (0.7.dp.toPx() * fontScale) }
    val textColor = TextHi.copy(alpha = alpha * if (provisional) 0.62f else 1f)
    val placeholderHeight = (layoutSp * 1.34f * fontScale).dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 26.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val value: AnnotatedString? = if (placeholder) null else annotated
        if (live && value != null) {
            // Partial Vosk text changes every few chunks; animating each update
            // would flicker, so the live source line is drawn directly.
            LyricContent(value, textColor, amount, strokePx, maxLines, textStyle)
        } else {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (
                        fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 6 }
                        ).togetherWith(fadeOut(tween(140)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "lyricContent",
            ) { content ->
                if (content == null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(placeholderHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        BufferDots(color = TextMid.copy(alpha = 0.90f))
                    }
                } else {
                    LyricContent(content, textColor, amount, strokePx, maxLines, textStyle)
                }
            }
        }
    }
}

@Composable
private fun LyricContent(
    text: AnnotatedString,
    color: Color,
    amount: Float,
    strokePx: Float,
    maxLines: Int,
    textStyle: TextStyle,
) {
    Box(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = textStyle,
        )
        if (amount > 0.01f) {
            // A stroked twin adds weight without changing text measurement.
            Text(
                text = text,
                modifier = Modifier.matchParentSize(),
                color = color.copy(alpha = (color.alpha * amount).coerceIn(0f, 1f)),
                textAlign = TextAlign.Center,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                style = textStyle.copy(drawStyle = Stroke(width = strokePx)),
            )
        }
    }
}

/** The opposite-language side of a pending line: three quiet breathing dots. */
@Composable
private fun BufferDots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "buffer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "bufferPhase",
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val wave = 0.5f + 0.5f * kotlin.math.sin(
                (phase * 2f * kotlin.math.PI.toFloat()) - index * 0.85f,
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { alpha = 0.28f + 0.62f * wave }
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun EdgeFades(top: Boolean, bottom: Boolean, strongTop: Boolean, strongBottom: Boolean) {
    Box(Modifier.fillMaxSize()) {
        if (top) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (strongTop) 96.dp else 64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Ink.copy(alpha = 0.92f), Ink.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )
        }
        if (bottom) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (strongBottom) 96.dp else 64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Ink.copy(alpha = 0.55f), Ink.copy(alpha = 0.92f)),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun ScrollHint(state: LazyListState, accent: Color, modifier: Modifier = Modifier) {
    val total = state.layoutInfo.totalItemsCount
    if (total <= 2) return
    val first = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return
    val approxItem = first.size.coerceAtLeast(1)
    val scrolled = (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset.toFloat() / approxItem) / (total - 1).toFloat()
    val frac = scrolled.coerceIn(0f, 1f)
    Canvas(
        modifier
            .width(6.dp)
            .fillMaxHeight()
            .padding(vertical = 54.dp, horizontal = 1.5.dp),
    ) {
        val thumbH = (size.height * 0.16f).coerceAtLeast(20.dp.toPx())
        val y = frac * (size.height - thumbH)
        drawRoundRect(
            color = accent.copy(alpha = 0.10f),
            topLeft = Offset(0f, y),
            size = Size(size.width, thumbH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width),
        )
    }
}

// ------------------------------------------------------------------- empty state

@Composable
private fun EmptyState(
    english: Boolean,
    accent: Color,
    listening: Boolean,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 30.dp),
        ) {
            if (listening) {
                LiveWave(accent)
                Spacer(Modifier.height(18.dp))
                Text(
                    if (english) "Listening…" else "正在聆听…",
                    color = TextMid,
                    fontFamily = if (english) Jost else Han,
                    fontSize = if (english) 21.sp else 19.sp,
                    fontWeight = if (english) FontWeight.Light else FontWeight.Normal,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (english) "Speak now — translations will appear here." else "请开始说话，译文会在这里出现。",
                    color = TextLo,
                    fontFamily = if (english) Jost else Han,
                    fontSize = 10.5.sp,
                )
            } else {
                Text(
                    text = if (english) "Ready when you are." else "让每一句话，都被听懂。",
                    color = TextMid.copy(alpha = 0.9f),
                    fontFamily = if (english) Jost else Han,
                    fontWeight = if (english) FontWeight.Light else FontWeight.Normal,
                    fontSize = if (english) 23.sp else 20.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = if (english) 0.2.sp else 0.8.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (english) "Tap the mic to start listening" else "轻触中间的按钮，开始实时翻译",
                    color = TextLo.copy(alpha = 0.9f),
                    fontFamily = Han,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                if (!english) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "演示对话可在设置中播放",
                        color = TextLo.copy(alpha = 0.62f),
                        fontFamily = Han,
                        fontSize = 9.5.sp,
                    )
                }

            }
        }
    }
}

@Composable
private fun LiveWave(accent: Color) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "wavePhase",
    )
    Canvas(Modifier.width(64.dp).height(22.dp)) {
        val bars = 7
        val gap = size.width / bars
        for (i in 0 until bars) {
            val h = (size.height * (0.28f + 0.66f * (0.5f + 0.5f * kotlin.math.sin(phase + i * 0.9f))))
            drawRoundRect(
                color = accent.copy(alpha = 0.35f + 0.4f * (h / size.height)),
                topLeft = Offset(i * gap + gap * 0.25f, (size.height - h) / 2f),
                size = Size(gap * 0.5f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap * 0.25f),
            )
        }
    }
}
