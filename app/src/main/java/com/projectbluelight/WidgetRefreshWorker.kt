package com.projectbluelight

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime

// Keeps the widget telling the truth without the app being opened:
//  - daily just after the 4am rollover, so "Tomorrow" becomes "Today" in your sleep
//  - hourly, so a finished event drops off within the hour
//  - the moment the calendar itself changes (event added, moved, cancelled)
class WidgetRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        BluelightWidget.refreshAll(applicationContext)
        // Content triggers are one-shot — re-arm after every run so the next
        // calendar change wakes us again.
        scheduleCalendarTrigger(applicationContext)
        return Result.success()
    }

    companion object {
        // Idempotent — KEEP means calling this on every app open is free.
        // WorkManager periodic timing is approximate, which is fine: the
        // widget also refreshes on app open and on calendar changes.
        fun scheduleAll(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(CalendarSource.DAY_ROLLOVER_HOUR.toInt(), 5)
            if (!next.isAfter(now)) next = next.plusDays(1)

            val manager = WorkManager.getInstance(context)
            manager.enqueueUniquePeriodicWork(
                "widget-rollover-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(Duration.ofDays(1))
                    .setInitialDelay(Duration.between(now, next))
                    .build(),
            )
            manager.enqueueUniquePeriodicWork(
                "widget-hourly-refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(Duration.ofHours(1)).build(),
            )
            scheduleCalendarTrigger(context)
        }

        // One-shot job that fires when the calendar provider reports a change,
        // batched a little so an editing burst becomes one refresh.
        private fun scheduleCalendarTrigger(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "calendar-changed-refresh",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                            .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                            .setTriggerContentMaxDelay(Duration.ofMinutes(2))
                            .build()
                    )
                    .build(),
            )
        }
    }
}
