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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
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
        val events = withContext(Dispatchers.IO) { visibleEvents(context) }
        val granted = CalendarSource.hasPermission(context)
        provideContent {
            WidgetContent(events, granted)
        }
    }
}

// Every upcoming event that has entered its visibility window, soonest first.
// This is the whole product: the widget is your calendar with each event
// held back until it's close enough to matter.
private fun visibleEvents(context: Context): List<UpcomingEvent> =
    CalendarSource.upcomingEvents(context).filter { event ->
        val window = EventWindows.daysFor(context, event.eventId)
        window > 0 && event.daysUntil <= window
    }

@Composable
private fun WidgetContent(events: List<UpcomingEvent>, granted: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BG)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()), // tap the tile to open the app
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        when {
            !granted -> {
                Big("Tap to set up")
                Small("grant calendar access")
            }
            events.isEmpty() -> {
                Small("Nothing in view yet")
            }
            else -> {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(events, itemId = { it.eventId }) { event ->
                        EventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: UpcomingEvent) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity<MainActivity>()), // list rows swallow the tile's tap
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        val countdown = when (event.daysUntil) {
            0L -> "Today"
            1L -> "1 day"
            else -> "${event.daysUntil} days"
        }
        Text(
            text = countdown,
            style = TextStyle(color = ColorProvider(ACCENT), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.width(64.dp),
        )
        Text(
            text = event.title,
            style = TextStyle(color = ColorProvider(FG), fontSize = 14.sp),
            maxLines = 1,
        )
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
