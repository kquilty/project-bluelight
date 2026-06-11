package com.projectbluelight

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// Android talks to this class; it just hands back our widget.
class BluelightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BluelightWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Make sure the 4am rollover refresh survives even if the app
        // itself is never opened again.
        WidgetRefreshWorker.scheduleDailyAtRollover(context)
    }
}
