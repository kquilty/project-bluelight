package com.projectbluelight

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime

// Re-renders the widget once a day just after the perceived-day rollover
// (see CalendarSource.DAY_ROLLOVER_HOUR), so "Tomorrow" becomes "Today" while
// you sleep, not whenever Android happens to feel like refreshing.
class WidgetRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        BluelightWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        // Idempotent — KEEP means calling this on every app open is free.
        // WorkManager periodic timing is approximate, which is fine: the
        // widget also refreshes on app open and on the system's daily cycle.
        fun scheduleDailyAtRollover(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(CalendarSource.DAY_ROLLOVER_HOUR.toInt(), 5)
            if (!next.isAfter(now)) next = next.plusDays(1)

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget-rollover-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(Duration.ofDays(1))
                    .setInitialDelay(Duration.between(now, next))
                    .build(),
            )
        }
    }
}
