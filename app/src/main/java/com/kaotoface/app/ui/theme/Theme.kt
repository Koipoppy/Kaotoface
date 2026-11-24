package com.kaotoface.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kaotoface.app.R

/*
 * Strict monochrome system. White paper, ink text, neutral glass.
 * Red is reserved exclusively for the live/running state of the microphone.
 */
val Ink = Color(0xFFFFFFFF)
val InkSoft = Color(0xFFF4F4F4)
val Paper = Color(0xFFFFFFFF)
val Glass = Color(0x0A000000)
val GlassStrong = Color(0x16000000)
val LineSoft = Color(0x10000000)
val Hairline = Color(0x14000000)
val TextHi = Color(0xFF0A0A0A)
val TextMid = Color(0xFF5C5C5C)
val TextLo = Color(0xFF9A9A9A)
val EnAccent = TextHi
val EnAccentDeep = Color(0xFF1C1C1C)
val ZhAccent = TextMid
val ZhAccentDeep = Color(0xFF3A3A3A)
val EnWash = Color(0xFFF0F0F0)
val ZhWash = Color(0xFFF6F6F6)
val Success = TextMid
val Danger = TextHi
val LiveRed = Color(0xFFD92D20)
val LiveRedDeep = Color(0xFFB42318)

/** Jost — the English voice. Variable weight axis gives real weights. */
@OptIn(ExperimentalTextApi::class)
val Jost = FontFamily(
    Font(R.font.jost, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.jost, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.jost, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.jost, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.jost, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** 思源黑体 (Noto Sans CJK SC, the same typeface family). */
val Han = FontFamily(
    Font(R.font.source_han_sans_regular, FontWeight.Normal),
    Font(R.font.source_han_sans_bold, FontWeight.Bold),
)

/** Phosphor Fill — soft, rounded, filled icons for every control. */
val PhosphorFill = FontFamily(
    Font(R.font.phosphor_fill, FontWeight.Normal),
)

private val KaoColors = lightColorScheme(
    primary = TextHi,
    onPrimary = Color.White,
    secondary = TextMid,
    onSecondary = Color.White,
    background = Ink,
    onBackground = TextHi,
    surface = Paper,
    onSurface = TextHi,
    surfaceVariant = InkSoft,
    onSurfaceVariant = TextMid,
    error = Danger,
    onError = Color.White,
    outline = Hairline,
)

private fun appTypography(font: FontFamily): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = font),
        displayMedium = b.displayMedium.copy(fontFamily = font),
        displaySmall = b.displaySmall.copy(fontFamily = font),
        headlineLarge = b.headlineLarge.copy(fontFamily = font),
        headlineMedium = b.headlineMedium.copy(fontFamily = font),
        headlineSmall = b.headlineSmall.copy(fontFamily = font),
        titleLarge = b.titleLarge.copy(fontFamily = font),
        titleMedium = b.titleMedium.copy(fontFamily = font),
        titleSmall = b.titleSmall.copy(fontFamily = font),
        bodyLarge = b.bodyLarge.copy(fontFamily = font),
        bodyMedium = b.bodyMedium.copy(fontFamily = font),
        bodySmall = b.bodySmall.copy(fontFamily = font),
        labelLarge = b.labelLarge.copy(fontFamily = font),
        labelMedium = b.labelMedium.copy(fontFamily = font),
        labelSmall = b.labelSmall.copy(fontFamily = font),
    )
}

private val KaoTypography = appTypography(Jost)

@Composable
fun KaoToFaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KaoColors,
        typography = KaoTypography,
        content = content,
    )
}
