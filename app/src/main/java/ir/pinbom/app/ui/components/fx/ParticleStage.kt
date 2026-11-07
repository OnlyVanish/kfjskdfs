package ir.pinbom.app.ui.components.fx

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.pinbom.app.fx.NativeParticles
import ir.pinbom.app.ui.theme.BrandRedDeep
import ir.pinbom.app.ui.theme.BrandRedSoft
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.reduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/* ────────────────────────────────────────────────────────────────────────────
 *  PinBom · particle stage
 *
 *  One simulation, one Canvas, many effects. The physics lives in C++
 *  (app/src/main/cpp/particle_core.h) and runs through JNI; when the library
 *  is not available (JVM unit tests, a failed load) a Kotlin port of the same
 *  rules takes over, so an effect never costs a crash.
 *
 *  The loop only runs while particles are alive — an idle stage costs nothing.
 *  Everything is skipped when the user turned special effects off or asked
 *  Android for reduced motion.
 * ──────────────────────────────────────────────────────────────────────────── */

/** The visual recipes the engine knows. Ids must match `Preset` in particle_core.h. */
enum class FxPreset(internal val id: Int) {
    /** Upward fan of tiny hearts. */
    Heart(0),

    /** Omni-directional glints. */
    Sparkle(1),

    /** Two cannons of tumbling strips. */
    Confetti(2),

    /** Slow rising glow, for ambient backgrounds. */
    Ember(3),

    /** Drifting motes, for ambient backgrounds. */
    Dust(4),

    /** Expanding ring of dots, for taps. */
    Ring(5),
}

private const val STRIDE = PARTICLE_STRIDE

private class NativeBackend(capacity: Int) : ParticleBackend {
    private var handle: Long = NativeParticles.nCreate(capacity)

    override fun emit(preset: Int, x: Float, y: Float, count: Int, seed: Int, unit: Float) {
        if (handle != 0L) NativeParticles.nEmit(handle, preset, x, y, count, seed, unit)
    }

    override fun step(dt: Float, width: Float, height: Float): Int =
        if (handle != 0L) NativeParticles.nStep(handle, dt, width, height) else 0

    override fun read(out: FloatArray): Int =
        if (handle != 0L) NativeParticles.nRead(handle, out) else 0

    override fun clear() {
        if (handle != 0L) NativeParticles.nClear(handle)
    }

    override fun release() {
        val h = handle
        handle = 0L
        if (h != 0L) NativeParticles.nDestroy(h)
    }
}

/**
 * Handle to one stage. Call [burst] from anywhere on the main thread; the stage
 * wakes up, simulates and goes back to sleep on its own.
 */
@Stable
class ParticleController internal constructor(
    internal val backend: ParticleBackend,
    internal val capacity: Int,
) {
    internal val buffer = FloatArray(capacity * STRIDE)
    internal var width = 0f
    internal var height = 0f

    /** Bumped on every burst so the frame loop restarts if it had gone idle. */
    internal var wake by mutableIntStateOf(0)

    /** Bumped on every simulated frame so the Canvas redraws. */
    internal var frame by mutableLongStateOf(0L)
    internal var count = 0

    /** Set by the stage from the user's effects / reduced-motion settings. */
    internal var enabled = true

    /** True when the C++ core is doing the physics (false = Kotlin fallback). */
    val isNative: Boolean get() = backend is NativeBackend

    /**
     * Emits [count] particles at ([x], [y]) in the stage's pixel space.
     * [unit] scales speed and size; it defaults to the stage's smaller side.
     */
    fun burst(
        preset: FxPreset,
        x: Float = width / 2f,
        y: Float = height / 2f,
        count: Int = 24,
        unit: Float = min(width, height),
    ) {
        if (!enabled || width <= 0f || height <= 0f) return
        backend.emit(preset.id, x, y, count.coerceIn(1, capacity), Random.nextInt(), unit.coerceAtLeast(1f))
        wake++
    }

    fun clear() {
        backend.clear()
        count = 0
        frame++
    }
}

/** Creates a controller that releases its native memory with the composition. */
@Composable
fun rememberParticleController(capacity: Int = 192): ParticleController {
    val controller = remember(capacity) {
        val backend: ParticleBackend =
            if (NativeParticles.AVAILABLE) runCatching { NativeBackend(capacity) }.getOrNull() ?: KotlinBackend(capacity)
            else KotlinBackend(capacity)
        ParticleController(backend, capacity)
    }
    DisposableEffect(controller) { onDispose { controller.backend.release() } }
    return controller
}

/** Palette per preset; index comes from the engine (0..4). */
private fun palette(preset: Int, primary: Color, foreground: Color): List<Color> = when (preset) {
    FxPreset.Confetti.id -> listOf(primary, Color(0xFFFFC857), Color(0xFF3DDBD9), Color(0xFFB388FF), Color(0xFFFFFFFF))
    FxPreset.Heart.id -> listOf(primary, BrandRedSoft, BrandRedDeep, primary.copy(alpha = 0.7f), BrandRedSoft)
    FxPreset.Sparkle.id -> listOf(Color(0xFFFFE9A8), primary, Color.White, BrandRedSoft, Color(0xFFFFC857))
    FxPreset.Ring.id -> listOf(primary, primary, BrandRedSoft, primary, BrandRedSoft)
    else -> listOf(primary.copy(alpha = 0.9f), BrandRedSoft, foreground.copy(alpha = 0.5f), primary.copy(alpha = 0.6f), Color.White.copy(alpha = 0.7f))
}

/**
 * Draws a [controller]. Put it above the content inside a Box (it ignores touches,
 * a plain Canvas never consumes pointer input).
 */
@Composable
fun ParticleStage(
    controller: ParticleController,
    modifier: Modifier = Modifier,
) {
    val effects = LocalSpecialEffectsEnabled.current
    val reduce = reduceMotion()
    controller.enabled = effects && !reduce
    val colors = PinBomTheme.colors
    val primary = colors.primary
    val foreground = colors.textPrimary
    val palettes = remember(primary, foreground) {
        List(6) { palette(it, primary, foreground) }
    }
    val heart = remember { unitHeart() }
    val spark = remember { unitSpark() }

    LaunchedEffect(controller, controller.wake) {
        if (controller.wake == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 1f / 60f else (now - last) / 1_000_000_000f
            last = now
            if (controller.width <= 0f) break
            controller.backend.step(dt, controller.width, controller.height)
            controller.count = controller.backend.read(controller.buffer)
            controller.frame++
            if (controller.count == 0) break
        }
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            controller.width = it.width.toFloat()
            controller.height = it.height.toFloat()
        },
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = controller.frame // read: invalidates this draw
        val buffer = controller.buffer
        for (i in 0 until controller.count) {
            drawParticle(buffer, i * STRIDE, palettes, heart, spark)
        }
    }
}

private fun DrawScope.drawParticle(b: FloatArray, o: Int, palettes: List<List<Color>>, heart: Path, spark: Path) {
    val x = b[o]; val y = b[o + 1]; val size = b[o + 2]; val rot = b[o + 3]; val alpha = b[o + 4]
    if (alpha <= 0.01f || size <= 0.1f) return
    val packed = b[o + 5].toInt()
    val preset = (packed shr 3).coerceIn(0, 5)
    val color = palettes[preset][packed and 7].let { it.copy(alpha = it.alpha * alpha) }
    when (b[o + 6].toInt()) {
        1 -> withTransform({
            translate(x, y); rotate(rot, Offset.Zero); scale(size, size, Offset.Zero)
        }) { drawPath(heart, color) }
        2 -> withTransform({
            translate(x, y); rotate(rot, Offset.Zero); scale(size, size, Offset.Zero)
        }) { drawPath(spark, color) }
        3 -> {
            val sx = b[o + 7]
            withTransform({ translate(x, y); rotate(rot, Offset.Zero); scale(sx, 1f, Offset.Zero) }) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-size * 1.4f, -size * 0.55f),
                    size = Size(size * 2.8f, size * 1.1f),
                    cornerRadius = CornerRadius(size * 0.4f),
                )
            }
        }
        else -> {
            // soft glow disc: a faint halo under the core keeps embers readable on any background
            drawCircle(color = color.copy(alpha = color.alpha * 0.22f), radius = size * 2.4f, center = Offset(x, y))
            drawCircle(color = color, radius = size, center = Offset(x, y))
        }
    }
}

/** A heart one unit wide, centred on the origin, so scale() sizes it. */
private fun unitHeart(): Path = Path().apply {
    moveTo(0f, 1f)
    cubicTo(-1.6f, 0.05f, -0.8f, -1f, 0f, -0.35f)
    cubicTo(0.8f, -1f, 1.6f, 0.05f, 0f, 1f)
    close()
}

private fun unitSpark(): Path = Path().apply {
    moveTo(0f, -1.6f)
    lineTo(0.32f, -0.32f); lineTo(1.6f, 0f); lineTo(0.32f, 0.32f)
    lineTo(0f, 1.6f); lineTo(-0.32f, 0.32f); lineTo(-1.6f, 0f); lineTo(-0.32f, -0.32f)
    close()
}

/**
 * Continuous low-density background effect (embers on the auth screen, motes
 * behind headers). It only runs while the screen is visible and stops entirely
 * with special effects off.
 */
@Composable
fun AmbientParticles(
    modifier: Modifier = Modifier,
    preset: FxPreset = FxPreset.Ember,
    perSecond: Float = 3f,
) {
    val controller = rememberParticleController(capacity = 96)
    val owner = LocalLifecycleOwner.current
    val effects = LocalSpecialEffectsEnabled.current
    val reduce = reduceMotion()

    ParticleStage(controller, modifier)

    LaunchedEffect(controller, effects, reduce, perSecond) {
        if (!effects || reduce || perSecond <= 0f) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val w = controller.width
                val h = controller.height
                if (w > 0f && h > 0f) {
                    val fromBottom = preset == FxPreset.Ember
                    controller.burst(
                        preset = preset,
                        x = Random.nextFloat() * w,
                        y = if (fromBottom) h + 10f else Random.nextFloat() * h,
                        count = 1,
                        unit = min(w, h),
                    )
                }
                delay((1000f / perSecond).toLong().coerceAtLeast(60L))
            }
        }
    }
}

/** Convenience used by call sites that only need "burst here when [trigger] changes". */
@Composable
fun BurstOnTrigger(
    trigger: Int,
    controller: ParticleController,
    preset: FxPreset,
    count: Int = 24,
    origin: (ParticleController) -> Offset = { Offset(it.width / 2f, it.height / 2f) },
) {
    val context = LocalContext.current
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val o = origin(controller)
        controller.burst(preset, o.x, o.y, count)
        if (preset == FxPreset.Confetti) ir.pinbom.app.fx.FxHaptics.celebrate(context)
    }
}
