package ir.pinbom.app.ui.components.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.pinbom.app.ui.theme.BrandRedDeep
import ir.pinbom.app.ui.theme.BrandRedSoft
import ir.pinbom.app.ui.theme.LocalSpecialEffectsEnabled
import ir.pinbom.app.ui.theme.PinBomTheme
import ir.pinbom.app.ui.theme.reduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Visual language for a one-shot lightweight burst. */
enum class ParticleBurstKind { Heart, Sparkle, Confetti }
enum class ParticleOrigin { Center, Top }

private data class ParticleSpec(
    val angle: Float,
    val speed: Float,
    val radius: Float,
    val rotation: Float,
    val spin: Float,
    val life: Float,
    val drift: Float,
    val colorIndex: Int,
    val fast: Boolean,
)

/**
 * One clock per burst, bounded to 60 simple Canvas particles. Trigger-specific
 * random specs keep repeated celebrations organic without allocating per frame.
 */
@Composable
fun PinBomParticles(
    trigger: Int,
    modifier: Modifier = Modifier,
    kind: ParticleBurstKind = ParticleBurstKind.Sparkle,
    maxParticles: Int = 24,
    origin: ParticleOrigin = ParticleOrigin.Center,
) {
    val effectsEnabled = LocalSpecialEffectsEnabled.current
    val reduce = reduceMotion()
    val colors = PinBomTheme.colors
    val count = maxParticles.coerceIn(1, 60)
    val progress = remember { Animatable(1f) }
    var lastTrigger by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var destinationVisible by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    // A fresh seed for each trigger; the particle list is created once, not per frame.
    val seed = remember(trigger) { Random.nextInt() }
    val particles = remember(seed, count, kind) {
        val random = Random(seed)
        List(count) {
            val fast = random.nextBoolean()
            val angle = when (kind) {
                ParticleBurstKind.Heart -> (-PI / 2.0 + random.nextDouble(-0.88, 0.88)).toFloat()
                ParticleBurstKind.Confetti -> (-PI / 2.0 + random.nextDouble(-1.05, 1.05)).toFloat()
                ParticleBurstKind.Sparkle -> (random.nextDouble(0.0, 2.0 * PI)).toFloat()
            }
            ParticleSpec(
                angle = angle,
                speed = random.nextFloat().let { value ->
                    when (kind) {
                        ParticleBurstKind.Heart -> 0.16f + value * 0.20f
                        ParticleBurstKind.Confetti -> if (fast) 0.24f + value * 0.30f else 0.10f + value * 0.16f
                        ParticleBurstKind.Sparkle -> 0.12f + value * 0.26f
                    }
                },
                radius = 0.006f + random.nextFloat() * 0.0055f,
                rotation = random.nextFloat() * 360f,
                spin = (70f + random.nextFloat() * 250f) * if (random.nextBoolean()) 1f else -1f,
                life = 0.78f + random.nextFloat() * 0.22f,
                drift = random.nextFloat() * (2f * PI.toFloat()),
                colorIndex = random.nextInt(5),
                fast = fast,
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            destinationVisible = when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_DESTROY -> false
                else -> destinationVisible
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(trigger, effectsEnabled, reduce, destinationVisible) {
        val isNewTrigger = trigger != lastTrigger
        lastTrigger = trigger
        if (trigger <= 0 || !effectsEnabled || reduce || !destinationVisible) {
            progress.snapTo(1f)
        } else if (isNewTrigger) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(durationMillis = 720))
        }
    }

    if (effectsEnabled && !reduce && destinationVisible) {
        Canvas(modifier = modifier) {
            val rawProgress = progress.value.coerceIn(0f, 1f)
            if (trigger <= 0 || rawProgress >= 1f) return@Canvas
            val center = Offset(
                x = size.width / 2f,
                y = if (origin == ParticleOrigin.Top) size.height * 0.07f else size.height / 2f,
            )
            val base = size.minDimension.coerceAtLeast(1f)
            particles.forEach { particle ->
                val local = rawProgress / particle.life
                if (local >= 1f) return@forEach
                val p = local.coerceIn(0f, 1f)
                val eased = 1f - (1f - p) * (1f - p) * (1f - p)
                val travel = base * particle.speed * eased
                val gravity = if (kind == ParticleBurstKind.Confetti) {
                    base * (0.06f + 0.26f * p * p) * if (particle.fast) 1.2f else 0.75f
                } else {
                    base * 0.035f * p * p
                }
                val horizontalDrift = if (kind == ParticleBurstKind.Confetti) {
                    sin(p * (2f * PI.toFloat()) + particle.drift) * base * 0.035f
                } else 0f
                val point = Offset(
                    x = center.x + cos(particle.angle) * travel + horizontalDrift,
                    y = center.y + sin(particle.angle) * travel + gravity,
                )
                val alpha = ((1f - p) * (0.62f + (particle.colorIndex % 3) * 0.10f)).coerceIn(0f, 0.85f)
                val color = particleColor(particle.colorIndex, colors.primary, colors.textPrimary).copy(alpha = alpha)
                val radius = base * particle.radius
                when (kind) {
                    ParticleBurstKind.Heart -> {
                        val popScale = 0.72f + 0.32f * sin(p * PI.toFloat()).coerceAtLeast(0f)
                        rotate(degrees = particle.rotation + particle.spin * eased * 0.25f, pivot = point) {
                            scale(popScale, popScale, pivot = point) {
                                drawMiniHeart(point, radius * 2.7f, color)
                            }
                        }
                    }
                    ParticleBurstKind.Confetti -> {
                        val rotation = particle.rotation + particle.spin * p
                        // A horizontal scale approximates rotation around the strip's vertical axis.
                        val axisScale = cos(rotation * (PI.toFloat() / 180f)).let { if (abs(it) < 0.07f) 0.07f else it }
                        scale(scaleX = axisScale, scaleY = 1f, pivot = point) {
                            rotate(degrees = rotation, pivot = point) {
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(point.x - radius * 1.8f, point.y - radius * 0.7f),
                                    size = Size(radius * 3.6f, radius * 1.4f),
                                    cornerRadius = CornerRadius(radius),
                                )
                            }
                        }
                    }
                    ParticleBurstKind.Sparkle -> {
                        drawCircle(color = color, radius = radius * (0.8f + 0.25f * eased), center = point)
                        if (particle.colorIndex % 2 == 0) drawSpark(point, radius * 2.7f, color)
                    }
                }
            }
        }
    }
}

private fun particleColor(index: Int, primary: Color, foreground: Color): Color = when (index % 5) {
    0 -> primary
    1 -> BrandRedSoft
    2 -> BrandRedDeep
    3 -> foreground
    else -> primary.copy(alpha = 0.66f)
}

private fun DrawScope.drawMiniHeart(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + radius)
        cubicTo(center.x - radius * 1.6f, center.y + radius * 0.05f, center.x - radius * 0.8f, center.y - radius, center.x, center.y - radius * 0.35f)
        cubicTo(center.x + radius * 0.8f, center.y - radius, center.x + radius * 1.6f, center.y + radius * 0.05f, center.x, center.y + radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSpark(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.20f, center.y - radius * 0.20f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * 0.20f, center.y + radius * 0.20f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.20f, center.y + radius * 0.20f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * 0.20f, center.y - radius * 0.20f)
        close()
    }
    drawPath(path, color)
}
