package ir.pinbom.app

import ir.pinbom.app.ui.components.fx.KotlinBackend
import ir.pinbom.app.ui.components.fx.PARTICLE_STRIDE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure-Kotlin fallback must obey the same contract as the C++ core. */
class ParticleBackendTest {
    private val presets = 0..5

    @Test
    fun everyPresetSimulatesAndStaysInRange() {
        for (preset in presets) {
            val engine = KotlinBackend(128)
            engine.emit(preset, 200f, 300f, 40, 1234 + preset, 400f)
            val out = FloatArray(128 * PARTICLE_STRIDE)
            assertEquals("preset $preset spawned", 40, engine.read(out))
            for (i in 0 until 40) {
                val alpha = out[i * PARTICLE_STRIDE + 4]
                assertTrue("alpha in range", alpha in 0f..1f)
                assertTrue("finite", out[i * PARTICLE_STRIDE].isFinite())
            }
            var frames = 0
            while (engine.step(1f / 60f, 400f, 800f) > 0 && frames < 60 * 20) frames++
            // ambient presets live long; every other preset must be gone well before the cap
            if (preset != 3 && preset != 4) assertEquals("preset $preset finished", 0, engine.step(0f, 400f, 800f))
        }
    }

    @Test
    fun capacityIsRespected() {
        val engine = KotlinBackend(10)
        engine.emit(2, 0f, 0f, 100, 7, 300f)
        assertEquals(10, engine.step(0f, 1000f, 1000f))
    }

    @Test
    fun aLongFrameDoesNotTeleportParticles() {
        val engine = KotlinBackend(4)
        engine.emit(1, 100f, 100f, 4, 9, 100f)
        assertEquals(4, engine.step(0.2f, 1000f, 1000f))
    }
}
