package com.projectbluelight

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

// The settings screen: every upcoming event, each with a slider for how far
// ahead it should appear on the widget.
class MainActivity : ComponentActivity() {

    private var settingsChanged = false

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                loadEvents()
            } else {
                showStatus(
                    "Calendar access is needed to show upcoming events.\n" +
                        "Reopen the app, or enable it in Settings, to try again."
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<RecyclerView>(R.id.events).layoutManager = LinearLayoutManager(this)

        if (CalendarSource.hasPermission(this)) {
            loadEvents()
        } else {
            requestPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    // Refresh the widget once on the way out, instead of after every slider nudge.
    override fun onStop() {
        super.onStop()
        if (settingsChanged) {
            settingsChanged = false
            refreshWidget()
        }
    }

    private fun loadEvents() {
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarSource.upcomingEvents(this@MainActivity)
            }
            showStatus(
                if (events.isEmpty()) "Nothing on your calendar for the next year."
                else "Slide to choose how far ahead each event appears on the widget."
            )
            findViewById<RecyclerView>(R.id.events).adapter =
                EventAdapter(events) { eventId, days ->
                    EventWindows.setDays(this@MainActivity, eventId, days)
                    settingsChanged = true
                }
            refreshWidget()
        }
    }

    private fun showStatus(text: String) {
        findViewById<TextView>(R.id.status).text = text
    }

    private fun refreshWidget() {
        lifecycleScope.launch {
            BluelightWidget().updateAll(applicationContext)
        }
    }
}

private class EventAdapter(
    private val events: List<UpcomingEvent>,
    private val onWindowChanged: (eventId: Long, days: Int) -> Unit,
) : RecyclerView.Adapter<EventAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.event_title)
        val date: TextView = view.findViewById(R.id.event_date)
        val slider: SeekBar = view.findViewById(R.id.window_slider)
        val windowLabel: TextView = view.findViewById(R.id.window_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.event_row, parent, false))

    override fun getItemCount() = events.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val event = events[position]
        holder.title.text = event.title
        holder.date.text = dateLabel(event)

        // Detach the old listener before setting progress — recycled rows
        // would otherwise write one event's setting onto another event.
        holder.slider.setOnSeekBarChangeListener(null)
        holder.slider.max = EventWindows.PRESETS.size - 1
        val days = EventWindows.daysFor(holder.itemView.context, event.eventId)
        holder.slider.progress = presetIndex(days)
        holder.windowLabel.text = windowLabel(days)

        holder.slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val chosen = EventWindows.PRESETS[progress]
                holder.windowLabel.text = windowLabel(chosen)
                if (fromUser) onWindowChanged(event.eventId, chosen)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
    }
}

private fun dateLabel(event: UpcomingEvent): String {
    val date = event.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    return when (event.daysUntil) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "$date · in ${event.daysUntil} days"
    }
}

private fun windowLabel(days: Int): String = when (days) {
    0 -> "Hidden from the widget"
    1 -> "Shows 1 day ahead"
    else -> "Shows $days days ahead"
}

// Saved values always come from PRESETS today, but settle for the nearest
// stop below if that ever stops being true.
private fun presetIndex(days: Int): Int {
    val exact = EventWindows.PRESETS.indexOf(days)
    return if (exact >= 0) exact else EventWindows.PRESETS.indexOfLast { it <= days }.coerceAtLeast(0)
}
