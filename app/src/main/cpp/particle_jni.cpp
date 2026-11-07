// JNI shim over pinbom::Engine. The Java side is ir.pinbom.app.fx.NativeParticles.
#include <jni.h>

#include "particle_core.h"

using pinbom::Engine;

namespace {
inline Engine* from(jlong handle) { return reinterpret_cast<Engine*>(handle); }
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nCreate(JNIEnv*, jclass, jint capacity) {
    return reinterpret_cast<jlong>(new Engine(capacity));
}

JNIEXPORT void JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nDestroy(JNIEnv*, jclass, jlong handle) {
    delete from(handle);
}

JNIEXPORT void JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nEmit(JNIEnv*, jclass, jlong handle, jint preset, jfloat x,
                                            jfloat y, jint count, jint seed, jfloat unit) {
    if (Engine* e = from(handle)) e->emit(preset, x, y, count, static_cast<uint32_t>(seed), unit);
}

JNIEXPORT jint JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nStep(JNIEnv*, jclass, jlong handle, jfloat dt, jfloat w,
                                            jfloat h) {
    Engine* e = from(handle);
    return e ? e->step(dt, w, h) : 0;
}

JNIEXPORT jint JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nRead(JNIEnv* env, jclass, jlong handle, jfloatArray out) {
    Engine* e = from(handle);
    if (!e || !out) return 0;
    const jsize room = env->GetArrayLength(out) / pinbom::kStride;
    const int n = std::min<int>(e->alive(), room);
    if (n == 0) return 0;
    // Critical access: no copy, and this loop is short and never calls back into the VM.
    jfloat* dst = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(out, nullptr));
    if (!dst) return 0;
    const int written = e->read(dst, n);
    env->ReleasePrimitiveArrayCritical(out, dst, 0);
    return written;
}

JNIEXPORT void JNICALL
Java_ir_pinbom_app_fx_NativeParticles_nClear(JNIEnv*, jclass, jlong handle) {
    if (Engine* e = from(handle)) e->clear();
}

}  // extern "C"
