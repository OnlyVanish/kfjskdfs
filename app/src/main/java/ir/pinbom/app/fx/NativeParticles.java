package ir.pinbom.app.fx;

/**
 * JNI binding for the C++ particle core (app/src/main/cpp).
 *
 * The library is optional at runtime: JVM unit tests, unusual ABIs and a failed
 * load all leave {@link #AVAILABLE} false, and the Kotlin wrapper then runs its
 * pure-Kotlin fallback instead of crashing.
 */
public final class NativeParticles {
    /** Floats per particle in the buffer filled by {@link #nRead}. Must match kStride in C++. */
    public static final int STRIDE = 8;

    public static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("pinbom_fx");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private NativeParticles() {}

    public static native long nCreate(int capacity);

    public static native void nDestroy(long handle);

    public static native void nEmit(long handle, int preset, float x, float y, int count, int seed, float unit);

    /** Advances the simulation and returns how many particles are still alive. */
    public static native int nStep(long handle, float dtSeconds, float width, float height);

    /** Fills {@code out} (STRIDE floats per particle) and returns the particle count. */
    public static native int nRead(long handle, float[] out);

    public static native void nClear(long handle);
}
