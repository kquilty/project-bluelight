@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.projectbluelight

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.projectbluelight.ui.Abyss
import com.projectbluelight.ui.Accent
import com.projectbluelight.ui.AccentGlow
import com.projectbluelight.ui.BluelightTheme
import com.projectbluelight.ui.DeepSpace
import com.projectbluelight.ui.Ink
import com.projectbluelight.ui.InkDim
import com.projectbluelight.ui.InkFaint
import com.projectbluelight.ui.OnAccent
import com.projectbluelight.ui.Surface1
import com.projectbluelight.ui.Surface2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WidgetRefreshWorker.scheduleDailyAtRollover(this)
        setContent {
            BluelightTheme {
                BluelightApp(saveWindow = { eventId, days ->
                    EventWindows.setDays(this, eventId, days)
                    lifecycleScope.launch { BluelightWidget().updateAll(applicationContext) }
                })
            }
        }
    }
}

// ---------- App shell: permission gate + resume-aware reload ----------

@Composable
private fun BluelightApp(saveWindow: (Long, Int) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) deniedOnce = true
    }

    // Re-check permission and re-read the calendar whenever the user comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = CalendarSource.hasPermission(context)
                resumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, Abyss))),
    ) {
        // A faint aurora behind the header, so the screen reads as light in darkness.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Accent.copy(alpha = 0.10f), Color.Transparent),
                    center = center.copy(x = size.width * 0.2f, y = 0f),
                    radius = size.width * 0.9f,
                ),
                center = center.copy(x = size.width * 0.2f, y = 0f),
                radius = size.width * 0.9f,
            )
        }
        if (granted) {
            EventsScreen(resumeTick = resumeTick, saveWindow = saveWindow)
        } else {
            OnboardingScreen(
                deniedOnce = deniedOnce,
                onConnect = { launcher.launch(Manifest.permission.READ_CALENDAR) },
            )
        }
    }
}

// ---------- Onboarding: one glowing invitation, one button ----------

@Composable
private fun OnboardingScreen(deniedOnce: Boolean, onConnect: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlowOrb(modifier = Modifier.size(180.dp))
        Spacer(Modifier.height(40.dp))
        BrandTitle()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Your calendar already knows what's coming.\nBluelight surfaces each thing only when it starts to matter.",
            style = MaterialTheme.typography.bodyLarge,
            color = InkDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onConnect,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) {
            Text("Connect your calendar", style = MaterialTheme.typography.titleMedium)
        }
        if (deniedOnce) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            }) {
                Text("Permission blocked? Open system settings", color = InkDim)
            }
        }
    }
}

@Composable
private fun GlowOrb(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    Canvas(modifier = modifier.scale(pulse)) {
        val r = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(Accent.copy(alpha = 0.35f), Color.Transparent), center = center, radius = r),
            radius = r,
        )
        drawCircle(color = Accent.copy(alpha = 0.30f), radius = r * 0.62f, style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = Accent, radius = r * 0.30f)
        drawCircle(color = AccentGlow.copy(alpha = 0.9f), radius = r * 0.16f)
    }
}

// ---------- The main screen: three sections of attention ----------

@Composable
private fun EventsScreen(resumeTick: Int, saveWindow: (Long, Int) -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<UpcomingEvent>?>(null) }
    val windows = remember { mutableStateMapOf<Long, Int>() }
    var selected by remember { mutableStateOf<UpcomingEvent?>(null) }

    LaunchedEffect(resumeTick) {
        val loaded = withContext(Dispatchers.IO) { CalendarSource.upcomingEvents(context) }
        loaded.forEach { windows[it.eventId] = EventWindows.daysFor(context, it.eventId) }
        events = loaded
    }

    val loaded = events
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    val inView = loaded.filter { (windows[it.eventId] ?: 0) > 0 && it.daysUntil <= (windows[it.eventId] ?: 0) }
    val waiting = loaded.filter { (windows[it.eventId] ?: 0) > 0 && it.daysUntil > (windows[it.eventId] ?: 0) }
    val resting = loaded.filter { (windows[it.eventId] ?: 0) == 0 }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 48.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp)) {
                BrandTitle()
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${inView.size} in view · ${waiting.size} waiting · ${resting.size} resting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkDim,
                )
            }
        }

        if (loaded.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Nothing on your calendar for the next year.\nAdd events there and they'll appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkDim,
                    modifier = Modifier.padding(vertical = 48.dp),
                )
            }
            return@LazyColumn
        }

        item(key = "hdr-view") { SectionHeader("On your widget", inView.size) }
        if (inView.isEmpty()) {
            item(key = "view-empty") {
                Text(
                    text = "Nothing needs you yet. Promote something below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkFaint,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(inView, key = { it.eventId }) { event ->
            EventCard(event, windows[event.eventId] ?: 0, Section.InView, Modifier.animateItem()) { selected = event }
        }

        if (waiting.isNotEmpty()) {
            item(key = "hdr-wait") { SectionHeader("Waiting their turn", waiting.size) }
            items(waiting, key = { it.eventId }) { event ->
                EventCard(event, windows[event.eventId] ?: 0, Section.Waiting, Modifier.animateItem()) { selected = event }
            }
        }

        if (resting.isNotEmpty()) {
            item(key = "hdr-rest") { SectionHeader("Everything else", resting.size) }
            items(resting, key = { it.eventId }) { event ->
                EventCard(event, 0, Section.Resting, Modifier.animateItem()) { selected = event }
            }
        }
    }

    selected?.let { event ->
        LeadTimeSheet(
            event = event,
            currentDays = windows[event.eventId] ?: 0,
            onSelect = { days ->
                windows[event.eventId] = days
                saveWindow(event.eventId, days)
            },
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun BrandTitle() {
    Text(
        text = "Bluelight",
        style = MaterialTheme.typography.displaySmall.merge(
            TextStyle(brush = Brush.horizontalGradient(listOf(AccentGlow, Accent)))
        ),
    )
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = title.uppercase() + "  ·  $count",
        style = MaterialTheme.typography.labelSmall,
        color = InkFaint,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}

private enum class Section { InView, Waiting, Resting }

@Composable
private fun EventCard(
    event: UpcomingEvent,
    window: Int,
    section: Section,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (section == Section.InView) Surface2 else Surface1.copy(alpha = 0.65f),
        border = if (section == Section.InView) BorderStroke(1.dp, Accent.copy(alpha = 0.25f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (section == Section.Resting) InkDim else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = cardDateLine(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
            }
            Spacer(Modifier.width(12.dp))
            when (section) {
                Section.InView -> Text(
                    text = countdown(event.daysUntil),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (event.daysUntil == 0L) AccentGlow else Accent,
                )
                Section.Waiting -> Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "surfaces in ${event.daysUntil - window}d",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkDim,
                    )
                    Text(
                        text = "${window}d ahead",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint,
                    )
                }
                Section.Resting -> Surface(
                    shape = CircleShape,
                    color = Surface2,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, contentDescription = "Give it a window", tint = InkDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------- The whole product in one sheet: "when does this start mattering?" ----------

private data class LeadTime(val days: Int, val label: String)

private val LEAD_TIMES = listOf(
    LeadTime(0, "Hidden"),
    LeadTime(1, "1 day"),
    LeadTime(3, "3 days"),
    LeadTime(7, "1 week"),
    LeadTime(14, "2 weeks"),
    LeadTime(30, "1 month"),
    LeadTime(90, "3 months"),
    LeadTime(365, "1 year"),
)

@Composable
private fun LeadTimeSheet(
    event: UpcomingEvent,
    currentDays: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Old or hand-set values that aren't a preset still deserve a chip.
    val choices = remember(currentDays) {
        if (LEAD_TIMES.any { it.days == currentDays }) LEAD_TIMES
        else (LEAD_TIMES + LeadTime(currentDays, "$currentDays days")).sortedBy { it.days }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(sheetDateLine(event), style = MaterialTheme.typography.bodyMedium, color = InkDim)
            Spacer(Modifier.height(28.dp))
            Text("STARTS MATTERING", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { lead ->
                    FilterChip(
                        selected = lead.days == currentDays,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(lead.days)
                        },
                        label = { Text(lead.label) },
                        shape = RoundedCornerShape(12.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Surface2,
                            labelColor = InkDim,
                            selectedContainerColor = Accent,
                            selectedLabelColor = OnAccent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            AnimatedContent(targetState = currentDays, label = "preview") { days ->
                Text(
                    text = previewLine(event, days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days > 0) AccentGlow else InkFaint,
                )
            }
        }
    }
}

// ---------- Wording ----------

private val cardDate = DateTimeFormatter.ofPattern("EEE, MMM d")
private val sheetDate = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val shortDate = DateTimeFormatter.ofPattern("MMM d")

private fun countdown(days: Long): String = when (days) {
    0L -> "Today"
    1L -> "1 day"
    else -> "$days days"
}

private fun cardDateLine(event: UpcomingEvent): String = when (event.daysUntil) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> "${event.date.format(cardDate)} · in ${event.daysUntil} days"
}

private fun sheetDateLine(event: UpcomingEvent): String = when (event.daysUntil) {
    0L -> "${event.date.format(sheetDate)} · today"
    1L -> "${event.date.format(sheetDate)} · tomorrow"
    else -> "${event.date.format(sheetDate)} · in ${event.daysUntil} days"
}

private fun previewLine(event: UpcomingEvent, days: Int): String = when {
    days == 0 -> "Stays off your widget."
    event.daysUntil == 0L -> "On your widget now — it's today."
    event.daysUntil <= days -> "On your widget now — ${event.daysUntil} days to go."
    else -> "Will surface ${event.date.minusDays(days.toLong()).format(shortDate)} — $days days ahead."
}
