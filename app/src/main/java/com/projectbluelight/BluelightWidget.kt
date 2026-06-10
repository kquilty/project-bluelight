package com.projectbluelight

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val ACCENT = Color(0xFF4DA3FF)
private val BG = Color(0xFF0E1A2B)
private val FG = Color(0xFFDCE6F2)

private data class UpcomingEvent(val title: String, val daysUntil: Long)

class BluelightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the calendar BEFORE drawing, so the tile has real data to show.
        val event = loadNextEvent(context)
        val granted = hasPermission(context)
        provideContent {
            WidgetContent(event, granted)
        }
    }
}

private fun hasPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

// Finds the single soonest upcoming event across all the phone's calendars.
// Uses the Instances table so recurring events (Christmas, birthdays) expand
// into their actual next date instead of their original one.
private suspend fun loadNextEvent(context: Context): UpcomingEvent? =
    withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null

        val now = System.currentTimeMillis()
        val oneYearOut = now + 365L * 24 * 60 * 60 * 1000

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, oneYearOut)
        val uri = builder.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
        )

        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { cursor ->
            val today = LocalDate.now()
            while (cursor.moveToNext()) {
                val title = cursor.getString(0) ?: continue
                val begin = cursor.getLong(1)
                val date = Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalDate()
                val days = ChronoUnit.DAYS.between(today, date)
                if (days >= 0) {
                    return@use UpcomingEvent(title, days)
                }
            }
            null
        }
    }

@Composable
private fun WidgetContent(event: UpcomingEvent?, granted: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BG)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()), // tap the tile to open the app
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        when {
            !granted -> {
                Big("Tap to set up")
                Small("grant calendar access")
            }
            event == null -> {
                Small("Nothing coming up")
            }
            else -> {
                val (big, small) = when (event.daysUntil) {
                    0L -> "Today" to event.title
                    1L -> "Tomorrow" to event.title
                    else -> "${event.daysUntil} days" to "until ${event.title}"
                }
                Big(big)
                Small(small)
            }
        }
    }
}

@Composable
private fun Big(text: String) {
    Text(
        text = text,
        style = TextStyle(color = ColorProvider(ACCENT), fontSize = 28.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Small(text: String) {
    Text(
        text = text,
        style = TextStyle(color = ColorProvider(FG), fontSize = 14.sp),
    )
}
