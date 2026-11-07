package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class VisualModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val sections: List<VisualSection>,
)

internal data class VisualSection(
    val title: String,
    val headline: String,
    val detail: String,
    val metrics: List<VisualMetric> = emptyList(),
    val rows: List<VisualRow> = emptyList(),
    val trend: VisualTrend? = null,
    val timeline: List<VisualTimeSlice> = emptyList(),
)

internal data class VisualMetric(val label: String, val value: String)
internal data class VisualRow(val title: String, val detail: String, val badge: String? = null)
internal data class VisualTrend(val title: String, val unit: String, val points: List<VisualTrendPoint>)
internal data class VisualTrendPoint(val label: String, val value: Int)
internal data class VisualTimeSlice(val label: String, val minutes: Int)

internal val visualModules get() = visualCore + visualTime

/** Presentation state belongs to this authenticated Home composition, never to a Space store. */
internal class VisualNavigator {
    var moduleId by mutableStateOf<String?>(null)
        private set
    var section by mutableIntStateOf(0)
        private set
    var detail by mutableStateOf<VisualRow?>(null)
        private set

    fun open(id: String) {
        moduleId = id
        openSection(0)
    }

    fun openSection(index: Int) {
        section = index
        detail = null
    }

    fun inspect(row: VisualRow) { detail = row }

    fun reset() {
        moduleId = null
        openSection(0)
    }

    fun back(): Boolean {
        if (detail != null) detail = null
        else if (moduleId != null) reset()
        else return false
        return true
    }
}

@Composable
internal fun VisualFoundation(navigator: VisualNavigator, onClose: () -> Unit, onRealApp: (String) -> Unit) {
    val module = visualModules.firstOrNull { it.id == navigator.moduleId }
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            ).testTag(module?.let { "visual-screen-${it.id}" } ?: "visual-catalogue"),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backLabel = when {
                    navigator.detail != null -> "Back to demo section"
                    module != null -> "Back to visual catalogue"
                    else -> "Close visual catalogue"
                }
                IconButton(onClick = { if (!navigator.back()) onClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, backLabel)
                }
                Column(Modifier.weight(1f)) {
                    Text(module?.title ?: "Nova Visual Foundation", style = MaterialTheme.typography.titleLarge)
                    Text("Synthetic examples", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                VisualDemoIndicator()
            }
            if (module == null) {
                VisualCatalogue(onOpen = navigator::open, onRealApp = onRealApp)
            } else {
                VisualModuleBody(module, navigator, onRealApp)
            }
        }
    }
}

@Composable
internal fun VisualDemoIndicator() {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.padding(end = 8.dp).testTag("visual-demo-indicator"),
        shape = RoundedCornerShape(12.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
    ) {
        Text("DEMO", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun VisualCatalogue(onOpen: (String) -> Unit, onRealApp: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("visual-catalogue-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            VisualHero(
                "Explore the next NovaOS apps",
                "Every app below is a built-in visual demo. The examples are fictional and actions stay on this screen.",
            )
        }
        item {
            Text("Available · demonstration only", style = MaterialTheme.typography.titleMedium)
        }
        items(visualModules, key = { it.id }) { module ->
            Surface(
                onClick = { onOpen(module.id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("visual-module-${module.id}"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(module.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.title, style = MaterialTheme.typography.titleMedium)
                        Text(module.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("DEMO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
        item {
            VisualHero("Planned · unavailable", "Live integrations and engines are unavailable in these demos.")
        }
        item {
            Text("Installed · functional", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onRealApp("mind") }) { Text("Open Nova Mind") }
            TextButton(onClick = { onRealApp("evidence") }) { Text("Open Evidence Locker") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisualModuleBody(module: VisualModule, navigator: VisualNavigator, onRealApp: (String) -> Unit) {
    val section = module.sections[navigator.section]
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        LazyRow(Modifier.fillMaxWidth().testTag("visual-sections"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(module.sections.indices.toList()) { index ->
                FilterChip(
                    selected = index == navigator.section,
                    onClick = { navigator.openSection(index) },
                    label = { Text(module.sections[index].title) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("visual-section-$index"),
                )
            }
        }
        key(module.id, navigator.section, navigator.detail) {
            val detail = navigator.detail
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (detail != null) {
                    VisualHero(detail.title, detail.detail)
                    Text("Example detail · no action has been performed", style = MaterialTheme.typography.labelMedium)
                } else {
                    VisualHero(section.headline, section.detail)
                    section.trend?.let { VisualTrendCard(it) }
                    if (section.timeline.isNotEmpty()) VisualTimelineCard(section.timeline)
                    if (section.metrics.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            section.metrics.forEach { metric ->
                                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Column(Modifier.widthIn(min = 120.dp).padding(14.dp)) {
                                        Text(metric.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(metric.label, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    section.rows.forEachIndexed { index, row ->
                        VisualActionCard(row.title, row.detail, row.badge, Modifier.testTag("visual-row-$index")) { navigator.inspect(row) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VisualHero(title: String, detail: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(26.dp), color = colors.surfaceVariant) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(colors.secondaryContainer, colors.surfaceVariant)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
}

@Composable
internal fun VisualActionCard(title: String, detail: String, badge: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
            badge?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun VisualTrendCard(trend: VisualTrend) {
    val colors = MaterialTheme.colorScheme
    val peak = trend.points.maxOf { it.value }.coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(trend.title, style = MaterialTheme.typography.titleMedium)
            Text("Synthetic DEMO · ${trend.unit}", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                trend.points.forEach { point ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${point.value}", style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.height(86.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.widthIn(max = 36.dp).fillMaxWidth()
                                .height((80f * point.value / peak).coerceAtLeast(3f).dp)
                                .background(colors.primary, RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)))
                        }
                        Text(point.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualTimelineCard(slices: List<VisualTimeSlice>) {
    val colors = MaterialTheme.colorScheme
    val accents = listOf(colors.primary, colors.secondary, colors.tertiary, colors.primaryContainer)
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).testTag("visual-daily-timeline"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("24-hour timeline", style = MaterialTheme.typography.titleMedium)
            Text("Synthetic DEMO · sample day", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                slices.forEachIndexed { index, slice ->
                    Box(Modifier.weight(slice.minutes.toFloat()).height(22.dp)
                        .background(accents[index % accents.size], RoundedCornerShape(4.dp)))
                }
            }
            var start = 0
            slices.forEachIndexed { index, slice ->
                val end = start + slice.minutes
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(accents[index % accents.size], RoundedCornerShape(3.dp)))
                    Text("${visualClock(start)}–${visualClock(end)} · ${slice.label}", style = MaterialTheme.typography.bodySmall)
                }
                start = end
            }
        }
    }
}

private fun visualClock(minute: Int): String =
    "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

@Composable
internal fun VisualCatalogueEntry(tag: String, onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Icon(Icons.Default.Science, null)
        Spacer(Modifier.width(8.dp))
        Text("Visual Foundation · DEMO")
    }
}
