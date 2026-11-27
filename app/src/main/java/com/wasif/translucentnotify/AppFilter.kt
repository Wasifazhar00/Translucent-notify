package com.wasif.translucentnotify

import android.content.Context

/** Which apps the glass should leave completely alone. */
object AppFilter {
    private fun sp(c: Context) = c.getSharedPreferences("shade", Context.MODE_PRIVATE)

    /** Apps over which the glass is never started (they get no pause, nothing). */
    fun skipApps(c: Context): Set<String> = sp(c).getStringSet("skip", emptySet()) ?: emptySet()
    fun setSkipApps(c: Context, s: Set<String>) =
        sp(c).edit().putStringSet("skip", HashSet(s)).apply()

    /** true = glass only over the home screen; every other app is never touched. */
    fun homeOnly(c: Context) = sp(c).getBoolean("homeOnly", false)
    fun setHomeOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("homeOnly", v).apply()
}
