package ir.pinbom.app.ui.components.fx

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.reduceMotion
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A card that leans into the finger: the edge you press dips a few degrees and
 * springs back on release. It only *watches* pointers (initial pass, nothing is
 * consumed), so clicks, long-presses and scrolling behave exactly as before.
 */
@Composable
fun Modifier.pressTilt(maxDegrees: Float = 5f, enabled: Boolean = true): Modifier {
    if (!enabled || reduceMotion() || !LocalSpecialEffectsEnabled.current) return this
    val scope = rememberCoroutineScope()
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val slopPx = with(density) { 20.dp.toPx() }
    val cameraPx = with(density) { 14.dp.toPx() * 8f }
    val soft = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
    val settle = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)

    return this
        .onSizeChanged { size = it }
        .pointerInput(maxDegrees) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val w = size.width.coerceAtLeast(1).toFloat()
                val h = size.height.coerceAtLeast(1).toFloat()
                fun lean(x: Float, y: Float) {
                    val nx = ((x / w) - 0.5f) * 2f
                    val ny = ((y / h) - 0.5f) * 2f
                    scope.launch { tiltY.animateTo(nx.coerceIn(-1f, 1f) * maxDegrees, soft) }
                    scope.launch { tiltX.animateTo(-ny.coerceIn(-1f, 1f) * maxDegrees, soft) }
                }
                lean(down.position.x, down.position.y)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    // A drag means scrolling, not pressing: let go of the lean.
                    if (abs(change.position.x - down.position.x) > slopPx || abs(change.position.y - down.position.y) > slopPx) break
                }
                scope.launch { tiltX.animateTo(0f, settle) }
                scope.launch { tiltY.animateTo(0f, settle) }
            }
        }
        .graphicsLayer {
            rotationX = tiltX.value
            rotationY = tiltY.value
            cameraDistance = cameraPx
        }
}

/**
 * Plays an XML AnimatedVectorDrawable (res/drawable/avd_*.xml). Flip [atEnd] to
 * run the animation forward (or backward when it goes back to false).
 */
@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun FxAnimatedIcon(
    @DrawableRes avd: Int,
    atEnd: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 24.dp,
) {
    val image = AnimatedImageVector.animatedVectorResource(avd)
    val painter = rememberAnimatedVectorPainter(animatedImageVector = image, atEnd = atEnd)
    Image(painter = painter, contentDescription = contentDescription, modifier = modifier.size(size))
}
