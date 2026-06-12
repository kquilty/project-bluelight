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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
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
private val GLOW = Color(0xFF9CCBFF) // today's rows — arrival reads warmer
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
        val initialScale = EventWindows.widgetFontScale(context)
        val initialPassed = withContext(Dispatchers.IO) { CalendarSource.passedWatchedLastWeek(context) }
        provideContent {
            // updateAll() on a live session only RECOMPOSES — it does not re-run
            // provideGlance — so data captured above goes stale the moment the
            // user promotes an event. Re-read inside composition, keyed on the
            // widget state that refreshAll() bumps.
            val state = currentState<Preferences>()
            var events by remember { mutableStateOf(initial) }
            var scale by remember { mutableStateOf(initialScale) }
            var passed by remember { mutableStateOf(initialPassed) }
            var handled by remember { mutableStateOf(EventWindows.handledIds(context, initial)) }
            LaunchedEffect(state) {
                val fresh = withContext(Dispatchers.IO) { visibleEvents(context) }
                events = fresh
                handled = EventWindows.handledIds(context, fresh)
                scale = EventWindows.widgetFontScale(context)
                passed = withContext(Dispatchers.IO) { CalendarSource.passedWatchedLastWeek(context) }
            }
            WidgetContent(
                events,
                CalendarSource.hasPermission(context),
                Voice.line(events, passedLastWeek = passed, handled = handled),
                scale,
            )
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
        EventWindows.isVisible(EventWindows.effectiveDaysFor(context, event), event.daysUntil)
    }

@Composable
private fun WidgetContent(events: List<UpcomingEvent>, granted: Boolean, voice: String, scale: Float) {
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
            Big("Tap to set up", scale)
            Small("grant calendar access", scale)
            return@Column
        }
        // The voice speaks only when it has something the list can't say;
        // otherwise the tile is just the list, which is the point.
        if (voice.isNotBlank()) {
            Text(
                text = voice,
                style = TextStyle(color = solid(DIM), fontSize = (12 * scale).sp, fontStyle = FontStyle.Italic),
            )
            Spacer(GlanceModifier.height(8.dp))
        }
        if (events.isEmpty()) {
            Small("tap to choose what shows here", scale)
        } else {
            // Evenings belong to tomorrow: after 9pm tomorrow's events lead
            // and today's survivors step into the background.
            // The list wraps its content (no fillMaxSize) so the empty space
            // below the last row belongs to the tile — and a tap there opens
            // the app instead of vanishing into the list view.
            val tonight = Voice.isTonight()
            LazyColumn {
                items(Voice.tonightOrder(events), itemId = { it.eventId }) { event ->
                    EventRow(event, scale, dimmed = tonight && event.daysUntil == 0L)
                }
            }
        }
    }
}

// Rows know who they are: tapping one opens the app with that event's sheet up.
private val OPEN_EVENT = ActionParameters.Key<Long>(MainActivity.EXTRA_OPEN_EVENT)

@Composable
private fun EventRow(event: UpcomingEvent, scale: Float, dimmed: Boolean = false) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity<MainActivity>(actionParametersOf(OPEN_EVENT to event.eventId))),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        val countdown = when (event.daysUntil) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> "${event.daysUntil} days"
        }
        // Wide enough for the longest label ("Tomorrow") so nothing wraps
        // mid-word — the column grows with the text size.
        Text(
            text = countdown,
            style = TextStyle(
                color = solid(if (dimmed) DIM else if (event.daysUntil == 0L) GLOW else ACCENT),
                fontSize = (14 * scale).sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
            modifier = GlanceModifier.width((82 * scale).dp),
        )
        Text(
            text = event.title,
            style = TextStyle(color = solid(if (dimmed) DIM else FG), fontSize = (14 * scale).sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        // A timed event today carries its hour after the title, a shade back.
        if (event.daysUntil == 0L && event.time != null) {
            Text(
                text = " @ ${Voice.clock(event.time)}",
                style = TextStyle(color = solid(DIM), fontSize = (13 * scale).sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Big(text: String, scale: Float) {
    Text(
        text = text,
        style = TextStyle(color = solid(ACCENT), fontSize = (28 * scale).sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Small(text: String, scale: Float) {
    Text(
        text = text,
        style = TextStyle(color = solid(FG), fontSize = (14 * scale).sp),
    )
}
