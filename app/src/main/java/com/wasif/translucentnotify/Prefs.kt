package com.wasif.translucentnotify

import android.content.Context
import android.graphics.Color

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("shade", Context.MODE_PRIVATE)
    private fun key(base: String, night: Boolean) = if (night) "${base}_n" else base

    // ---- glass look: one profile for day, one for night (used when auto theme is on) ----

    /** Tint strength, 0..80 percent. */
    fun tint(c: Context, night: Boolean = false) =
        sp(c).getInt(key("tint", night), if (night) 40 else 35).coerceIn(0, 80)
    fun setTint(c: Context, v: Int, night: Boolean = false) =
        sp(c).edit().putInt(key("tint", night), v.coerceIn(0, 80)).apply()

    /** Extra darkening (black layer), 0..70 percent. */
    fun dim(c: Context, night: Boolean = false) =
        sp(c).getInt(key("dim", night), if (night) 10 else 0).coerceIn(0, 70)
    fun setDim(c: Context, v: Int, night: Boolean = false) =
        sp(c).edit().putInt(key("dim", night), v.coerceIn(0, 70)).apply()

    /** Tint colour as hue 0..360, saturation 0..100, brightness 0..100. Default = neutral grey. */
    fun hue(c: Context, night: Boolean = false) = sp(c).getInt(key("hue", night), 0).coerceIn(0, 360)
    fun setHue(c: Context, v: Int, night: Boolean = false) =
        sp(c).edit().putInt(key("hue", night), v.coerceIn(0, 360)).apply()
    fun sat(c: Context, night: Boolean = false) = sp(c).getInt(key("sat", night), 0).coerceIn(0, 100)
    fun setSat(c: Context, v: Int, night: Boolean = false) =
        sp(c).edit().putInt(key("sat", night), v.coerceIn(0, 100)).apply()
    fun bright(c: Context, night: Boolean = false) =
        sp(c).getInt(key("bright", night), if (night) 10 else 50).coerceIn(0, 100)
    fun setBright(c: Context, v: Int, night: Boolean = false) =
        sp(c).edit().putInt(key("bright", night), v.coerceIn(0, 100)).apply()

    /** Opaque RGB of the chosen tint colour. */
    fun rgb(c: Context, night: Boolean = false): Int = Color.HSVToColor(
        floatArrayOf(hue(c, night).toFloat(), sat(c, night) / 100f, bright(c, night) / 100f)
    )

    // ---- shared ----

    /** Blur strength, 0..100 (dp of blur radius). */
    fun blur(c: Context) = sp(c).getInt("blur", 40).coerceIn(0, 100)
    fun setBlur(c: Context, v: Int) = sp(c).edit().putInt("blur", v.coerceIn(0, 100)).apply()

    /** Fade in/out duration in ms, 0..600. Lower = faster, 0 = instant. */
    fun animMs(c: Context) = sp(c).getInt("animMs", 160).coerceIn(0, 600)
    fun setAnimMs(c: Context, v: Int) = sp(c).edit().putInt("animMs", v.coerceIn(0, 600)).apply()

    /** Use the night profile while the phone is in dark mode. */
    fun autoTheme(c: Context) = sp(c).getBoolean("autoTheme", false)
    fun setAutoTheme(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoTheme", v).apply()

    /** Extra darkness added while Quick Settings is open, 0..50 percent. */
    fun dimQs(c: Context) = sp(c).getInt("dimQs", 0).coerceIn(0, 50)
    fun setDimQs(c: Context, v: Int) = sp(c).edit().putInt("dimQs", v.coerceIn(0, 50)).apply()

    // ---- owner mode ----

    /** Keep the detector switched on by itself (needs the owner-mode permission). */
    fun ownerAuto(c: Context) = sp(c).getBoolean("ownerAuto", true)
    fun setOwnerAuto(c: Context, v: Boolean) = sp(c).edit().putBoolean("ownerAuto", v).apply()

    /** Pause the detector while one of the protected apps opens, resume later. */
    fun autoPause(c: Context) = sp(c).getBoolean("autoPause", false)
    fun setAutoPause(c: Context, v: Boolean) = sp(c).edit().putBoolean("autoPause", v).apply()

    /** Minutes until the detector resumes by itself after an automatic pause, 1..60. */
    fun pauseMin(c: Context) = sp(c).getInt("pauseMin", 10).coerceIn(1, 60)
    fun setPauseMin(c: Context, v: Int) = sp(c).edit().putInt("pauseMin", v.coerceIn(1, 60)).apply()

    fun protectedApps(c: Context): Set<String> = sp(c).getStringSet("protected", emptySet()) ?: emptySet()
    fun setProtectedApps(c: Context, s: Set<String>) =
        sp(c).edit().putStringSet("protected", HashSet(s)).apply()
}

/** Colour maths shared by the real layer and the preview. */
object Frost {
    /**
     * Tint colour at [tintAlpha]/255, then black at [dimAlpha]/255 on top, both scaled by progress [p] (0..1),
     * merged into one ARGB colour.
     */
    fun color(rgb: Int, tintAlpha: Int, dimAlpha: Int, p: Float): Int {
        val at = tintAlpha / 255f * p
        val ad = dimAlpha / 255f * p
        val a = 1f - (1f - at) * (1f - ad)
        if (a <= 0.001f) return Color.TRANSPARENT
        val k = at * (1f - ad) / a            // black adds no colour, only darkens
        return Color.argb(
            (a * 255f).toInt().coerceIn(0, 255),
            (Color.red(rgb) * k).toInt().coerceIn(0, 255),
            (Color.green(rgb) * k).toInt().coerceIn(0, 255),
            (Color.blue(rgb) * k).toInt().coerceIn(0, 255)
        )
    }
}
