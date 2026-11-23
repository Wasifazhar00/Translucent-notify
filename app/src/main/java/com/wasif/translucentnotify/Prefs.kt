package com.wasif.translucentnotify

import android.content.Context
import android.graphics.Color

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("shade", Context.MODE_PRIVATE)

    /** Tint strength, 0..80 percent. */
    fun tint(c: Context) = sp(c).getInt("tint", 35).coerceIn(0, 80)
    fun setTint(c: Context, v: Int) = sp(c).edit().putInt("tint", v.coerceIn(0, 80)).apply()

    /** Extra darkening (black layer), 0..70 percent. */
    fun dim(c: Context) = sp(c).getInt("dim", 0).coerceIn(0, 70)
    fun setDim(c: Context, v: Int) = sp(c).edit().putInt("dim", v.coerceIn(0, 70)).apply()

    /** Tint colour as hue 0..360, saturation 0..100, brightness 0..100. Default = neutral grey. */
    fun hue(c: Context) = sp(c).getInt("hue", 0).coerceIn(0, 360)
    fun setHue(c: Context, v: Int) = sp(c).edit().putInt("hue", v.coerceIn(0, 360)).apply()
    fun sat(c: Context) = sp(c).getInt("sat", 0).coerceIn(0, 100)
    fun setSat(c: Context, v: Int) = sp(c).edit().putInt("sat", v.coerceIn(0, 100)).apply()
    fun bright(c: Context) = sp(c).getInt("bright", 50).coerceIn(0, 100)
    fun setBright(c: Context, v: Int) = sp(c).edit().putInt("bright", v.coerceIn(0, 100)).apply()

    /** Opaque RGB of the chosen tint colour. */
    fun rgb(c: Context): Int =
        Color.HSVToColor(floatArrayOf(hue(c).toFloat(), sat(c) / 100f, bright(c) / 100f))

    /** Blur strength, 0..100 (dp of blur radius). */
    fun blur(c: Context) = sp(c).getInt("blur", 40).coerceIn(0, 100)
    fun setBlur(c: Context, v: Int) = sp(c).edit().putInt("blur", v.coerceIn(0, 100)).apply()

    /** Fade in/out duration in ms, 0..600. Lower = faster, 0 = instant. */
    fun animMs(c: Context) = sp(c).getInt("animMs", 160).coerceIn(0, 600)
    fun setAnimMs(c: Context, v: Int) = sp(c).edit().putInt("animMs", v.coerceIn(0, 600)).apply()
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
