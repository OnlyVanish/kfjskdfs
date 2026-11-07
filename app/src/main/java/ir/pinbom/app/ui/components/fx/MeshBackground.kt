package ir.pinbom.app.ui.components.fx

import androidx.compose.runtime.remember
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import ir.pinbom.app.ui.theme.BrandRedSoft
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.PinBomColors
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.reduceMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Low-alpha radial blobs, with no real-time blur or per-frame layout work. */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    dust: Boolean = false,
) {
    val colors = PinBomTheme.colors
    if (!LocalSpecialEffectsEnabled.current || reduceMotion()) {
        Canvas(modifier) { drawPinBomMesh(colors, 0.5f, 0.5f, 0.5f, dust = false, dustPhase = 0f) }
    } else {
        AnimatedMeshBackground(modifier = modifier, colors = colors, dust = dust)
    }
}

@Composable
private fun AnimatedMeshBackground(
    modifier: Modifier,
    colors: PinBomColors,
    dust: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "pinBomMesh")
    val driftA = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshDriftA",
    )
    val driftB = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(9_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshDriftB",
    )
    val driftC = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "meshDriftC",
    )
    val dustPhase: State<Float> = if (dust) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Restart),
            label = "meshDustPhase",
        )
    } else {
        // Form pages have a moving mesh but no dust clock or dust redraws.
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(modifier) {
        drawPinBomMesh(colors, driftA.value, driftB.value, driftC.value, dust, if (dust) dustPhase.value else 0f)
    }
}

private fun DrawScope.drawPinBomMesh(
    colors: PinBomColors,
    a: Float,
    b: Float,
    c: Float,
    dust: Boolean,
    dustPhase: Float,
) {
    drawRect(colors.background)
    val intensity = if (colors.isDark) 0.055f else 0.115f
    val radius = size.minDimension * 0.86f
    val aPoint = Offset(
        size.width * (0.16f + 0.20f * sin(a * Math.PI.toFloat() * 2f)),
        size.height * (0.12f + 0.10f * cos(a * Math.PI.toFloat() * 2f)),
    )
    val bPoint = Offset(
        size.width * (0.86f + 0.10f * cos(b * Math.PI.toFloat() * 2f)),
        size.height * (0.38f + 0.18f * sin(b * Math.PI.toFloat() * 2f)),
    )
    val cPoint = Offset(
        size.width * (0.38f + 0.18f * sin(c * Math.PI.toFloat() * 2f)),
        size.height * (0.88f - 0.12f * cos(c * Math.PI.toFloat() * 2f)),
    )
    drawBlob(aPoint, colors.primary, radius, intensity, colors.background.copy(alpha = 0f))
    drawBlob(bPoint, BrandRedSoft, radius * 0.82f, intensity * 0.72f, colors.background.copy(alpha = 0f))
    drawBlob(cPoint, colors.primaryPressed, radius, intensity * 0.58f, colors.background.copy(alpha = 0f))

    if (dust) {
        repeat(14) { index ->
            val seed = index * 0.6180339f
            val travel = (dustPhase + seed) % 1f
            val x = size.width * (0.08f + ((index * 37) % 84) / 100f)
            val y = size.height * (1.04f - travel * 1.16f)
            val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin((travel + seed) * Math.PI.toFloat() * 2f))
            val alpha = (if (colors.isDark) 0.025f else 0.045f) * pulse * (1f - abs(travel - 0.5f))
            drawCircle(
                color = BrandRedSoft.copy(alpha = alpha),
                radius = (3f + (index % 4) * 1.2f) * density,
                center = Offset(x, y),
            )
        }
    }
}

private fun DrawScope.drawBlob(center: Offset, color: androidx.compose.ui.graphics.Color, radius: Float, alpha: Float, clear: androidx.compose.ui.graphics.Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha),
                0.42f to color.copy(alpha = alpha * 0.42f),
                1f to clear,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
