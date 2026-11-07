package ir.pinbom.app.fx;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Small, deliberate haptic vocabulary that pairs with the visual effects:
 * a crisp tick for toggles, a springy pop for likes and a rising triple for
 * celebrations. Every call is best-effort — devices without a vibrator, or with
 * haptics turned off, simply do nothing.
 */
public final class FxHaptics {
    private FxHaptics() {}

    public static void tick(Context context) {
        play(context, VibrationEffect.EFFECT_TICK, 12L, 40);
    }

    public static void pop(Context context) {
        play(context, VibrationEffect.EFFECT_CLICK, 18L, 90);
    }

    public static void heavy(Context context) {
        play(context, VibrationEffect.EFFECT_HEAVY_CLICK, 28L, 160);
    }

    /** Rising three-beat pattern: publishing a pin, finishing setup. */
    public static void celebrate(Context context) {
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                VibrationEffect effect = VibrationEffect.startComposition()
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.5f)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 40)
                        .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 60)
                        .compose();
                vibrator.vibrate(effect);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[] {0, 14, 50, 18, 50, 30}, -1));
            } else {
                vibrator.vibrate(new long[] {0, 14, 50, 18, 50, 30}, -1);
            }
        } catch (RuntimeException ignored) {
            // Haptics are decoration; never let them take a screen down.
        }
    }

    private static void play(Context context, int predefined, long fallbackMs, int fallbackAmplitude) {
        Vibrator vibrator = vibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(predefined));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, fallbackAmplitude));
            } else {
                vibrator.vibrate(fallbackMs);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static Vibrator vibrator(Context context) {
        if (context == null) return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
