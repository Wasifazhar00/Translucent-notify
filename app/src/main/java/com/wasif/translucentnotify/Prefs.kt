package com.wasif.translucentnotify
import android.content.Context
object Prefs {
 private const val FILE = "shade_background"
 fun tintPercent(c: Context) = c.getSharedPreferences(FILE,0).getInt("tint_percent",58).coerceIn(0,100)
 fun setTintPercent(c: Context,v:Int) { c.getSharedPreferences(FILE,0).edit().putInt("tint_percent",v.coerceIn(0,100)).apply() }
 fun blurDp(c: Context) = c.getSharedPreferences(FILE,0).getInt("blur_dp",30).coerceIn(0,80)
 fun setBlurDp(c: Context,v:Int) { c.getSharedPreferences(FILE,0).edit().putInt("blur_dp",v.coerceIn(0,80)).apply() }
}