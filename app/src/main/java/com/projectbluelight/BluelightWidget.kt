package com.projectbluelight

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ACCENT = Color(0xFF4DA3FF)
private val BG = Color(0xFF0E1A2B)
private val FG = Color(0xFFDCE6F2)
private val DIM = Color(0xFF93A7C0)

// ColorProvider(Color) is public API, but the IDE's RestrictedApi check flags
// it anyway: it matches the generated ColorProviderKt facade, where @RestrictTo
// siblings live next to the public overload. One suppression here instead of
// one per call site. (CLI lint agrees the calls are fine.)
@SuppressLint("RestrictedApi")
private fun solid(color: Color): ColorProvider = ColorProvider(color)

class BluelightWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read the calendar BEFORE composing, so the first paint has real data.
        val initial = withContext(Dispatchers.IO) { visibleEvents(context) }
        provideContent {
            // updateAll() on a live session only RECOMPOSES — it does not re-run
            // provideGlance — so data captured above goes stale the moment the
            // user promotes an event. Re-read inside composition, keyed on the
            // widget state that refreshAll() bumps.
            val state = currentState<Preferences>()
            var events by remember { mutableStateOf(initial) }
            LaunchedEffect(state) {
                events = withContext(Dispatchers.IO) { visibleEvents(context) }
            }
            WidgetContent(events, CalendarSource.hasPermission(context), Voice.line(events))
        }
    }

    companion object {
        private val REFRESH = intPreferencesKey("refresh")

        // The one way to refresh every tile. Bumping the counter changes the
        // composition key even when a session is alive; plain updateAll() alone
        // would recompose with stale data.
        suspend fun refreshAll(context: Context) {
            val widget = BluelightWidget()
            GlanceAppWidgetManager(context).getGlanceIds(BluelightWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[REFRESH] = (prefs[REFRESH] ?: 0) + 1
                }
            }
            widget.updateAll(context)
        }
    }
}

// Every upcoming event that has entered its visibility window, soonest first.
// This is the whole product: the widget is your calendar with each event
// held back until it's close enough to matter. The window is the explicit
// choice when one exists, otherwise the user's default for new events.
private fun visibleEvents(context: Context): List<UpcomingEvent> =
    CalendarSource.upcomingEvents(context).filter { event ->
        val window = EventWindows.effectiveDaysFor(context, event)
        window > 0 && event.daysUntil <= window
    }

@Composable
private fun WidgetContent(events: List<UpcomingEvent>, granted: Boolean, voice: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(BG)
            .cornerRadius(24.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()), // tap the tile to open the app
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        if (!granted) {
            Big("Tap to set up")
            Small("grant calendar access")
            return@Column
        }
        // The voice leads. The list is reference; this line is the point.
        Text(
            text = voice,
            style = TextStyle(color = solid(DIM), fontSize = 12.sp, fontStyle = FontStyle.Italic),
        )
        if (events.isEmpty()) {
            Spacer(GlanceModifier.height(6.dp))
            Small("tap to choose what shows here")
        } else {
            Spacer(GlanceModifier.height(8.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(events, itemId = { it.eventId }) { event ->
                    EventRow(event)
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
        // A timed event today shows its hour — "Today / at 4" — on two lines so
        // the countdown column keeps its width.
        val countdown = when {
            event.daysUntil == 0L && event.time != null -> "Today\nat ${Voice.clock(event.time)}"
            event.daysUntil == 0L -> "Today"
            event.daysUntil == 1L -> "Tomorrow"
            else -> "${event.daysUntil} days"
        }
        // Wide enough for the longest label ("Tomorrow") so nothing wraps
        // mid-word; the only two-line countdown is the deliberate "Today / at 4".
        Text(
            text = countdown,
            style = TextStyle(color = solid(ACCENT), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
            modifier = GlanceModifier.width(82.dp),
        )
        Text(
            text = event.title,
            style = TextStyle(color = solid(FG), fontSize = 14.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun Big(text: String) {
    Text(
        text = text,
        style = TextStyle(color = solid(ACCENT), fontSize = 28.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Small(text: String) {
    Text(
        text = text,
        style = TextStyle(color = solid(FG), fontSize = 14.sp),
    )
}
