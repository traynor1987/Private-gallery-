package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val recoveryMint = Color(0xff79e2ba)
private val pulseCoral = Color(0xffff9ca6)
private val sleepLavender = Color(0xffbcb2ff)
private val activityCyan = Color(0xff81d7f7)
private val timeAmber = Color(0xffffce83)
private val gigOrange = Color(0xffffa775)
private val dashboardInk = Color(0xff111b27)
private val dashboardText = Color(0xfff0f5fa)
private val dashboardSubtle = Color(0xffb5c5d1)
private val dashboardGrid = Color(0xff334452)

@Composable
internal fun VisualHealthTimeDesign(module: VisualModule, navigator: VisualNavigator) {
    val page = navigator.section
    val section = module.sections[page]
    Text(
        section.headline,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    if (module.id == "health") {
        HealthSection(page, section, navigator)
    } else {
        TimeSection(page, section, navigator)
    }
}

@Composable
private fun HealthSection(page: Int, section: VisualSection, navigator: VisualNavigator) {
    when (page) {
        0 -> {
            Text("THURSDAY  ·  14 MAY", style = MaterialTheme.typography.labelMedium)
            HealthSplit(
                first = { HealthDial("Nova Recovery", 74, "Readiness", recoveryMint) },
                second = { HealthDial("Nova Strain", 42, "Activity load", activityCyan) },
                minimumWidth = 286.dp,
            )
            HealthSplit(
                first = { HealthPulse("Heart rate", "78 bpm", listOf(64, 69, 67, 75, 70, 81, 76, 78), pulseCoral) },
                second = { HealthSleep() },
            )
            HealthMovement()
            HealthLinks(listOf("Vitals" to 1, "Trends" to 6, "Sources" to 10), navigator)
        }
        1 -> {
            HealthPulse("Heart rate", "78 bpm", listOf(64, 69, 67, 75, 70, 81, 76, 78), pulseCoral)
            HealthSplit(
                first = { HealthPulse("Overnight HRV", "49 ms", listOf(32, 35, 33, 44, 42, 51, 46, 49), recoveryMint) },
                second = { HealthPulse("Resting heart rate", "59 bpm", listOf(66, 62, 64, 63, 60, 61, 60, 59), activityCyan) },
            )
        }
        2 -> {
            HealthSleep()
            HealthDial("Nova Recovery", 74, "Sample readiness", recoveryMint)
        }
        3 -> {
            HealthMovement()
            HealthPulse("Training load", "42 / 100", listOf(14, 24, 36, 32, 40, 42), activityCyan)
        }
        4 -> {
            HealthPulse("Weight history", "71.4 kg", listOf(720, 719, 721, 717, 715, 714), activityCyan)
            HealthSplit(
                first = { DashboardValue("Current sample", "71.4 kg", activityCyan) },
                second = { DashboardValue("Goal", "70.0 kg", recoveryMint) },
            )
        }
        5 -> HealthMood()
        in 6..8 -> {
            HealthLinks(listOf("Daily" to 6, "Weekly" to 7, "Monthly" to 8), navigator)
            section.trend?.let { SampleTrend(it, if (page == 6) pulseCoral else activityCyan, bars = page != 6) }
        }
        9 -> Text("MAY 2026", style = MaterialTheme.typography.labelLarge)
        10 -> HealthWatchPreview()
        11 -> Text("0 active health sources", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val icons = when (page) {
        0 -> listOf(Icons.Default.DirectionsWalk, Icons.Default.Bedtime, Icons.Default.Favorite)
        1 -> listOf(Icons.Default.MonitorHeart, Icons.Default.Timeline, Icons.Default.FavoriteBorder)
        2 -> listOf(Icons.Default.Bedtime, Icons.Default.Refresh, Icons.Default.Event)
        3 -> listOf(Icons.Default.DirectionsWalk, Icons.Default.FitnessCenter, Icons.Default.Timeline)
        4 -> listOf(Icons.Default.MonitorWeight, Icons.Default.Flag)
        5 -> listOf(Icons.Default.Mood, Icons.Default.EmojiEmotions, Icons.Default.Add)
        10 -> listOf(Icons.Default.Watch, Icons.Default.FavoriteBorder, Icons.Default.Watch, Icons.Default.History, Icons.Default.Sync)
        11 -> listOf(Icons.Default.Lock, Icons.Default.Download, Icons.Default.DeleteOutline)
        else -> listOf(Icons.Default.EventNote)
    }
    DashboardRecords(section.rows, icons, navigator)
}

@Composable
private fun TimeSection(page: Int, section: VisualSection, navigator: VisualNavigator) {
    when (page) {
        0 -> {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("THU 14 MAY 2026", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { navigator.openSection(1) }) { Text("Open timeline") }
            }
            TimeClock(section.timeline)
            HealthSplit(
                first = { DashboardValue("Paid activity", "8h 30m", timeAmber) },
                second = { DashboardValue("Personal + free", "4h 15m", recoveryMint) },
            )
        }
        1 -> {
            TimeClock(visualTime.sections.first().timeline)
        }
        2 -> {
            DashboardValue("Work-related window", "10h 45m", timeAmber)
            TimeAllocation(listOf("Paid" to 510, "Travel & preparation" to 105, "Break" to 30))
        }
        3 -> {
            DashboardValue("Sample gross earnings", "£106.50", timeAmber)
            HealthSplit(
                first = { DashboardValue("Per paid hour", "£12.53", timeAmber) },
                second = { DashboardValue("Per work-related hour", "£9.91", activityCyan) },
            )
        }
        4 -> TimeAllocation(listOf("Personal" to 195, "Free" to 60, "Sleep" to 525, "Interruptions" to 15))
        in 5..7 -> {
            HealthLinks(listOf("Weekly" to 5, "Monthly" to 6, "Trends" to 7), navigator)
            section.trend?.let { SampleTrend(it, timeAmber, bars = page != 7) }
            if (page == 5) TimeAllocation(listOf("Sleep" to 57, "Paid" to 39, "Work support" to 10, "Personal" to 60, "Interruptions" to 2), "hrs")
            if (page == 6) TimeAllocation(listOf("Sleep" to 228, "Paid" to 156, "Work support" to 40, "Personal" to 240, "Interruptions" to 8), "hrs")
        }
        8 -> DashboardValue("Connected sources", "0 active", activityCyan)
        9 -> TimeOverlap()
        10 -> Text("0 source permissions granted", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val icons = when (page) {
        0 -> listOf(Icons.Default.Weekend, Icons.Default.Commute, Icons.Default.NotificationsNone)
        1 -> listOf(Icons.Default.Bedtime, Icons.Default.Commute, Icons.Default.WorkOutline, Icons.Default.Commute,
            Icons.Default.DirectionsBike, Icons.Default.Weekend, Icons.Default.Bedtime)
        2 -> listOf(Icons.Default.WorkOutline, Icons.Default.DirectionsBike, Icons.Default.Event, Icons.Default.Commute, Icons.Default.Coffee)
        3 -> listOf(Icons.Default.WorkOutline, Icons.Default.DirectionsBike, Icons.Default.Schedule, Icons.Default.Timelapse)
        4 -> listOf(Icons.Default.Bedtime, Icons.Default.Weekend, Icons.Default.WbSunny, Icons.Default.NotificationsNone)
        8 -> listOf(Icons.Default.EventNote, Icons.Default.DirectionsBike, Icons.Default.FavoriteBorder, Icons.Default.Edit, Icons.Default.Sync)
        9 -> listOf(Icons.Default.ContentCopy, Icons.Default.Timeline, Icons.Default.HelpOutline, Icons.Default.Sync)
        10 -> listOf(Icons.Default.Lock, Icons.Default.Download, Icons.Default.LinkOff)
        else -> listOf(Icons.Default.BarChart)
    }
    DashboardRecords(section.rows, icons, navigator, timeline = page == 1)
}

@Composable
private fun HealthSplit(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    minimumWidth: androidx.compose.ui.unit.Dp = 480.dp,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= minimumWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                first()
                second()
            }
        }
    }
}

@Composable
private fun DashboardPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = dashboardInk, contentColor = dashboardText) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun HealthDial(label: String, value: Int, caption: String, accent: Color) {
    DashboardPanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 240.dp) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    RecoveryRing(value, accent)
                    Text("$caption / 100", color = dashboardSubtle, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    RecoveryRing(value, accent)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                        Text("$caption / 100", color = dashboardSubtle, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryRing(value: Int, accent: Color) {
    Box(Modifier.size(90.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val width = 7.dp.toPx()
            drawCircle(dashboardGrid, radius = size.minDimension / 2 - width / 2, style = Stroke(width))
            drawArc(accent, -90f, 360f * value / 100, false,
                topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width),
                style = Stroke(width, cap = StrokeCap.Round))
        }
        Text("$value", color = accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DashboardValue(label: String, value: String, accent: Color) {
    DashboardPanel {
        Text(label, style = MaterialTheme.typography.labelLarge, color = dashboardSubtle)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthPulse(label: String, value: String, values: List<Int>, accent: Color) {
    DashboardPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = dashboardSubtle)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        DashboardPlot(values, accent, bars = false, selected = values.lastIndex, modifier = Modifier.height(66.dp))
    }
}

@Composable
private fun HealthSleep() {
    val stages = listOf(0 to 9, 1 to 48, 2 to 36, 1 to 43, 3 to 31, 1 to 39, 2 to 37, 3 to 53, 1 to 36, 3 to 57, 1 to 40, 0 to 9)
    val colors = listOf(timeAmber, activityCyan, Color(0xffa59bf9), Color(0xffd9b6f7))
    DashboardPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sleep stages", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("7h 18m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(65.dp).testTag("health-sleep-stages")) {
            val total = stages.sumOf { it.second }
            val lane = size.height / colors.size
            for (i in colors.indices) {
                val y = (i + .5f) * lane
                drawLine(dashboardGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            var start = 0f
            for ((stage, minutes) in stages) {
                val width = size.width * minutes / total
                drawRoundRect(
                    colors[stage],
                    topLeft = Offset(start, stage * lane + 2.dp.toPx()),
                    size = Size((width - 2.dp.toPx()).coerceAtLeast(1f), lane - 4.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
                start += width
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("23:06", color = dashboardSubtle, style = MaterialTheme.typography.labelSmall)
            Text("06:24", color = dashboardSubtle, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Awake", "Light", "Deep", "REM").forEachIndexed { index, label ->
                Text(label, color = colors[index], style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun HealthMovement() {
    DashboardPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsWalk, null, tint = activityCyan)
            Column(Modifier.weight(1f)) {
                Text("Movement", style = MaterialTheme.typography.titleMedium)
                Text("24 min · Park loop", style = MaterialTheme.typography.labelMedium, color = dashboardSubtle)
            }
            Text("6,420 steps", style = MaterialTheme.typography.titleMedium, color = activityCyan)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(dashboardGrid, Offset(3.dp.toPx(), center.y), Offset(size.width - 3.dp.toPx(), center.y),
                strokeWidth = size.height, cap = StrokeCap.Round)
            drawLine(activityCyan, Offset(3.dp.toPx(), center.y), Offset(size.width * .642f, center.y),
                strokeWidth = size.height, cap = StrokeCap.Round)
        }
        Text("Sample goal · 10,000 steps", style = MaterialTheme.typography.labelSmall, color = dashboardSubtle)
    }
}

@Composable
private fun HealthMood() {
    var selected by remember { mutableStateOf("Calm") }
    DashboardPanel {
        Text("Sample mood check-in", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Calm", "Focused", "Tired").forEach { mood ->
                FilterChip(selected == mood, { selected = mood }, { Text(mood) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text("Preview only · $selected", style = MaterialTheme.typography.labelMedium, color = dashboardSubtle)
    }
}

@Composable
private fun HealthWatchPreview() {
    DashboardPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = Color(0xff253342), modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(61.dp)) {
                        val stroke = 4.dp.toPx()
                        drawCircle(dashboardGrid, style = Stroke(stroke))
                        drawArc(recoveryMint, -90f, 266.4f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("74", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = recoveryMint)
                        Text("Recovery", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Watch Companion", style = MaterialTheme.typography.titleMedium)
                Text("Wear OS preview · unpaired", color = dashboardSubtle, style = MaterialTheme.typography.bodySmall)
                Text("74 recovery   ·   6,420 steps", color = recoveryMint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HealthLinks(links: List<Pair<String, Int>>, navigator: VisualNavigator) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        links.forEach { (label, page) ->
            FilterChip(navigator.section == page, { navigator.openSection(page) }, { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun SampleTrend(trend: VisualTrend, accent: Color, bars: Boolean) {
    var selected by remember(trend) { mutableIntStateOf(trend.points.lastIndex) }
    val point = trend.points[selected]
    DashboardPanel {
        Text(trend.title, style = MaterialTheme.typography.titleMedium)
        Text("${point.value} ${trend.unit}  ·  ${point.label}", color = accent, style = MaterialTheme.typography.titleLarge)
        DashboardPlot(trend.points.map { it.value }, accent, bars, selected, Modifier.height(96.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val touchWidth = (maxWidth / trend.points.size).coerceAtLeast(48.dp)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                trend.points.forEachIndexed { i, p ->
                    TextButton(onClick = { selected = i }, modifier = Modifier.width(touchWidth).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 1.dp)) {
                        Text(p.label, style = MaterialTheme.typography.labelSmall, color = if (selected == i) accent else dashboardSubtle)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPlot(values: List<Int>, accent: Color, bars: Boolean, selected: Int, modifier: Modifier) {
    Canvas(modifier.fillMaxWidth()) {
        val low = if (bars) 0 else values.min()
        val range = (values.max() - low).coerceAtLeast(1)
        val step = size.width / values.size
        val padding = 6.dp.toPx()
        val bottom = size.height - padding
        val points = values.mapIndexed { index, value ->
            Offset(step * (index + .5f), bottom - (value - low).toFloat() / range * (bottom - padding))
        }
        for (level in 0..3) {
            val y = padding + (bottom - padding) * level / 3
            drawLine(dashboardGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (bars) {
            val width = step * .53f
            points.forEachIndexed { index, point ->
                drawRoundRect(accent.copy(alpha = if (selected == index) 1f else .48f),
                    topLeft = Offset(point.x - width / 2, point.y),
                    size = Size(width, (bottom - point.y).coerceAtLeast(2.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            }
        } else {
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .34f), Color.Transparent)))
            drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent, 4.dp.toPx(), points[selected])
        }
    }
}

private fun timeColor(label: String): Color = when (label) {
    "Sleep" -> sleepLavender
    "Paid shift", "Paid" -> timeAmber
    "Gig shift" -> gigOrange
    "Personal", "Free", "Free time" -> recoveryMint
    "Interruption", "Interruptions" -> pulseCoral
    else -> activityCyan
}

private fun clockText(minutes: Int): String =
    "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"

@Composable
private fun TimeClock(slices: List<VisualTimeSlice>) {
    val hourLabels = listOf(0, 6, 12, 18, 24)
    val samples = listOf(0, 1, 3, 8)
    var chosen by remember { mutableIntStateOf(3) }
    val slice = slices[chosen]
    val start = slices.take(chosen).sumOf { it.minutes }
    DashboardPanel(Modifier.testTag("visual-daily-timeline")) {
        Text("24-hour timeline", style = MaterialTheme.typography.titleMedium)
        Text("${clockText(start)}–${clockText(start + slice.minutes)}  ·  ${slice.label}", color = timeColor(slice.label),
            style = MaterialTheme.typography.titleLarge)
        Canvas(Modifier.fillMaxWidth().height(34.dp).pointerInput(slices) {
            detectTapGestures { point ->
                val minute = (point.x / size.width * 1440).toInt().coerceIn(0, 1439)
                var end = 0
                chosen = slices.indexOfFirst {
                    end += it.minutes
                    minute < end
                }.coerceAtLeast(0)
            }
        }) {
            var x = 0f
            slices.forEachIndexed { index, interval ->
                val width = size.width * interval.minutes / 1440
                drawRect(timeColor(interval.label).copy(alpha = if (chosen == index) 1f else .60f),
                    topLeft = Offset(x, 0f), size = Size((width - 1.dp.toPx()).coerceAtLeast(1f), size.height))
                x += width
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hourLabels.forEach { Text(clockText(it * 60), style = MaterialTheme.typography.labelSmall, color = dashboardSubtle) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            samples.zip(listOf("Sleep", "Prep", "Work", "Gig")).forEach { (index, name) ->
                TextButton(onClick = { chosen = index }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(0.dp)) {
                    Text(name, color = if (index == chosen) timeColor(slices[index].label) else dashboardSubtle)
                }
            }
        }
    }
}

@Composable
private fun TimeAllocation(parts: List<Pair<String, Int>>, unit: String = "min") {
    DashboardPanel {
        Text("Time allocation", style = MaterialTheme.typography.titleMedium)
        val total = parts.sumOf { it.second }
        Row(Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(7.dp))) {
            parts.forEach { (label, value) ->
                Box(Modifier.weight(value.toFloat()).fillMaxHeight().background(timeColor(label)))
            }
        }
        parts.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(timeColor(label), CircleShape))
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text("$value $unit  ·  ${value * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = dashboardSubtle)
            }
        }
    }
}

@Composable
private fun TimeOverlap() {
    var resolution by remember { mutableStateOf(false) }
    DashboardPanel {
        Text("17:00–18:30 · Delivery", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Source A", if (resolution) "A + B · 90 min" else "Source B").forEach { text ->
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(6.dp), color = timeAmber, contentColor = dashboardInk) {
                    Text(text, Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        TextButton(onClick = { resolution = !resolution }) {
            Text(if (resolution) "Compare originals" else "Preview reconciliation", color = timeAmber)
        }
    }
}

@Composable
private fun DashboardRecords(rows: List<VisualRow>, icons: List<ImageVector>, navigator: VisualNavigator, timeline: Boolean = false) {
    if (rows.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    Column {
        rows.forEachIndexed { index, row ->
            Surface(
                onClick = { navigator.inspect(row) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("visual-row-$index"),
                color = colors.background,
            ) {
                Box(contentAlignment = Alignment.CenterStart) {
                    if (timeline) {
                        Canvas(Modifier.matchParentSize()) {
                            val x = 21.dp.toPx()
                            val start = if (index == 0) center.y else 0f
                            val end = if (index == rows.lastIndex) center.y else size.height
                            drawLine(colors.outlineVariant, Offset(x, start), Offset(x, end), 1.dp.toPx())
                        }
                    }
                    Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).background(if (timeline) colors.tertiaryContainer else colors.secondaryContainer,
                            RoundedCornerShape(if (timeline) 17.dp else 10.dp)), contentAlignment = Alignment.Center) {
                            Icon(icons[index % icons.size], null, modifier = Modifier.size(18.dp),
                                tint = if (timeline) colors.onTertiaryContainer else colors.onSecondaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            row.badge?.let { Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (!timeline && index != rows.lastIndex) HorizontalDivider(color = colors.outlineVariant)
        }
    }
}
