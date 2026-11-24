package com.kaotoface.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaotoface.app.R
import com.kaotoface.app.ui.theme.EnAccent
import com.kaotoface.app.ui.theme.EnAccentDeep
import com.kaotoface.app.ui.theme.Glass
import com.kaotoface.app.ui.theme.Han
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.LiveRed
import com.kaotoface.app.ui.theme.TextHi
import com.kaotoface.app.ui.theme.TextLo
import com.kaotoface.app.ui.theme.TextMid
import com.kaotoface.app.ui.theme.ZhAccent
import com.kaotoface.app.ui.theme.ZhAccentDeep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------------- typography

private fun isCjk(c: Char): Boolean {
    val code = c.code
    return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
        (code in 0xF900..0xFAFF) || (code in 0x3000..0x303F) ||
        (code in 0xFF00..0xFFEF) || c == '·' || c == '—' || c == '…'
}

/**
 * Splits every lyric into script runs, so English is always Jost and Chinese is
 * always 思源黑体 — even inside a mixed sentence.
 */
@Composable
fun bilingualText(text: String): AnnotatedString = remember(text) {
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val cjk = isCjk(text[i])
            var j = i + 1
            while (j < text.length) {
                val c = text[j]
                if (isCjk(c) != cjk) {
                    // keep spaces and neutral punctuation attached to the current run
                    if (c == ' ' || c == '\n') {
                        j++
                        continue
                    }
                    break
                }
                j++
            }
            val chunk = text.substring(i, j)
            withStyle(
                SpanStyle(
                    fontFamily = if (cjk) Han else Jost,
                    fontWeight = FontWeight.Normal,
                ),
            ) { append(chunk) }
            i = j
        }
    }
}

// --------------------------------------------------------------------------- controls

@Composable
fun GlassIconButton(
    icon: KaoIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 18.dp,
    tint: Color = TextHi.copy(alpha = 0.82f),
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(90),
        label = "iconPress",
    )
    val background by animateColorAsState(
        targetValue = if (pressed) Color(0x14111827) else Glass,
        animationSpec = tween(130),
        label = "glassPress",
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(background)
            .border(1.dp, Color(0x12111827), CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        KaoGlyph(
            icon = icon,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun GlassPill(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .height(32.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xE8FFFFFF))
        .border(1.dp, Color(0x12111827), RoundedCornerShape(16.dp))
    Row(
        modifier = if (onClick != null) {
            base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
        } else base,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Spacer(Modifier.width(11.dp))
        content()
        Spacer(Modifier.width(11.dp))
    }
}

@Composable
fun StatusDot(
    color: Color,
    active: Boolean,
    size: Dp = 6.dp,
) {
    val transition = rememberInfiniteTransition(label = "dot")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "dotPulse",
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = if (active) pulse else 0.5f)),
    )
}

@Composable
fun LanguageTag(
    english: Boolean,
    accent: Color,
    lines: Int,
    listening: Boolean,
    translating: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (listening) LiveRed else TextLo, active = listening, size = 5.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (english) "ENGLISH" else "中文",
            color = accent.copy(alpha = 0.92f),
            fontFamily = if (english) Jost else Han,
            fontWeight = if (english) FontWeight.Medium else FontWeight.Bold,
            fontSize = if (english) 10.5.sp else 11.5.sp,
            letterSpacing = if (english) 2.4.sp else 1.2.sp,
        )
        if (lines > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$lines",
                color = TextLo.copy(alpha = 0.75f),
                fontFamily = Jost,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        if (translating) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val transition = rememberInfiniteTransition(label = "dots")
                repeat(3) { i ->
                    val a by transition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(620, delayMillis = i * 140),
                            RepeatMode.Reverse,
                        ),
                        label = "tDot$i",
                    )
                    Box(
                        Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = a * 0.8f)),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "翻译中",
                    color = TextLo.copy(alpha = 0.9f),
                    fontFamily = Han,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
fun GlassButtonLabel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = EnAccent,
    enabled: Boolean = true,
    icon: KaoIcon? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) {
            KaoGlyph(
                icon = icon,
                tint = if (enabled) accent else accent.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = if (enabled) accent else accent.copy(alpha = 0.35f),
            fontFamily = Han,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ------------------------------------------------------------------------------ mic

@Composable
fun MicButton(
    listening: Boolean,
    preparing: Boolean,
    progress: Float,
    level: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(90), label = "micPress")
    val background by animateColorAsState(
        targetValue = if (listening) LiveRed else Color(0xFF080808),
        animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "micBackground",
    )
    val orbAlpha by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = tween(560),
        label = "orbAlpha",
    )
    val orbScale by animateFloatAsState(
        targetValue = if (listening) 1f else 0.70f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "orbScale",
    )
    val earAlpha by animateFloatAsState(
        targetValue = if (listening) 0f else if (preparing) 0.55f else 1f,
        animationSpec = tween(560),
        label = "earAlpha",
    )
    val earScale by animateFloatAsState(
        targetValue = if (listening) 0.72f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "earScale",
    )
    val transition = rememberInfiniteTransition(label = "caustics")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(5500, easing = androidx.compose.animation.core.LinearEasing),
            RepeatMode.Restart,
        ),
        label = "causticPhase",
    )
    val drift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "causticDrift",
    )
    Box(
        modifier = modifier.size(size * 1.18f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape)
                .background(background)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !preparing,
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics {
                    contentDescription = when {
                        preparing -> "正在准备语音引擎"
                        listening -> "停止聆听"
                        else -> "开始聆听"
                    }
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = preparing,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(180)),
            ) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(size - 12.dp),
                    color = Color.White,
                    trackColor = Color(0xFFFFE8E2).copy(alpha = 0.22f),
                    strokeWidth = 3.dp,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_ear),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(size * 0.48f)
                    .graphicsLayer {
                        alpha = earAlpha
                        scaleX = earScale
                        scaleY = earScale
                    },
            )
            if (orbAlpha > 0.001f) {
                CausticFill(
                    phase = phase,
                    drift = drift,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = orbAlpha
                            scaleX = orbScale
                            scaleY = orbScale
                        },
                )
            }
        }
    }
}

/**
 * Flat red orb with slowly drifting, diffuse glass-caustic light inside.
 * No glow, no shadow and no specular cap: all movement stays inside the circle.
 */
@Composable
private fun CausticFill(
    phase: androidx.compose.runtime.State<Float>,
    drift: androidx.compose.runtime.State<Float>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = phase.value
        val d = drift.value

        // Base colour: a flat, saturated red. The glass edge is the part of this
        // base that the blurred dark copies do not cover.
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF05245), Color(0xFFC92115), Color(0xFF9E120A)),
            ),
            radius = w * 0.5f,
            center = Offset(w * 0.5f, h * 0.5f),
        )

        // The tutorial's method, applied to the circle: copies of the shape,
        // black at 20 %, slid down and heavily blurred. Their overlaps deepen
        // the lower centre while the upper edge keeps a bright glass rim.
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                maskFilter = android.graphics.BlurMaskFilter(
                    w * 0.065f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL,
                )
            }
            val slideX = w * 0.095f * sin(p * 0.75f)
            val slideY = h * 0.070f * sin(p * 0.95f + 0.8f)
            repeat(6) { index ->
                val t = p * 0.95f + index * 1.33f
                val cx = w * (0.50f + 0.130f * cos(t * 0.85f + index * 0.6f)) + slideX
                val cy = h * (0.55f + index * 0.033f + 0.075f * sin(t * 0.95f + index * 0.9f)) + slideY
                val radius = w * (0.30f + 0.065f * sin(t * 1.10f + index))
                paint.color = android.graphics.Color.argb(51, 0, 0, 0) // black, 20 %
                canvas.nativeCanvas.drawCircle(cx, cy, radius, paint)
            }
            // One smaller, darker block settles the weight at the bottom.
            paint.color = android.graphics.Color.argb(64, 0, 0, 0)
            canvas.nativeCanvas.drawCircle(
                w * (0.5f + 0.055f * sin(p * 0.72f)),
                h * (0.74f + 0.035f * cos(p * 0.64f)),
                w * 0.24f,
                paint,
            )
        }

        // Broad colour fields, not pin-points: they lift the upper glass and
        // warm the lower edge, both sliding slowly with the dark copies.
        val topField = Offset(
            w * (0.42f + 0.040f * cos(p * 0.62f + 0.4f)),
            h * (0.20f + 0.030f * sin(p * 0.54f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x40FF8A78), Color(0x14FF6F60), Color.Transparent),
                center = topField,
                radius = w * 0.66f,
            ),
            radius = w * 0.66f,
            center = topField,
        )
        val lowerField = Offset(
            w * (0.5f + 0.030f * sin(p * 0.58f)),
            h * (0.90f + 0.015f * cos(p * 0.67f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x38FF9A70).copy(alpha = 0.18f + 0.05f * d),
                    Color(0x0DFF7A45),
                    Color.Transparent,
                ),
                center = lowerField,
                radius = w * 0.52f,
            ),
            radius = w * 0.52f,
            center = lowerField,
        )
    }
}

// -------------------------------------------------------------------------- misc

@Composable
fun ShortDivider(color: Color = Color(0x12111827)) {
    Box(
        Modifier
            .width(28.dp)
            .height(1.dp)
            .background(color),
    )
}

fun accentFor(english: Boolean): Color = if (english) EnAccent else ZhAccent

fun deepAccentFor(english: Boolean): Color = if (english) EnAccentDeep else ZhAccentDeep

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = TextLo,
        fontFamily = Han,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
}

@Composable
fun KaoSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontFamily = Han, fontSize = 14.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TextLo, fontFamily = Han, fontSize = 10.5.sp)
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EnAccentDeep,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0x18111827),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

fun polar(cx: Float, cy: Float, r: Float, angleDeg: Float): Offset {
    val rad = angleDeg / 180f * PI.toFloat()
    return Offset(cx + cos(rad) * r, cy + sin(rad) * r)
}
