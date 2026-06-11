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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WidgetRefreshWorker.scheduleAll(this)
        setContent {
            BluelightTheme {
                BluelightApp(
                    saveWindows = { eventIds, days ->
                        eventIds.forEach { id -> EventWindows.setDays(this, id, days) }
                        lifecycleScope.launch { BluelightWidget.refreshAll(applicationContext) }
                    },
                    refreshWidget = {
                        lifecycleScope.launch { BluelightWidget.refreshAll(applicationContext) }
                    },
                )
            }
        }
    }
}

// ---------- App shell: permission gate + resume-aware reload ----------

@Composable
private fun BluelightApp(saveWindows: (List<Long>, Int) -> Unit, refreshWidget: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    var deniedOnce by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) deniedOnce = true
    }

    // The widget can't watch the calendar or permission grants on its own —
    // opening the app is the natural moment to bring it back in sync.
    LaunchedEffect(granted) {
        if (granted) BluelightWidget.refreshAll(context.applicationContext)
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
            EventsScreen(resumeTick = resumeTick, saveWindows = saveWindows, refreshWidget = refreshWidget)
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
private fun EventsScreen(resumeTick: Int, saveWindows: (List<Long>, Int) -> Unit, refreshWidget: () -> Unit) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<UpcomingEvent>?>(null) }
    val windows = remember { mutableStateMapOf<Long, Int>() }
    var selected by remember { mutableStateOf<UpcomingEvent?>(null) }
    var bulkOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var settingsTick by remember { mutableIntStateOf(0) }
    var defaultDays by remember { mutableIntStateOf(EventWindows.DEFAULT_DAYS) }
    var defaultChosen by remember { mutableStateOf(true) }

    LaunchedEffect(resumeTick, settingsTick) {
        val loaded = withContext(Dispatchers.IO) { CalendarSource.upcomingEvents(context) }
        // Only events the user has actually decided on enter the map — so a
        // missing key means "never asked", and an explicit 0 means "chose Hidden".
        // Keyed by the merged event's primary ID; reads span every copy.
        loaded.forEach { e ->
            EventWindows.explicitDaysFor(context, e)?.let { windows[e.eventId] = it }
        }
        defaultDays = EventWindows.defaultDays(context)
        defaultChosen = EventWindows.isDefaultChosen(context)
        events = loaded
    }

    val loaded = events
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Accent)
        }
        return
    }

    // The window an event lives by: explicit choice first, then the default.
    fun eff(e: UpcomingEvent): Int =
        windows[e.eventId] ?: EventWindows.resolveDefault(defaultDays, e.title)

    var hintSeen by remember { mutableStateOf(EventWindows.isScrubHintSeen(context)) }
    val scrubCommit: (UpcomingEvent, Int) -> Unit = { e, days ->
        windows[e.eventId] = days
        saveWindows(e.allIds, days)
        if (!hintSeen) {
            EventWindows.setScrubHintSeen(context)
            hintSeen = true
        }
    }

    val inView = loaded.filter { EventWindows.isVisible(eff(it), it.daysUntil) }
    val waiting = loaded.filter { eff(it) != 0 && !EventWindows.isVisible(eff(it), it.daysUntil) }
    val resting = loaded.filter { eff(it) == 0 }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Item indices of the section headers — must mirror the LazyColumn layout
    // below (header, optional nudge, view header, view rows or empty row, …).
    val nudgeRows = if (!defaultChosen) 1 else 0
    val idxWait = 1 + nudgeRows + 1 + (if (inView.isEmpty()) 1 else inView.size)
    val idxRest = idxWait + (if (waiting.isNotEmpty()) 1 + waiting.size else 0)

    Box(Modifier.fillMaxSize()) {

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 110.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandTitle()
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = InkDim,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Voice.line(inView),
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = InkDim,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${inView.size} in view · ${waiting.size} waiting · ${resting.size} resting",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
                if (!hintSeen) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tip: slide any event sideways to set its window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Accent.copy(alpha = 0.7f),
                    )
                }
            }
        }

        // One-time nudge: until a default is chosen, new calendar events stay
        // invisible — the quiet failure mode. Choosing anything dismisses it.
        if (!defaultChosen) {
            item(key = "default-nudge") {
                Surface(
                    onClick = { settingsOpen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Surface2,
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.35f)),
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text("New events start hidden", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Pick a default window and they'll surface on their own.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim,
                        )
                    }
                }
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
            EventCard(event, eff(event), Section.InView, Modifier.animateItem(), { scrubCommit(event, it) }) { selected = event }
        }

        if (waiting.isNotEmpty()) {
            item(key = "hdr-wait") { SectionHeader("Waiting their turn", waiting.size) }
            items(waiting, key = { it.eventId }) { event ->
                EventCard(event, eff(event), Section.Waiting, Modifier.animateItem(), { scrubCommit(event, it) }) { selected = event }
            }
        }

        if (resting.isNotEmpty()) {
            item(key = "hdr-rest") { SectionHeader("Everything else", resting.size) }
            if (resting.size > 1) {
                item(key = "bulk-all") {
                    TextButton(
                        onClick = { bulkOpen = true },
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        Text(
                            "Give all ${resting.size} a window at once →",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Accent,
                        )
                    }
                }
            }
            items(resting, key = { it.eventId }) { event ->
                EventCard(event, 0, Section.Resting, Modifier.animateItem(), { scrubCommit(event, it) }) { selected = event }
            }
        }
    }

    // Always-reachable rail: where you are, where you can jump, and the gear —
    // no scrolling back to the top required.
    if (loaded.isNotEmpty()) {
        // Which section the eye is on. A short last section never reaches the
        // top of the screen, so hitting the end of the list counts as being there.
        val firstVisible = listState.firstVisibleItemIndex
        val atEnd = !listState.canScrollForward
        val currentSection = when {
            resting.isNotEmpty() && (firstVisible >= idxRest || atEnd) -> 2
            waiting.isNotEmpty() && (firstVisible >= idxWait || atEnd) -> 1
            else -> 0
        }
        SectionRail(
            inViewCount = inView.size,
            waitingCount = waiting.size,
            restingCount = resting.size,
            current = currentSection,
            onJump = { section ->
                scope.launch {
                    listState.animateScrollToItem(
                        when (section) {
                            1 -> idxWait
                            2 -> idxRest
                            else -> 0
                        }
                    )
                }
            },
            onSettings = { settingsOpen = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }

    }

    selected?.let { event ->
        LeadTimeSheet(
            event = event,
            currentDays = eff(event),
            onSelect = { days ->
                windows[event.eventId] = days
                saveWindows(event.allIds, days)
            },
            onDismiss = { selected = null },
        )
    }

    if (settingsOpen) {
        SettingsSheet(
            currentDefault = defaultDays,
            onPickDefault = { days ->
                EventWindows.setDefaultDays(context, days)
                defaultDays = days
                defaultChosen = true
                refreshWidget()
            },
            onCalendarsChanged = {
                settingsTick++
                refreshWidget()
            },
            refreshWidget = refreshWidget,
            onDismiss = { settingsOpen = false },
        )
    }

    if (bulkOpen) {
        BulkPromoteSheet(
            events = resting,
            hiddenByChoice = resting.mapNotNull { e ->
                e.eventId.takeIf { windows[it] == 0 }
            }.toSet(),
            onApply = { ids, days ->
                ids.forEach { windows[it] = days }
                saveWindows(ids, days)
            },
            onDismiss = { bulkOpen = false },
        )
    }
}

// ---------- The section rail: the app's only chrome ----------

@Composable
private fun SectionRail(
    inViewCount: Int,
    waitingCount: Int,
    restingCount: Int,
    current: Int,
    onJump: (Int) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Surface1,
        border = BorderStroke(1.dp, Accent.copy(alpha = 0.18f)),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailChip("Widget", inViewCount, current == 0) { onJump(0) }
            if (waitingCount > 0) RailChip("Waiting", waitingCount, current == 1) { onJump(1) }
            if (restingCount > 0) RailChip("Resting", restingCount, current == 2) { onJump(2) }
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = InkDim,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RailChip(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) Accent else InkDim,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = if (active) AccentGlow else InkFaint,
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

// The scrub gesture's stops are LEAD_TIMES left to right — left means less.
// A custom value (a 21-day window) starts from its nearest preset.
private fun nearestStopIndex(window: Int): Int {
    LEAD_TIMES.indexOfFirst { it.days == window }.let { if (it >= 0) return it }
    return LEAD_TIMES.withIndex()
        .filter { it.value.days > 0 }
        .minByOrNull { abs(it.value.days - window) }!!
        .index
}

// What a scrubbed-to window means for THIS event, while the finger is down.
private fun scrubPreview(event: UpcomingEvent, days: Int): String = when {
    days == 0 -> "stays hidden"
    EventWindows.isVisible(days, event.daysUntil) -> "on your widget now"
    days == EventWindows.DAY_OF -> "surfaces ${event.date.format(shortDate)} — day of"
    else -> "surfaces ${event.date.minusDays(days.toLong()).format(shortDate)}"
}

@Composable
private fun EventCard(
    event: UpcomingEvent,
    window: Int,
    section: Section,
    modifier: Modifier = Modifier,
    onScrub: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var widthPx by remember { mutableStateOf(1f) }
    var scrubIndex by remember(event.eventId) { mutableStateOf<Int?>(null) }
    var startIndex by remember { mutableIntStateOf(0) }
    var dragAccum by remember { mutableStateOf(0f) }
    val scrubbing = scrubIndex != null

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Drag sideways to scrub the window across the preset stops; the
            // vertical list keeps ambiguous gestures, commit happens on lift.
            .pointerInput(event.eventId, window) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragAccum = 0f
                        startIndex = nearestStopIndex(window)
                        scrubIndex = startIndex
                    },
                    onDragEnd = {
                        scrubIndex?.let { i ->
                            if (LEAD_TIMES[i].days != window) onScrub(LEAD_TIMES[i].days)
                        }
                        scrubIndex = null
                    },
                    onDragCancel = { scrubIndex = null },
                ) { change, amount ->
                    change.consume()
                    dragAccum += amount
                    val stopWidth = widthPx / LEAD_TIMES.size
                    val next = (startIndex + dragAccum / stopWidth).roundToInt()
                        .coerceIn(0, LEAD_TIMES.lastIndex)
                    if (next != scrubIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubIndex = next
                    }
                }
            },
        shape = RoundedCornerShape(20.dp),
        color = if (scrubbing) Surface2 else if (section == Section.InView) Surface2 else Surface1.copy(alpha = 0.65f),
        border = when {
            scrubbing -> BorderStroke(1.dp, Accent.copy(alpha = 0.6f))
            section == Section.InView -> BorderStroke(1.dp, Accent.copy(alpha = 0.25f))
            else -> null
        },
    ) {
        Box {
            if (scrubbing) {
                val frac by animateFloatAsState(
                    targetValue = (scrubIndex!! + 1f) / LEAD_TIMES.size,
                    label = "scrubTrack",
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(frac)
                        .height(3.dp)
                        .background(Accent.copy(alpha = 0.55f)),
                )
            }
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (section == Section.Resting && !scrubbing) InkDim else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = scrubIndex?.let { scrubPreview(event, LEAD_TIMES[it].days) }
                        ?: cardDateLine(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (scrubbing) AccentGlow else InkFaint,
                )
            }
            Spacer(Modifier.width(12.dp))
            val scrubbedTo = scrubIndex
            when {
                scrubbedTo != null -> Text(
                    text = LEAD_TIMES[scrubbedTo].label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Accent,
                    maxLines = 1,
                )
                section == Section.InView -> Text(
                    text = countdown(event.daysUntil),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (event.daysUntil == 0L) AccentGlow else Accent,
                )
                section == Section.Waiting -> Column(horizontalAlignment = Alignment.End) {
                    val dayOf = window == EventWindows.DAY_OF
                    Text(
                        text = "surfaces in ${if (dayOf) event.daysUntil else event.daysUntil - window}d",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkDim,
                    )
                    Text(
                        text = if (dayOf) "day of" else "${window}d ahead",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint,
                    )
                }
                else -> Surface(
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
}

// ---------- The whole product in one sheet: "when does this start mattering?" ----------

private data class LeadTime(val days: Int, val label: String)

private val LEAD_TIMES = listOf(
    LeadTime(0, "Hidden"),
    LeadTime(EventWindows.DAY_OF, "Day of"),
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
    val isPreset = LEAD_TIMES.any { it.days == currentDays }
    // A hand-set value that isn't a preset arrives with the stepper already
    // open and showing it — the stepper, not an extra chip, owns odd numbers.
    var customOpen by remember(event.eventId) { mutableStateOf(!isPreset) }

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
            if (event.allIds.size > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "On ${event.allIds.size} calendars — one window covers them all.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
            }
            Spacer(Modifier.height(28.dp))
            Text("STARTS MATTERING", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LEAD_TIMES.forEach { lead ->
                    FilterChip(
                        selected = lead.days == currentDays,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            customOpen = false
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
                FilterChip(
                    selected = !isPreset,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!customOpen) {
                            customOpen = true
                            // Coming from Hidden or Day of, give the stepper
                            // somewhere to start.
                            if (currentDays <= 0) onSelect(10)
                        }
                    },
                    label = { Text("Custom…") },
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
            if (customOpen) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepperButton("−", enabled = currentDays > 1) { onSelect(currentDays - 1) }
                    Text(
                        text = if (currentDays == 1) "1 day" else "$currentDays days",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(96.dp),
                    )
                    StepperButton("+", enabled = currentDays < 365) { onSelect(currentDays + 1) }
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

// ---------- Settings: the only two dials the product has ----------

private val DEFAULT_CHOICES = listOf(
    LeadTime(EventWindows.DEFAULT_DAYS, "Hidden"),
    LeadTime(EventWindows.SMART, "Smart"),
    LeadTime(EventWindows.DAY_OF, "Day of"),
    LeadTime(1, "1 day"),
    LeadTime(3, "3 days"),
    LeadTime(7, "1 week"),
    LeadTime(14, "2 weeks"),
    LeadTime(30, "1 month"),
)

private fun defaultCaption(days: Int): String = when (days) {
    EventWindows.DEFAULT_DAYS -> "New events stay hidden until you promote them."
    EventWindows.SMART -> "By kind: birthdays surface 2 weeks out, trips 3 days, exams a week — everything else the day before."
    EventWindows.DAY_OF -> "Every new event surfaces only on the day it happens."
    else -> "Every new event surfaces ${DEFAULT_CHOICES.first { it.days == days }.label} ahead. Your per-event choices always win."
}

// Each chip renders at the size it sets — the label is its own preview.
private data class FontChoice(val scale: Float, val label: String, val previewSp: Int)

private val FONT_CHOICES = listOf(
    FontChoice(0.85f, "Small", 12),
    FontChoice(1f, "Default", 14),
    FontChoice(1.15f, "Large", 16),
    FontChoice(1.3f, "Huge", 18),
)

@Composable
private fun SettingsSheet(
    currentDefault: Int,
    onPickDefault: (Int) -> Unit,
    onCalendarsChanged: () -> Unit,
    refreshWidget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    var muted by remember { mutableStateOf(setOf<Long>()) }
    var fontScale by remember { mutableStateOf(EventWindows.widgetFontScale(context)) }
    LaunchedEffect(Unit) {
        calendars = withContext(Dispatchers.IO) { CalendarSource.calendars(context) }
        muted = EventWindows.mutedCalendars(context)
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
            Text("How Bluelight behaves", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(24.dp))

            Text("NEW EVENTS START", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DEFAULT_CHOICES.forEach { lead ->
                    FilterChip(
                        selected = lead.days == currentDefault,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPickDefault(lead.days)
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
            Spacer(Modifier.height(10.dp))
            AnimatedContent(targetState = currentDefault, label = "defaultCaption") { days ->
                Text(
                    text = defaultCaption(days),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (days == EventWindows.DEFAULT_DAYS) InkFaint else AccentGlow,
                )
            }

            Spacer(Modifier.height(28.dp))
            Text("WIDGET TEXT", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_CHOICES.forEach { choice ->
                    FilterChip(
                        selected = choice.scale == fontScale,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            EventWindows.setWidgetFontScale(context, choice.scale)
                            fontScale = choice.scale
                            refreshWidget()
                        },
                        label = { Text(choice.label, fontSize = choice.previewSp.sp) },
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

            if (calendars.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("CALENDARS", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Untick a calendar to keep it off Bluelight entirely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(calendars, key = { it.id }) { cal ->
                        val watched = cal.id !in muted
                        fun toggle() {
                            EventWindows.setCalendarMuted(context, cal.id, watched)
                            muted = EventWindows.mutedCalendars(context)
                            onCalendarsChanged()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { toggle() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = cal.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (watched) Ink else InkFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (cal.account.isNotBlank() && cal.account != cal.name) {
                                    Text(
                                        text = cal.account,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkFaint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Checkbox(
                                checked = watched,
                                onCheckedChange = { toggle() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Accent,
                                    checkmarkColor = OnAccent,
                                    uncheckedColor = InkFaint,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Bulk promotion: sweep "Everything else" in with one window ----------

@Composable
private fun BulkPromoteSheet(
    events: List<UpcomingEvent>,
    hiddenByChoice: Set<Long>,
    onApply: (List<Long>, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var days by remember { mutableIntStateOf(1) }
    // Everything starts included — except events the user explicitly hid,
    // which start unticked so the sweep respects past choices. A tap flips
    // either way; unticking never records a new "Hidden".
    val excluded = remember {
        mutableStateMapOf<Long, Boolean>().apply { hiddenByChoice.forEach { put(it, true) } }
    }
    val chosen = events.filter { excluded[it.eventId] != true }

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
            Text("Everything else, sorted", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "One window for everything you haven't sorted. Anything you chose to hide starts unticked.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim,
            )
            Spacer(Modifier.height(24.dp))
            Text("START MATTERING", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LEAD_TIMES.filter { it.days != 0 }.forEach { lead ->
                    FilterChip(
                        selected = lead.days == days,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            days = lead.days
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
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(events, key = { it.eventId }) { event ->
                    val included = excluded[event.eventId] != true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { excluded[event.eventId] = included }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (included) Ink else InkFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (event.eventId in hiddenByChoice)
                                    "${cardDateLine(event)}  ·  hidden by you"
                                else cardDateLine(event),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkFaint,
                            )
                        }
                        Checkbox(
                            checked = included,
                            onCheckedChange = { excluded[event.eventId] = included },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Accent,
                                checkmarkColor = OnAccent,
                                uncheckedColor = InkFaint,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Every underlying ID, so duplicates on other calendars
                    // get the window too.
                    onApply(chosen.flatMap { it.allIds }, days)
                    onDismiss()
                },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                val label = LEAD_TIMES.first { it.days == days }.label
                Text(
                    text = when {
                        chosen.isEmpty() -> "Nothing ticked"
                        days == EventWindows.DAY_OF -> "Promote ${chosen.size} · day of"
                        else -> "Promote ${chosen.size} · $label ahead"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// One step of the custom window: a quiet round button, disabled at the rails
// (1 day at the bottom — Hidden is the chips' job — and a year at the top).
@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        shape = CircleShape,
        color = Surface2,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) Accent else InkFaint,
            )
        }
    }
}

// ---------- Wording ----------

private val cardDate = DateTimeFormatter.ofPattern("EEE, MMM d")
private val sheetDate = DateTimeFormatter.ofPattern("EEEE, MMMM d")
private val shortDate = DateTimeFormatter.ofPattern("MMM d")

private fun countdown(days: Long): String = when (days) {
    0L -> "Today"
    1L -> "Tomorrow"
    else -> "$days days"
}

// "Today at 4" for timed events; all-day events are just "Today".
private fun todayLabel(event: UpcomingEvent): String =
    event.time?.let { "Today at ${Voice.clock(it)}" } ?: "Today"

private fun cardDateLine(event: UpcomingEvent): String = when (event.daysUntil) {
    0L -> todayLabel(event)
    1L -> "Tomorrow"
    else -> "${event.date.format(cardDate)} · in ${event.daysUntil} days"
}

private fun sheetDateLine(event: UpcomingEvent): String = when (event.daysUntil) {
    0L -> "${event.date.format(sheetDate)} · ${todayLabel(event).lowercase()}"
    1L -> "${event.date.format(sheetDate)} · tomorrow"
    else -> "${event.date.format(sheetDate)} · in ${event.daysUntil} days"
}

private fun previewLine(event: UpcomingEvent, days: Int): String = when {
    days == 0 -> "Stays off your widget."
    days == EventWindows.DAY_OF && event.daysUntil > 0L ->
        "Will surface ${event.date.format(shortDate)} — the day of."
    event.daysUntil == 0L -> "On your widget now — it's today."
    event.daysUntil <= days ->
        if (event.daysUntil == 1L) "On your widget now — 1 day to go."
        else "On your widget now — ${event.daysUntil} days to go."
    else -> "Will surface ${event.date.minusDays(days.toLong()).format(shortDate)} — " +
        if (days == 1) "1 day ahead." else "$days days ahead."
}
