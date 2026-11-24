package com.kaotoface.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaotoface.app.core.DisplayMode
import com.kaotoface.app.ui.theme.PhosphorFill

/**
 * One soft, rounded, filled icon system: Phosphor Fill.
 * Rendered from the bundled font so every button shares the same optical weight.
 */
enum class KaoIcon(val cp: Int) {
    Mic(0xe326),
    Listen(0xe2a6),      // headphones: "listening"
    Stop(0xe46c),
    Play(0xe3d0),
    Share(0xe408),       // share-network
    Records(0xe34e),     // notebook
    Settings(0xe272),    // gear-six
    Swap(0xe0a0),        // arrows-left-right
    People(0xe68e),      // users-three
    More(0xe1fe),
    Close(0xe4f6),
    Check(0xe182),
    Copy(0xe1ca),
    Download(0xe20c),
    Speaker(0xe44a),
    Refresh(0xe036),
    Plus(0xe3d4),
    Trash(0xe4a6),
    Logout(0xe42a),
    ChevronDown(0xe136),
    FollowLatest(0xe136),
    PersonAdd(0xe4d0),
    Visibility(0xe220),
    VisibilityOff(0xe224),
}

private const val MODE_LANDSCAPE = 0xe546 // columns
private const val MODE_PORTRAIT = 0xe5a2  // rows
private const val MODE_FACE_TO_FACE = 0xe098 // arrows-down-up

@Composable
private fun IconGlyph(
    codepoint: Int,
    tint: Color,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (maxWidth * 1.08f).toSp() }
        Text(
            text = codepoint.toChar().toString(),
            color = tint,
            fontFamily = PhosphorFill,
            fontSize = fontSize,
            lineHeight = fontSize,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
fun KaoGlyph(
    icon: KaoIcon,
    tint: Color,
    modifier: Modifier = Modifier.size(20.dp),
    stroke: Dp = 1.7.dp,
) {
    IconGlyph(icon.cp, tint, modifier)
}

@Composable
fun ModeGlyph(
    mode: DisplayMode,
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp),
    stroke: Dp = 1.5.dp,
) {
    val cp = when (mode) {
        DisplayMode.LANDSCAPE -> MODE_LANDSCAPE
        DisplayMode.PORTRAIT -> MODE_PORTRAIT
        DisplayMode.FACE_TO_FACE -> MODE_FACE_TO_FACE
    }
    IconGlyph(cp, tint, modifier)
}
