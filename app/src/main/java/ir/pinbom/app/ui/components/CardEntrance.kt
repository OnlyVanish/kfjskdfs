package ir.pinbom.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import ir.pinbom.app.ui.theme.reduceMotion
import ir.pinbom.app.ui.theme.specialEffectsEnabled
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · card entrance
 *  The first pins of a fresh wall rise into place one after another (a soft
 *  stagger), which is the moment people actually look at. It is deliberately
 *  limited: only the first few pins, and each pin only once per process, so
 *  infinite scrolling, scrolling back up and refreshing the same pins never
 *  start a swarm of animations.
 * ──────────────────────────────────────────────────────────────────────────── */

/** Pins that already played their entrance in this process. */
object CardEntranceSeen {
    private val seen = HashSet<Long>()

    /** True the first time an id is asked about, false ever after. */
    @Synchronized
    fun markFirst(id: Long): Boolean = seen.add(id)
}

/** How many pins at the top of a wall are allowed to animate in. */
const val ENTRANCE_MAX_ITEMS = 10

/**
 * Fade + rise + tiny scale, driven by one spring and read only inside the graphics
 * layer — no recomposition while it runs. [delayMillis] staggers neighbours.
 */
@Composable
fun Modifier.cardEntrance(enabled: Boolean, delayMillis: Int = 0): Modifier {
    if (!enabled || reduceMotion() || !specialEffectsEnabled()) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(
            1f,
            spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow),
        )
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 26.dp.toPx()
        val s = 0.96f + 0.04f * p
        scaleX = s
        scaleY = s
    }
}
