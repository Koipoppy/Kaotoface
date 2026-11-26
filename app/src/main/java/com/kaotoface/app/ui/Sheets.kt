package com.kaotoface.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.text.InputType
import com.kaotoface.app.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaotoface.app.core.AppSettings
import com.kaotoface.app.core.DiscussionRecord
import com.kaotoface.app.core.DisplayMode
import com.kaotoface.app.core.Languages
import com.kaotoface.app.core.Member
import com.kaotoface.app.core.ModelInfo
import com.kaotoface.app.core.RoomUi
import com.kaotoface.app.core.SessionEngine
import com.kaotoface.app.core.SettingsStore
import com.kaotoface.app.core.TranslateTone
import com.kaotoface.app.ui.theme.Danger
import com.kaotoface.app.ui.theme.EnAccent
import com.kaotoface.app.ui.theme.Han
import com.kaotoface.app.ui.theme.InkSoft
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.Paper
import com.kaotoface.app.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kaotoface.app.ui.theme.TextHi
import com.kaotoface.app.ui.theme.TextLo
import com.kaotoface.app.ui.theme.TextMid
import com.kaotoface.app.ui.theme.ZhAccent

// --------------------------------------------------------------------- shell

@Composable
fun KaoSheet(
    onDismiss: () -> Unit,
    fill: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        progress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
    }
    val scrimInteraction = remember { MutableInteractionSource() }
    val sheetInteraction = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f * (1f - progress.value)))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
            ) { onDismiss() },
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(if (fill) Modifier.fillMaxHeight(0.93f) else Modifier.heightIn(max = maxHeight * 0.9f))
                .graphicsLayer { translationY = progress.value * screenHeightPx }
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFFFBFBFB))
                .clickable(
                    interactionSource = sheetInteraction,
                    indication = null,
                ) {},
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp, 4.dp)
                        .clip(CircleShape)
                        .background(Color(0x26111827)),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 26.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Text(
        title,
        color = TextHi,
        fontFamily = Han,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
    )
    if (subtitle != null) {
        Spacer(Modifier.height(5.dp))
        Text(
            subtitle,
            color = TextLo,
            fontFamily = Han,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
fun SheetDivider() {
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = Color(0x10111827))
    Spacer(Modifier.height(18.dp))
}

// ------------------------------------------------------------------- helpers

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val accent = EnAccent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color(0x06111827))
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.30f) else Color(0x12111827),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            color = if (selected) accent else TextMid,
            fontFamily = Han,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text,
        color = TextLo,
        fontFamily = Han,
        fontSize = 10.5.sp,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun KaoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        textStyle = TextStyle(fontFamily = Jost, fontSize = 13.sp, color = TextHi),
        placeholder = {
            Text(placeholder, color = TextLo.copy(alpha = 0.6f), fontFamily = Han, fontSize = 12.sp)
        },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(13.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EnAccent.copy(alpha = 0.55f),
            unfocusedBorderColor = Color(0x18111827),
            focusedContainerColor = Color(0xFFFFFFFF),
            unfocusedContainerColor = Color(0xFFFFFFFF),
            cursorColor = EnAccent,
            focusedTextColor = TextHi,
            unfocusedTextColor = TextHi,
        ),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    icon: KaoIcon? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(listOf(TextHi, Color(0xFF2E2E2E)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0x08000000), Color(0x05000000)))
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                KaoGlyph(
                    icon = icon,
                    tint = if (enabled) Color.White else TextLo,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text,
                color = if (enabled) Color.White else TextLo,
                fontFamily = Han,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
fun ActionRow(
    icon: KaoIcon,
    title: String,
    subtitle: String,
    tint: Color = TextHi.copy(alpha = 0.8f),
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFFFFFFF))
            .border(1.dp, Color(0x10111827), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (danger) Danger.copy(alpha = 0.09f) else Color(0x07111827)),
            contentAlignment = Alignment.Center,
        ) {
            KaoGlyph(icon, if (danger) Danger else tint, Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) Danger else TextHi,
                fontFamily = Han,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp,
            )
            Text(
                subtitle,
                color = TextLo,
                fontFamily = Han,
                fontSize = 10.sp,
                lineHeight = 12.sp,
            )
        }
        Text("›", color = if (danger) Danger.copy(alpha = 0.6f) else TextLo, fontSize = 18.sp)
    }
}

@Composable
private fun MemberChip(member: Member, selfId: String?, hostName: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFFFFFFFF))
            .border(1.dp, Color(0x10111827), RoundedCornerShape(13.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (member.isHost) ZhAccent.copy(alpha = 0.20f) else EnAccent.copy(alpha = 0.16f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = member.name.take(1),
                color = if (member.isHost) ZhAccent else EnAccent,
                fontFamily = Han,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = member.name + if (member.id == selfId) " · 我" else "",
            color = TextHi.copy(alpha = 0.9f),
            fontFamily = Han,
            fontSize = 10.5.sp,
        )
        if (member.isHost) {
            Text(
                "主持",
                color = ZhAccent,
                fontFamily = Han,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(ZhAccent.copy(alpha = 0.12f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("kaotoface", text))
}

// ------------------------------------------------------------------ code boxes

@Composable
fun CodeBoxes(code: String, accent: Color = EnAccent, big: Boolean = false, cursorIndex: Int? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 8.dp)) {
        repeat(4) { i ->
            val filled = i < code.length
            val isCursor = cursorIndex == i
            Box(
                modifier = Modifier
                    .size(width = if (big) 54.dp else 50.dp, height = if (big) 66.dp else 58.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (filled) accent.copy(alpha = 0.07f) else Color(0x04111827))
                    .border(
                        1.dp,
                        if (isCursor) accent.copy(alpha = 0.55f) else if (filled) accent.copy(alpha = 0.24f) else Color(0x14111827),
                        RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code.getOrNull(i)?.toString() ?: "",
                    color = TextHi,
                    fontFamily = Jost,
                    fontSize = if (big) 30.sp else 26.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ room sheet

@Composable
fun RoomSheet(
    room: RoomUi?,
    nickname: String,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onJoin: (String, String?) -> Unit,
    onLeave: () -> Unit,
    onAllowGuestMic: (Boolean) -> Unit,
    error: String?,
) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var showIp by rememberSaveable { mutableStateOf(false) }

    KaoSheet(onDismiss = onDismiss) {
        if (!error.isNullOrBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Danger.copy(alpha = 0.10f))
                    .border(1.dp, Danger.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    error,
                    color = Danger,
                    fontFamily = Han,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        when {
            room == null -> {
                SheetTitle("局域网房间", "同一 Wi-Fi · 四位暗号 · 无需账号")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(EnAccent.copy(alpha = 0.13f), ZhAccent.copy(alpha = 0.09f)),
                            ),
                        )
                        .border(1.dp, EnAccent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onCreate)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x0A111827)),
                        contentAlignment = Alignment.Center,
                    ) {
                        KaoGlyph(KaoIcon.Plus, TextHi, Modifier.size(18.dp), 1.8.dp)
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text("创建房间", color = TextHi, fontFamily = Han, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "生成四位暗号，同伴在同一 Wi-Fi 下即可加入",
                            color = TextLo,
                            fontFamily = Han,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                    }
                    KaoGlyph(KaoIcon.ChevronDown, TextLo, Modifier.size(14.dp))
                }

                Spacer(Modifier.height(26.dp))
                SectionLabel("加入房间")
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = code,
                    onValueChange = { v ->
                        if (v.length <= 4 && v.all { it.isDigit() }) code = v
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                    cursorBrush = SolidColor(Color.Transparent),
                    decorationBox = {
                        CodeBoxes(code, cursorIndex = if (code.length < 4) code.length else null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showIp = !showIp }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "找不到房间？手动输入主机 IP",
                        color = TextLo,
                        fontFamily = Han,
                        fontSize = 9.5.sp,
                    )
                    KaoGlyph(KaoIcon.ChevronDown, TextLo, Modifier.size(10.dp))
                }
                if (showIp) {
                    Spacer(Modifier.height(8.dp))
                    KaoTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        placeholder = "例如 192.168.1.23",
                        keyboardType = KeyboardType.Uri,
                    )
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = "加入房间",
                    enabled = code.length == 4,
                    icon = KaoIcon.PersonAdd,
                    onClick = { onJoin(code, manualIp.takeIf { it.isNotBlank() }) },
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "房间只在局域网内通信，讨论内容不经过服务器；音频始终在本机识别，仅文字匿名发送给 DeepSeek 翻译。",
                    color = TextLo.copy(alpha = 0.8f),
                    fontFamily = Han,
                    fontSize = 9.5.sp,
                    lineHeight = 15.sp,
                )
            }
            room.joining -> {
                SheetTitle("正在加入房间", "暗号 ${room.code}")
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = EnAccent, strokeWidth = 2.5.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "正在局域网内寻找房间…",
                        color = TextMid,
                        fontFamily = Han,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "请确认两台设备连接同一个 Wi-Fi",
                        color = TextLo,
                        fontFamily = Han,
                        fontSize = 10.sp,
                    )
                    Spacer(Modifier.height(22.dp))
                    TextButton(onClick = onLeave) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            KaoGlyph(KaoIcon.Close, TextLo, Modifier.size(13.dp))
                            Text("取消", color = TextLo, fontFamily = Han, fontSize = 12.sp)
                        }
                    }
                }
            }
            else -> {
                SheetTitle(
                    if (room.hosting) "房间已创建" else "已加入房间",
                    if (room.hosting) "让同伴输入暗号加入，或手动填写你的 IP" else "房主 ${room.hostName.ifBlank { "—" }}",
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeBoxes(room.code, big = true)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0x07111827))
                            .clickable {
                                copyToClipboard(context, room.code)
                                Toast.makeText(context, "暗号已复制", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        KaoGlyph(KaoIcon.Copy, TextMid, Modifier.size(15.dp))
                    }
                }
                if (room.hosting) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x05111827))
                            .clickable {
                                val ip = room.hostAddress
                                if (!ip.isNullOrBlank()) {
                                    copyToClipboard(context, ip)
                                    Toast.makeText(context, "地址已复制", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("本机 IP", color = TextLo, fontFamily = Han, fontSize = 10.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            room.hostAddress ?: "获取中…",
                            color = TextHi.copy(alpha = 0.85f),
                            fontFamily = Jost,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        KaoGlyph(KaoIcon.Copy, TextLo, Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("房间里的人")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${room.members.size}",
                        color = TextLo,
                        fontFamily = Jost,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    room.members.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            row.forEach { m ->
                                MemberChip(m, room.selfId, room.hostName)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (room.hosting) {
                    KaoSwitchRow(
                        title = "允许成员用手机收音",
                        subtitle = "开启后，成员说话也会被翻译并同步给所有人",
                        checked = room.allowGuestMic,
                        onCheckedChange = onAllowGuestMic,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x05111827))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KaoGlyph(KaoIcon.Mic, TextLo, Modifier.size(14.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (room.allowGuestMic) "主持人已开放成员收音，你可以开始发言" else "正在旁听 · 主持人未开放成员收音",
                            color = TextLo,
                            fontFamily = Han,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Danger.copy(alpha = 0.09f))
                        .border(1.dp, Danger.copy(alpha = 0.16f), RoundedCornerShape(13.dp))
                        .clickable(onClick = onLeave),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    KaoGlyph(KaoIcon.Logout, Danger, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (room.hosting) "结束房间" else "离开房间",
                        color = Danger,
                        fontFamily = Han,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "我的昵称：$nickname · 可在设置中修改",
                    color = TextLo.copy(alpha = 0.8f),
                    fontFamily = Han,
                    fontSize = 9.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// --------------------------------------------------------------- settings sheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    settingsStore: SettingsStore,
    engine: SessionEngine,
    lineCount: Int,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onDemo: () -> Unit,
) {
    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var fontScale by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }
    var confirmClear by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }
    var languagePicker by remember { mutableStateOf<String?>(null) }

    fun loadModels() {
        if (modelsLoading) return
        modelsLoading = true
        modelsError = null
        engine.fetchModels { result ->
            modelsLoading = false
            result.onSuccess { models = it }
                .onFailure { modelsError = it.message ?: "读取模型失败" }
        }
    }

    LaunchedEffect(Unit) { loadModels() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = InkSoft,
            title = { Text("清空当前记录？", color = TextHi, fontFamily = Han, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (lineCount == 0) "当前记录是空的。" else "当前记录的 $lineCount 句对话将被永久删除。",
                    color = TextMid,
                    fontFamily = Han,
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    engine.clearLines()
                    Toast.makeText(context, "记录已清空", Toast.LENGTH_SHORT).show()
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

    languagePicker?.let { which ->
        AlertDialog(
            onDismissRequest = { languagePicker = null },
            containerColor = InkSoft,
            title = {
                Text(
                    if (which == "A") "选择语言 A" else "选择语言 B",
                    color = TextHi,
                    fontFamily = Han,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Languages.all.forEach { language ->
                        val selected = if (which == "A") settings.langA == language.code else settings.langB == language.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0x0F111827) else Color.Transparent)
                                .clickable {
                                    if (which == "A") settingsStore.setLangA(language.code)
                                    else settingsStore.setLangB(language.code)
                                    languagePicker = null
                                }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                language.label,
                                color = if (selected) TextHi else TextMid,
                                fontFamily = Han,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                KaoGlyph(KaoIcon.Check, TextHi, Modifier.size(13.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languagePicker = null }) {
                    Text("取消", color = TextLo, fontFamily = Han)
                }
            },
        )
    }

    KaoSheet(onDismiss = onDismiss, fill = true) {
        SheetTitle("设置", "翻译 · 阅读 · 房间")
        SectionLabel("翻译引擎")
        Spacer(Modifier.height(12.dp))
        FieldLabel("DeepSeek API Key")
        KaoTextField(
            value = settings.apiKey,
            onValueChange = { settingsStore.setApiKey(it) },
            placeholder = "sk-...",
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                TextButton(onClick = { showKey = !showKey }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        KaoGlyph(
                            if (showKey) KaoIcon.VisibilityOff else KaoIcon.Visibility,
                            TextLo,
                            Modifier.size(13.dp),
                        )
                        Text(if (showKey) "隐藏" else "显示", color = TextLo, fontFamily = Han, fontSize = 10.sp)
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButtonLabel(
                text = if (testing) "测试中…" else "测试连接",
                icon = KaoIcon.Refresh,
                onClick = {
                    if (testing) return@GlassButtonLabel
                    testing = true
                    testMessage = null
                    engine.testApi { result ->
                        testing = false
                        result.onSuccess {
                            testOk = true
                            testMessage = "连接成功 · ${it} ms"
                        }.onFailure {
                            testOk = false
                            testMessage = it.message ?: "连接失败"
                        }
                    }
                },
                enabled = !testing,
            )
            Spacer(Modifier.width(10.dp))
            testMessage?.let {
                Text(
                    it,
                    color = if (testOk) Success else Danger,
                    fontFamily = Han,
                    fontSize = 10.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "密钥只保存在本机。本地只做 VAD 切句/预录；句末音频会上传 DeepSeek ASR 识别，文字用于翻译。",
            color = TextLo.copy(alpha = 0.8f),
            fontFamily = Han,
            fontSize = 9.sp,
            lineHeight = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("翻译模型")
            Spacer(Modifier.weight(1f))
            if (modelsLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    color = TextLo,
                    strokeWidth = 1.4.dp,
                )
                Spacer(Modifier.width(3.dp))
            } else {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { loadModels() }
                        .padding(4.dp),
                ) {
                    KaoGlyph(KaoIcon.Refresh, TextLo, Modifier.size(13.dp))
                }
            }
        }
        val shownModels = if (models.isEmpty() || models.any { it.id == settings.model }) {
            models.ifEmpty { listOf(ModelInfo(settings.model)) }
        } else {
            models + ModelInfo(settings.model)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shownModels.forEach { model ->
                ChoiceChip(model.name, settings.model == model.id) {
                    settingsStore.setModel(model.id)
                }
            }
        }
        if (modelsLoading) {
            Spacer(Modifier.height(6.dp))
            Text("正在读取可用模型…", color = TextLo, fontFamily = Han, fontSize = 9.5.sp)
        }
        modelsError?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = TextLo, fontFamily = Han, fontSize = 9.5.sp, lineHeight = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel("语气")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TranslateTone.entries.forEach { tone ->
                ChoiceChip(tone.label, settings.tone == tone) { settingsStore.setTone(tone) }
            }
        }
        Spacer(Modifier.height(16.dp))
        FieldLabel("API 地址（高级）")
        KaoTextField(
            value = settings.baseUrl,
            onValueChange = { settingsStore.setBaseUrl(it) },
            placeholder = "https://api.deepseek.com",
            keyboardType = KeyboardType.Uri,
        )

        SheetDivider()
        SectionLabel("两侧语言")
        Spacer(Modifier.height(12.dp))
        ActionRow(
            icon = KaoIcon.Swap,
            title = "语言 A",
            subtitle = Languages.label(settings.langA),
            onClick = { languagePicker = "A" },
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(
            icon = KaoIcon.Swap,
            title = "语言 B",
            subtitle = Languages.label(settings.langB),
            onClick = { languagePicker = "B" },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "A / B 为两侧语言；选到另一侧正在用的语言会自动对调。交换键只换位置，不改语言。",
            color = TextLo,
            fontFamily = Han,
            fontSize = 9.sp,
            lineHeight = 14.sp,
        )

        SheetDivider()
        SectionLabel("阅读体验")
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Aa 你好",
                color = TextHi,
                fontFamily = Han,
                fontSize = (17 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${(fontScale * 100).toInt()}%",
                color = TextLo,
                fontFamily = Jost,
                fontSize = 10.sp,
            )
        }
        Slider(
            value = fontScale,
            onValueChange = { fontScale = it },
            onValueChangeFinished = { settingsStore.setFontScale(fontScale) },
            valueRange = 0.82f..1.22f,
            thumb = {
                Box(
                    Modifier
                        .size(18.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Paper)
                        .border(2.dp, EnAccent, CircleShape),
                )
            },
            track = { sliderState ->
                val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                val fraction = if (span > 0f) {
                    ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
                } else 0f
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x14111827)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(EnAccent),
                    )
                }
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
        KaoSwitchRow(
            title = "点按当前句子朗读",
            subtitle = "使用系统 TTS，双语皆可；朗读时自动暂停收音",
            checked = settings.tts,
            onCheckedChange = { settingsStore.setTts(it) },
        )

        SheetDivider()
        SectionLabel("房间身份")
        Spacer(Modifier.height(12.dp))
        FieldLabel("我的昵称")
        KaoTextField(
            value = settings.nickname,
            onValueChange = { settingsStore.setNickname(it) },
            placeholder = "房间中显示的名字",
        )

        SheetDivider()
        SectionLabel("数据与关于")
        Spacer(Modifier.height(12.dp))
        ActionRow(
            icon = KaoIcon.Download,
            title = "导出与分享",
            subtitle = if (lineCount == 0) "还没有记录" else "$lineCount 句 · Markdown / 纯文本",
            onClick = onExport,
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(
            icon = KaoIcon.Play,
            title = "播放演示对话",
            subtitle = "用一段中英双语录音走通完整流程",
            onClick = onDemo,
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(
            icon = KaoIcon.Trash,
            title = "清空当前记录",
            subtitle = "删除本机保存的对话时间线",
            danger = true,
            onClick = { confirmClear = true },
        )
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "版本 ${BuildConfig.VERSION_NAME}",
                color = TextLo,
                fontFamily = Jost,
                fontSize = 9.5.sp,
                letterSpacing = 0.6.sp,
            )
            Text(
                "VAD 切句 · DeepSeek ASR/翻译 · 局域网直连",
                color = TextLo.copy(alpha = 0.7f),
                fontFamily = Han,
                fontSize = 9.sp,
            )
        }
    }
}

// --------------------------------------------------------------- records sheet

@Composable
fun RecordsSheet(
    records: List<DiscussionRecord>,
    onDismiss: () -> Unit,
    onShare: (DiscussionRecord, Boolean) -> Unit,
    onCopy: (DiscussionRecord) -> Unit,
    onSave: (DiscussionRecord) -> Unit,
) {
    var selected by remember { mutableStateOf<DiscussionRecord?>(null) }
    KaoSheet(onDismiss = onDismiss, fill = true) {
        val open = selected
        if (open == null) {
            SheetTitle("记录", "每次聆听自动保存一条 · 共 ${records.size} 条")
            if (records.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KaoGlyph(KaoIcon.Records, TextLo, Modifier.size(30.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("还没有记录", color = TextMid, fontFamily = Han, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "每次按下聆听键都会从一条新记录开始",
                            color = TextLo, fontFamily = Han, fontSize = 10.sp,
                        )
                    }
                }
            } else {
                records.sortedByDescending { it.createdAt }.forEach { record ->
                    RecordCard(
                        record = record,
                        onOpen = { selected = record },
                        onShare = { onShare(record, true) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable { selected = null },
                    contentAlignment = Alignment.Center,
                ) {
                    KaoGlyph(
                        KaoIcon.ChevronDown,
                        TextHi,
                        Modifier.size(15.dp).graphicsLayer { rotationZ = 90f },
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        open.name,
                        color = TextHi,
                        fontFamily = Han,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${open.lines.size} 句 · ${recordStamp(open.createdAt)}",
                        color = TextLo,
                        fontFamily = Han,
                        fontSize = 9.5.sp,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("分享 Markdown", icon = KaoIcon.Share, onClick = { onShare(open, true) })
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = KaoIcon.Share,
                title = "分享纯文本",
                subtitle = "任何聊天软件都能直接打开",
                onClick = { onShare(open, false) },
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = KaoIcon.Copy,
                title = "复制全文",
                subtitle = "粘贴到备忘录或聊天窗口",
                onClick = { onCopy(open) },
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = KaoIcon.Download,
                title = "保存到下载目录",
                subtitle = "Download/BilingualNotes",
                onClick = { onSave(open) },
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Color(0x10111827))
            Spacer(Modifier.height(12.dp))
            if (open.lines.isEmpty()) {
                Text(
                    "这条记录还没有句子。",
                    color = TextLo, fontFamily = Han, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            } else {
                open.lines.forEach { u ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(
                            u.en,
                            color = TextHi,
                            fontFamily = Han,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            u.zh,
                            color = TextMid,
                            fontFamily = Han,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    record: DiscussionRecord,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0x10111827), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x07111827)),
            contentAlignment = Alignment.Center,
        ) {
            KaoGlyph(KaoIcon.Records, TextHi, Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.name,
                color = TextHi,
                fontFamily = Han,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "${record.lines.size} 句 · ${recordStamp(record.createdAt)}",
                color = TextLo,
                fontFamily = Han,
                fontSize = 9.5.sp,
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            KaoGlyph(KaoIcon.Share, TextHi, Modifier.size(15.dp))
        }
        KaoGlyph(
            KaoIcon.ChevronDown,
            TextLo,
            Modifier.size(13.dp).graphicsLayer { rotationZ = -90f },
        )
    }
}

private fun recordStamp(at: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))

// ---------------------------------------------------------------- export sheet

@Composable
fun ExportSheet(
    count: Int,
    roomCode: String?,
    onDismiss: () -> Unit,
    onShareMarkdown: () -> Unit,
    onShareText: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    KaoSheet(onDismiss = onDismiss) {
        SheetTitle("导出与分享", if (roomCode != null) "房间 $roomCode · 共 $count 句" else "共 $count 句")
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFF5F8FD)),
                    ),
                )
                .border(1.dp, Color(0x10111827), RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Column {
                Text("双语讨论记录", color = TextHi, fontFamily = Han, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "双语逐句对照，带时间戳；Markdown 适合归档与二次编辑，纯文本适合直接发送。",
                    color = TextLo,
                    fontFamily = Han,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("分享 Markdown 文件", enabled = count > 0, icon = KaoIcon.Share, onClick = onShareMarkdown)
        Spacer(Modifier.height(10.dp))
        ActionRow(
            icon = KaoIcon.Share,
            title = "分享纯文本",
            subtitle = "任何聊天软件都能直接打开",
            onClick = onShareText,
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(
            icon = KaoIcon.Copy,
            title = "复制全文",
            subtitle = "粘贴到备忘录或聊天窗口",
            onClick = onCopy,
        )
        Spacer(Modifier.height(8.dp))
        ActionRow(
            icon = KaoIcon.Download,
            title = "保存到下载目录",
            subtitle = "Download/BilingualNotes，可离线保留",
            onClick = onSave,
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onClear, enabled = count > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KaoGlyph(
                    KaoIcon.Trash,
                    if (count > 0) Danger.copy(alpha = 0.85f) else TextLo,
                    Modifier.size(13.dp),
                )
                Text("清空记录", color = if (count > 0) Danger.copy(alpha = 0.85f) else TextLo, fontFamily = Han, fontSize = 11.sp)
            }
        }
    }
}
