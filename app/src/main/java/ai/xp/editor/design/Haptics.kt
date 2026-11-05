package ai.xp.editor.design

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Custom haptics — no HapticFeedbackConstants, no Material ripple.
 * Each feel is a hand-written waveform with its own amplitude envelope.
 */
object Haptics {
    @Volatile
    var enabled: Boolean = true

    private var vibrator: Vibrator? = null

    fun init(ctx: Context) {
        if (vibrator != null) return
        vibrator = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun oneShot(ms: Long, amplitude: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
        } catch (_: Throwable) {
        }
    }

    private fun wave(timings: LongArray, amplitudes: IntArray) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Throwable) {
        }
    }

    /** tiny tick for scroll / scrub feedback */
    fun tick() = oneShot(6, 40)

    /** normal tap */
    fun tap() = oneShot(9, 80)

    /** heavy press — delete, snap to grid */
    fun thud() = wave(longArrayOf(0, 12, 22, 16), intArrayOf(0, 190, 0, 120))

    /** something worked */
    fun success() = wave(longArrayOf(0, 10, 46, 16), intArrayOf(0, 110, 0, 220))

    /** something is wrong */
    fun warn() = wave(longArrayOf(0, 16, 40, 16, 40, 14), intArrayOf(0, 200, 0, 150, 0, 200))

    /** clip snapped to a boundary */
    fun snap() = wave(longArrayOf(0, 8, 18, 8), intArrayOf(0, 150, 0, 90))

    /** selection changed */
    fun select() = oneShot(7, 60)
}
