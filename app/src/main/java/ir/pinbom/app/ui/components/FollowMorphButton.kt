package ir.pinbom.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ir.pinbom.app.R
import ir.pinbom.app.ui.components.fx.ParticleBurstKind
import ir.pinbom.app.ui.components.fx.PinBomParticles
import ir.pinbom.app.ui.theme.AbsoluteWhite
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.Motion
import ir.pinbom.app.ui.theme.PinBomShape
import ir.pinbom.app.ui.theme.PinBomText
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.pinbomClick
import ir.pinbom.app.ui.theme.reduceMotion
import androidx.compose.ui.res.stringResource

/** A follow CTA whose label, width, and checkmark morph without touching follow state logic. */
@Composable
fun FollowMorphButton(
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PinBomTheme.colors
    val reduce = reduceMotion()
    val effects = LocalSpecialEffectsEnabled.current
    val interaction = remember { MutableInteractionSource() }
    val background = animateColorAsState(
        targetValue = if (following) colors.surfaceGrey else colors.primary,
        animationSpec = if (reduce || !effects) snap() else Motion.colorShort,
        label = "followButtonBackground",
    )
    val foreground = animateColorAsState(
        targetValue = if (following) colors.textPrimary else AbsoluteWhite,
        animationSpec = if (reduce || !effects) snap() else Motion.colorShort,
        label = "followButtonForeground",
    )
    val checkProgress = remember { Animatable(if (following) 1f else 0f) }
    var previousFollowing by remember { mutableStateOf(following) }
    var sparkleTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(following, reduce, effects) {
        if (following != previousFollowing) {
            if (following && effects && !reduce) sparkleTrigger += 1
            if (reduce || !effects) checkProgress.snapTo(if (following) 1f else 0f)
            else if (following) checkProgress.animateTo(1f, Motion.popSpring)
            else checkProgress.animateTo(0f, Motion.pressSpring)
            previousFollowing = following
        }
    }

    Box(
        modifier = modifier
            .clip(PinBomShape.pill)
            .drawBehind {
                drawRoundRect(
                    color = background.value,
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
            .animateContentSize(animationSpec = if (reduce || !effects) snap() else spring(dampingRatio = 0.62f, stiffness = 520f))
            .pinbomClick(
                onClick = onClick,
                source = interaction,
                enabled = enabled,
                pressedScale = 0.96f,
                role = Role.Button,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (effects && !reduce && sparkleTrigger > 0) {
            PinBomParticles(
                trigger = sparkleTrigger,
                modifier = Modifier.matchParentSize(),
                kind = ParticleBurstKind.Sparkle,
                maxParticles = 6,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(if (following) R.string.action_following else R.string.action_follow),
                style = PinBomText.button,
                color = foreground.value,
                maxLines = 1,
            )
            if (following) {
                Spacer(Modifier.size(7.dp))
                Canvas(Modifier.size(15.dp)) {
                    if (reduce || !effects) {
                        drawLine(foreground.value, Offset(size.width * 0.16f, size.height * 0.52f), Offset(size.width * 0.42f, size.height * 0.78f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(foreground.value, Offset(size.width * 0.42f, size.height * 0.78f), Offset(size.width * 0.86f, size.height * 0.22f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    } else {
                        val p = checkProgress.value.coerceIn(0f, 1f)
                        val start = Offset(size.width * 0.16f, size.height * 0.52f)
                        val middle = Offset(size.width * 0.42f, size.height * 0.78f)
                        val end = Offset(size.width * 0.86f, size.height * 0.22f)
                        if (p < 0.5f) {
                            val t = p / 0.5f
                            drawLine(foreground.value, start, Offset(start.x + (middle.x - start.x) * t, start.y + (middle.y - start.y) * t), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        } else {
                            drawLine(foreground.value, start, middle, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            val t = (p - 0.5f) / 0.5f
                            drawLine(foreground.value, middle, Offset(middle.x + (end.x - middle.x) * t, middle.y + (end.y - middle.y) * t), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }
}
