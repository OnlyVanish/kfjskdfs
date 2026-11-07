package ir.pinbom.app.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import ir.pinbom.app.ui.theme.PinBomTheme
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Velocity
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.specialEffectsEnabled
import kotlin.math.roundToInt

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · collapsing header
 *  The brand bar and the filter chips slide away while you scroll down a wall
 *  and slide back the moment you scroll up (or reach the top), so the pins get
 *  the whole screen while you browse.
 *
 *  How it stays smooth: the offset is a single float read only inside the layout
 *  and graphics-layer lambdas, so a scroll frame re-measures one node and
 *  recomposes nothing. It reacts only to scroll the list really *consumed*, so a
 *  non-scrollable empty state never collapses it. On release it snaps to fully
 *  shown or fully hidden with a spring instead of stopping half-way.
 * ──────────────────────────────────────────────────────────────────────────── */

@Stable
class CollapsingHeaderState {
    /** 0 = fully shown … −height = fully hidden. Read in layout/draw only. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Measured height of the header; a plain field, written during layout. */
    internal var heightPx: Float = 0f
    internal var motionEnabled: Boolean = true

    internal fun scrollBy(dy: Float) {
        if (heightPx <= 0f) return
        offsetPx = (offsetPx + dy).coerceIn(-heightPx, 0f)
    }

    /** Animate to fully shown. */
    suspend fun show() = animateTo(0f)

    /** Spring to whichever end is closer. */
    internal suspend fun settle() {
        if (heightPx <= 0f) return
        animateTo(if (offsetPx < -heightPx / 2f) -heightPx else 0f)
    }

    private suspend fun animateTo(target: Float) {
        if (offsetPx == target) return
        if (!motionEnabled) {
            offsetPx = target
            return
        }
        animate(
            initialValue = offsetPx,
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        ) { value, _ -> offsetPx = value }
    }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            /* consumed.y < 0 → the list moved up (finger up, content scrolls
             * forward) → hide; consumed.y > 0 → reveal. */
            scrollBy(consumed.y)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState {
    val state = remember { CollapsingHeaderState() }
    val motionEnabled = !reduceMotion() && specialEffectsEnabled()
    SideEffect { state.motionEnabled = motionEnabled }
    return state
}

/**
 * Apply to the container that holds the header content. The container's height
 * shrinks with the offset, the content slides up out of it and fades.
 */
fun Modifier.collapsingHeader(state: CollapsingHeaderState): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        state.heightPx = placeable.height.toFloat()
        val shown = (placeable.height + state.offsetPx).coerceIn(0f, placeable.height.toFloat()).roundToInt()
        layout(placeable.width, shown) {
            placeable.placeRelative(0, shown - placeable.height)
        }
    }
    .clipToBounds()
    .graphicsLayer {
        val h = state.heightPx
        val visible = if (h <= 0f) 1f else (1f + state.offsetPx / h).coerceIn(0f, 1f)
        alpha = visible
        scaleX = 0.985f + visible * 0.015f
        scaleY = 0.98f + visible * 0.02f
    }

/** Convenience: a Column that collapses. */
@Composable
fun CollapsingHeaderColumn(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = PinBomTheme.colors
    Column(
        modifier = modifier
            .drawBehind {
                val h = state.heightPx
                val visible = if (h <= 0f) 1f else (1f + state.offsetPx / h).coerceIn(0f, 1f)
                val baseAlpha = if (colors.isDark) 0.025f else 0.045f
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = baseAlpha * visible),
                            colors.background.copy(alpha = 0f),
                        ),
                    ),
                )
            }
            .collapsingHeader(state),
        content = content,
    )
}
