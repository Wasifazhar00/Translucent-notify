package com.wasif.translucentnotify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Optional home-screen widget: shows ON/OFF and switches the detector with one tap. */
class GlassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = views(context)
        appWidgetIds.forEach { manager.updateAppWidget(it, views) }
    }

    companion object {
        /** Redraw all placed widgets (called when the detector starts or stops). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, GlassWidget::class.java))
            if (ids.isEmpty()) return
            val views = views(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun views(context: Context): RemoteViews {
            val on = ShadeBlurService.instance != null
            val tap = PendingIntent.getActivity(
                context, 0,
                Intent(context, ToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return RemoteViews(context.packageName, R.layout.widget_glass).apply {
                setTextViewText(R.id.state, if (on) "ON - tap to turn off" else "OFF - tap to turn on")
                setOnClickPendingIntent(R.id.root, tap)
            }
        }
    }
}
