package com.kaotoface.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaotoface.app.core.DisplayMode
import com.kaotoface.app.core.RoomUi
import com.kaotoface.app.ui.theme.EnAccent
import com.kaotoface.app.ui.theme.Jost
import com.kaotoface.app.ui.theme.TextLo
import com.kaotoface.app.ui.theme.TextMid

/**
 * One fixed control cluster in the exact centre of the screen. The two groups
 * keep the same spacing around the mic, turned with the panel layout:
 *
 *   portrait:  [ settings share room ]  ( listen )  [ mode swap latest ]
 *   landscape:            [ settings share room ]
 *                                  ( listen )
 *                         [ mode swap latest ]
 */
@Composable
fun ControlDock(
    listening: Boolean,
    preparing: Boolean,
    progress: Float,
    level: Float,
    mode: DisplayMode,
    browsing: Boolean,
    room: RoomUi?,
    onMic: () -> Unit,
    onMode: () -> Unit,
    onFollow: () -> Unit,
    onSwap: () -> Unit,
    onRoom: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    if (vertical) {
        // Landscape: the row is parallel to the SHORT edge (vertical). Every
        // icon keeps the same physical position it had in portrait:
        //   portrait left  [settings records room]  -> bottom, nearest mic = room
        //   portrait right [mode swap follow]       -> top,    nearest mic = mode
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FollowDockIcon(browsing, onFollow)
                DockIcon(KaoIcon.Swap, "交换语言位置", onSwap)
                ModeDockIcon(mode, onMode)
            }
            MicButton(
                listening = listening,
                preparing = preparing,
                progress = progress,
                level = level,
                onClick = onMic,
                size = 54.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoomDockIcon(room, onRoom)
                DockIcon(KaoIcon.Records, "记录", onRecords)
                DockIcon(KaoIcon.Settings, "设置", onSettings)
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DockIcon(KaoIcon.Settings, "设置", onSettings)
                DockIcon(KaoIcon.Records, "记录", onRecords)
                RoomDockIcon(room, onRoom)
            }
            MicButton(
                listening = listening,
                preparing = preparing,
                progress = progress,
                level = level,
                onClick = onMic,
                size = 54.dp,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModeDockIcon(mode, onMode)
                DockIcon(KaoIcon.Swap, "交换语言位置", onSwap)
                FollowDockIcon(browsing, onFollow)
            }
        }
    }
}

@Composable
private fun DockIcon(
    icon: KaoIcon,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Black,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed && enabled) Color(0x12111827) else Color.Transparent,
        animationSpec = tween(120),
        label = "dockPress",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background)
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
            tint = if (enabled) tint else tint.copy(alpha = 0.26f),
            modifier = Modifier.size(17.dp),
        )
        badge?.invoke(this)
    }
}

@Composable
private fun RoomDockIcon(room: RoomUi?, onClick: () -> Unit) {
    DockIcon(
        icon = KaoIcon.People,
        label = if (room == null) "创建或加入局域网房间" else "房间 ${room.code}",
        onClick = onClick,
        tint = Color.Black,
        badge = if (room != null) {
            {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(TextLo),
                )
            }
        } else null,
    )
}

@Composable
private fun ModeDockIcon(mode: DisplayMode, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) Color(0x12111827) else Color.Transparent,
        animationSpec = tween(120),
        label = "modePress",
    )
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics {
                contentDescription = "切换显示模式"
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        ModeGlyph(mode, Color.Black, Modifier.size(18.dp))
    }
}

@Composable
private fun FollowDockIcon(browsing: Boolean, onClick: () -> Unit) {
    DockIcon(
        icon = KaoIcon.FollowLatest,
        label = "回到最新一句",
        onClick = onClick,
        tint = Color.Black,
    )
}
