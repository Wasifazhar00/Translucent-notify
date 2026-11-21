package com.wasif.translucentnotify

import android.content.Context

object Prefs {
    const val MIN_OPACITY = 20
    const val MAX_OPACITY = 100

    fun opacity(ctx: Context): Int =
        ctx.getSharedPreferences("panel", Context.MODE_PRIVATE)
            .getInt("opacity", MAX_OPACITY)
            .coerceIn(MIN_OPACITY, MAX_OPACITY)

    fun setOpacity(ctx: Context, value: Int) {
        ctx.getSharedPreferences("panel", Context.MODE_PRIVATE)
            .edit().putInt("opacity", value.coerceIn(MIN_OPACITY, MAX_OPACITY)).apply()
    }
}
