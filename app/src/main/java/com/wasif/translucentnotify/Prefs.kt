package com.wasif.translucentnotify

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("shade", Context.MODE_PRIVATE)

    /** Grey tint strength, 0..80 percent. */
    fun tint(c: Context) = sp(c).getInt("tint", 35).coerceIn(0, 80)
    fun setTint(c: Context, v: Int) = sp(c).edit().putInt("tint", v.coerceIn(0, 80)).apply()

    /** Blur strength, 0..100 (dp of blur radius). */
    fun blur(c: Context) = sp(c).getInt("blur", 40).coerceIn(0, 100)
    fun setBlur(c: Context, v: Int) = sp(c).edit().putInt("blur", v.coerceIn(0, 100)).apply()
}
