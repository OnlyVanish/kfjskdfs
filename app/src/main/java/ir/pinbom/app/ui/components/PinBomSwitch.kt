package ir.pinbom.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pinbom.app.ui.theme.AbsoluteBlack
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.reduceMotion
import kotlin.math.roundToInt

/** Compact switch with symmetric 3dp track insets, spring travel and press stretch. */
@Composable
fun PinBomSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion() || !LocalSpecialEffectsEnabled.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress = animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (reduce) snap() else Motion.layoutSpring,
        label = "pinBomSwitchThumb",
    )
    val stretch = animateFloatAsState(
        targetValue = if (pressed && enabled && !reduce) 1.12f else 1f,
        animationSpec = if (reduce) snap() else Motion.pressSpring,
        label = "pinBomSwitchPressStretch",
    )
    val trackColor = animateColorAsState(
        targetValue = when {
            !enabled -> colors.surfaceGrey
            checked -> colors.primary
            else -> colors.surfaceGreyPressed
        },
        animationSpec = if (reduce) snap() else Motion.colorShort,
        label = "pinBomSwitchTrack",
    )

    Box(
        modifier = modifier
            .size(width = 48.dp, height = 48.dp)
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .pinbomClick(
                onClick = { if (enabled) onCheckedChange(!checked) },
                source = interaction,
                enabled = enabled,
                pressedScale = 0.96f,
                role = Role.Switch,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .size(width = 42.dp, height = 25.dp)
                .clip(RoundedCornerShape(percent = 50))
                .drawBehind {
                    drawRoundRect(
                        color = trackColor.value,
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
                .then(
                    if (checked) Modifier else Modifier.border(
                        width = 1.dp,
                        color = colors.divider,
                        shape = RoundedCornerShape(percent = 50),
                    ),
                ),
        ) {
            // Effective travel = track width - thumb width - 2 × inner padding.
            // 42dp - 19dp - (2 × 3dp) = 17dp; the thumb's two edge insets stay 3dp.
            val travel = maxWidth - 19.dp - 6.dp
            Box(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset {
                            val direction = if (rtl) -1f else 1f
                            IntOffset((direction * travel.toPx() * progress.value).roundToInt(), 0)
                        }
                        .size(width = 19.dp, height = 19.dp)
                        .graphicsLayer {
                            scaleX = stretch.value
                            scaleY = 1f + (stretch.value - 1f) * 0.28f
                        }
                        .clip(CircleShape)
                        .background(colors.surface)
                        .then(
                            if (colors.isDark) Modifier else Modifier.border(
                                width = 0.5.dp,
                                color = AbsoluteBlack.copy(alpha = 0.06f),
                                shape = CircleShape,
                            ),
                        ),
                )
            }
        }
    }
}
