package ir.pinbom.app.ui.components.fx

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Floats per particle in the shared buffer. Mirrors `kStride` in particle_core.h. */
internal const val PARTICLE_STRIDE = 8

internal interface ParticleBackend {
    fun emit(preset: Int, x: Float, y: Float, count: Int, seed: Int, unit: Float)
    fun step(dt: Float, width: Float, height: Float): Int
    fun read(out: FloatArray): Int
    fun clear()
    fun release()
}

/** Same rules as the C++ core, trimmed: enough to look right when the .so cannot load. */
internal class KotlinBackend(private val cap: Int) : ParticleBackend {
    private val x = FloatArray(cap); private val y = FloatArray(cap)
    private val vx = FloatArray(cap); private val vy = FloatArray(cap)
    private val age = FloatArray(cap); private val life = FloatArray(cap)
    private val size = FloatArray(cap); private val rot = FloatArray(cap)
    private val spin = FloatArray(cap); private val phase = FloatArray(cap)
    private val grav = FloatArray(cap); private val drag = FloatArray(cap)
    private val color = IntArray(cap); private val shape = IntArray(cap); private val kind = IntArray(cap)
    private var alive = 0
    private var recycle = 0
    private var time = 0f
    private var random = Random(1)

    private fun r(lo: Float, hi: Float) = lo + (hi - lo) * random.nextFloat()

    override fun emit(preset: Int, x0: Float, y0: Float, count: Int, seed: Int, unit: Float) {
        random = Random(seed)
        val u = max(unit, 1f)
        for (i in 0 until count) {
            val s = if (alive < cap) alive++ else recycle++ % cap
            kind[s] = preset; age[s] = 0f; phase[s] = r(0f, 6.2831853f)
            x[s] = x0; y[s] = y0; rot[s] = r(0f, 360f); spin[s] = 0f
            color[s] = random.nextInt(5); shape[s] = 0; grav[s] = 0f; drag[s] = 1.5f
            val a: Float
            val sp: Float
            when (preset) {
                0 -> {
                    a = -1.5708f + r(-0.9f, 0.9f); sp = u * r(0.55f, 1.35f)
                    vx[s] = cos(a) * sp; vy[s] = sin(a) * sp
                    life[s] = r(0.7f, 1.15f); size[s] = u * r(0.035f, 0.06f)
                    spin[s] = r(-90f, 90f); shape[s] = 1; grav[s] = u * 0.9f; drag[s] = 2.2f
                }
                1 -> {
                    a = r(0f, 6.2831853f); sp = u * r(0.4f, 1.2f)
                    vx[s] = cos(a) * sp; vy[s] = sin(a) * sp
                    life[s] = r(0.5f, 0.9f); size[s] = u * r(0.025f, 0.055f)
                    shape[s] = if (random.nextFloat() < 0.55f) 2 else 0; drag[s] = 3f
                }
                2 -> {
                    val left = i % 2 == 0
                    a = (if (left) -1.05f else -2.09f) + r(-0.45f, 0.45f); sp = u * r(0.9f, 2.2f)
                    vx[s] = cos(a) * sp; vy[s] = sin(a) * sp
                    life[s] = r(1.6f, 2.8f); size[s] = u * r(0.022f, 0.04f)
                    spin[s] = r(-420f, 420f); shape[s] = 3; grav[s] = u * 1.7f; drag[s] = 1.1f
                }
                3 -> {
                    vx[s] = r(-0.03f, 0.03f) * u; vy[s] = -u * r(0.05f, 0.16f)
                    life[s] = r(3.2f, 6f); size[s] = u * r(0.006f, 0.017f)
                    grav[s] = -u * 0.02f; drag[s] = 0.35f
                }
                4 -> {
                    vx[s] = r(-0.04f, 0.04f) * u; vy[s] = r(-0.03f, 0.03f) * u
                    life[s] = r(4f, 8f); size[s] = u * r(0.004f, 0.011f); drag[s] = 0.2f
                }
                else -> {
                    a = 6.2831853f * i / max(count, 1); sp = u * r(0.9f, 1.1f)
                    vx[s] = cos(a) * sp; vy[s] = sin(a) * sp
                    life[s] = r(0.45f, 0.6f); size[s] = u * 0.02f; drag[s] = 4.5f
                }
            }
        }
    }

    override fun step(dt: Float, width: Float, height: Float): Int {
        val d = dt.coerceIn(0f, 0.05f)
        time += d
        var i = 0
        while (i < alive) {
            age[i] += d
            var dead = age[i] >= life[i]
            if (!dead) {
                val t = time * 1.7f + phase[i]
                val turb = when (kind[i]) { 2 -> 1f; 3 -> 0.9f; 4 -> 0.5f; else -> 0.18f }
                val k = exp(-drag[i] * d)
                vx[i] = (vx[i] + sin(t + y[i] * 0.011f) * 0.6f * turb * d * 60f) * k
                vy[i] = (vy[i] + (grav[i] + cos(t * 0.8f + x[i] * 0.009f) * 0.35f * turb) * d) * k
                x[i] += vx[i] * d; y[i] += vy[i] * d; rot[i] += spin[i] * d
                if (y[i] > height + 80f || x[i] < -120f || x[i] > width + 120f || y[i] < -240f) dead = true
            }
            if (dead) {
                val last = --alive
                if (i != last) {
                    x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                    age[i] = age[last]; life[i] = life[last]; size[i] = size[last]
                    rot[i] = rot[last]; spin[i] = spin[last]; phase[i] = phase[last]
                    grav[i] = grav[last]; drag[i] = drag[last]; color[i] = color[last]
                    shape[i] = shape[last]; kind[i] = kind[last]
                }
            } else i++
        }
        return alive
    }

    override fun read(out: FloatArray): Int {
        val n = min(alive, out.size / PARTICLE_STRIDE)
        for (i in 0 until n) {
            val p = (age[i] / life[i]).coerceIn(0f, 1f)
            val k = kind[i]
            val alpha = if (k == 3 || k == 4) {
                min(min(p / 0.2f, 1f), min((1f - p) / 0.3f, 1f)) * (if (k == 4) 0.55f else 0.8f)
            } else min(1f, (1f - p) * 1.6f)
            val sizeScale = when (k) {
                1 -> 0.4f + 0.9f * sin(p * 3.14159f)
                0 -> 0.6f + 0.5f * sin(min(p * 2f, 1f) * 1.5708f)
                5 -> 1f - 0.5f * p
                else -> 1f
            }
            var scaleX = 1f
            if (shape[i] == 3) {
                val c = cos(rot[i] * 0.0174533f * 1.6f + phase[i])
                scaleX = if (abs(c) < 0.08f) 0.08f else c
            }
            val o = i * PARTICLE_STRIDE
            out[o] = x[i]; out[o + 1] = y[i]; out[o + 2] = size[i] * sizeScale; out[o + 3] = rot[i]
            out[o + 4] = alpha; out[o + 5] = (k * 8 + color[i]).toFloat()
            out[o + 6] = shape[i].toFloat(); out[o + 7] = scaleX
        }
        return n
    }

    override fun clear() { alive = 0 }
    override fun release() { alive = 0 }
}

