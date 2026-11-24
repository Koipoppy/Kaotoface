package com.kaotoface.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.kaotoface.app.ui.theme.Han
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.PhosphorFill

/*
 * App-wide text: Jost for every Latin letter, 思源黑体 for CJK.
 * Splitting the string into script runs is deterministic across Android
 * versions (a platform fallback chain is not), so English embedded in a Chinese
 * label is still set in Jost.
 */

private fun isCjk(c: Char): Boolean {
    val code = c.code
    return (code in 0x4E00..0x9FFF) || (code in 0x3400..0x4DBF) ||
        (code in 0xF900..0xFAFF) || (code in 0x3000..0x303F) ||
        (code in 0xFF00..0xFFEF) || c == '·' || c == '—' || c == '…'
}

private fun scriptStyled(text: String, weight: FontWeight): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val cjk = isCjk(text[i])
        var j = i + 1
        while (j < text.length) {
            val c = text[j]
            if (isCjk(c) != cjk) {
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
                fontWeight = if (cjk) {
                    if (weight >= FontWeight.SemiBold) FontWeight.Bold else FontWeight.Normal
                } else {
                    weight
                },
            ),
        ) { append(chunk) }
        i = j
    }
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    // Icon-font glyphs must be rendered as-is.
    if (fontFamily == PhosphorFill) {
        MaterialText(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
        return
    }
    val weight = fontWeight ?: style.fontWeight ?: FontWeight.Normal
    MaterialText(
        text = scriptStyled(text, weight),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = weight,
        fontFamily = Jost,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style.copy(fontFamily = Jost),
    )
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}
