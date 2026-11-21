package com.wasif.translucentnotify

import android.content.Context

object Prefs {
    private const val FILE = "shade_background"

    fun tintPercent(c: Context) =
        c.getSharedPreferences(FILE, 0).getInt("tint_percent", 58).coerceIn(0, 100)

    fun setTintPercent(c: Context, v: Int) {
        c.getSharedPreferences(FILE, 0).edit()
            .putInt("tint_percent", v.coerceIn(0, 100)).apply()
    }

    // 0 = soft grey, 1 = charcoal, 2 = deep black, 3 = blue-grey.
    fun tintColor(c: Context) =
        c.getSharedPreferences(FILE, 0).getInt("tint_color", 1).coerceIn(0, 3)

    fun setTintColor(c: Context, v: Int) {
        c.getSharedPreferences(FILE, 0).edit()
            .putInt("tint_color", v.coerceIn(0, 3)).apply()
    }

    fun blurDp(c: Context) =
        c.getSharedPreferences(FILE, 0).getInt("blur_dp", 24).coerceIn(0, 80)

    fun setBlurDp(c: Context, v: Int) {
        c.getSharedPreferences(FILE, 0).edit()
            .putInt("blur_dp", v.coerceIn(0, 80)).apply()
    }

    fun blurEnabled(c: Context) =
        c.getSharedPreferences(FILE, 0).getBoolean("blur_enabled", false)

    fun setBlurEnabled(c: Context, enabled: Boolean) {
        c.getSharedPreferences(FILE, 0).edit()
            .putBoolean("blur_enabled", enabled).apply()
    }
}