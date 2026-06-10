package com.projectbluelight

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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

private val ACCENT = Color(0xFF4DA3FF)
private val BG = Color(0xFF0E1A2B)
private val FG = Color(0xFFDCE6F2)

class BluelightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the calendar BEFORE drawing, so the tile has real data to show.
        val event = withContext(Dispatchers.IO) { nextVisibleEvent(context) }
        val granted = CalendarSource.hasPermission(context)
        provideContent {
            WidgetContent(event, granted)
        }
    }
}

// The soonest upcoming event that has entered its visibility window.
private fun nextVisibleEvent(context: Context): UpcomingEvent? =
    CalendarSource.upcomingEvents(context).firstOrNull { event ->
        val window = EventWindows.daysFor(context, event.eventId)
        window > 0 && event.daysUntil <= window
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
                Small("Nothing in view yet")
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
