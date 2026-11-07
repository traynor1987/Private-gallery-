package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
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

private val visualGroups = listOf(
    "Core applications" to (visualCore + visualTime),
    "Privacy & security" to (visualPrivacy + visualSanctuary),
    "AI experience" to (listOf(visualPeople) + visualAi),
    "System & utilities" to (visualStudios + visualUtilities),
)
internal val visualModules = visualGroups.flatMap { it.second }

/** Presentation state belongs to this authenticated Home composition, never to a Space store. */
internal class VisualNavigator {
    var moduleId by mutableStateOf<String?>(null)
        private set
    var section by mutableIntStateOf(0)
        private set
    var detail by mutableStateOf<VisualRow?>(null)
        private set

    private val detailHistory = mutableListOf<VisualRow>()

    fun open(id: String) {
        moduleId = id
        openSection(0)
    }

    fun openSection(index: Int) {
        section = index
        detail = null
        detailHistory.clear()
    }

    fun inspect(row: VisualRow) {
        if (detail == row) return
        detail?.let(detailHistory::add)
        detail = row
    }

    fun reset() {
        moduleId = null
        openSection(0)
    }

    fun back(): Boolean {
        if (detail != null) detail = detailHistory.removeLastOrNull()
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
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
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
                    Text(if (module?.id == "sanctuary") "Protection is not active" else "Synthetic data",
                        color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
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
        Text("DEMO", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
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
            Text("Demo apps", style = MaterialTheme.typography.titleMedium)
        }
        visualGroups.forEach { (group, modules) ->
          item(key = group) { Text(group, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
          items(modules, key = { it.id }) { module ->
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
        }
        item {
            Text("Installed · functional", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onRealApp("mind") }) { Text("Open Nova Mind") }
            TextButton(onClick = { onRealApp("evidence") }) { Text("Open Evidence Locker") }
        }
    }
}

@Composable
private fun VisualModuleBody(module: VisualModule, navigator: VisualNavigator, onRealApp: (String) -> Unit) {
    val guardian = remember(module.id) { if (module.id == "sanctuary") VisualGuardianState() else null }
    val people = remember(module.id) { if (module.id == "people") VisualPeopleState() else null }
    val sections = remember(module.id) { LazyListState() }
    val scroll = rememberScrollState()
    val detail = navigator.detail
    LaunchedEffect(navigator.section, detail) {
        scroll.scrollTo(0)
        if (detail == null) {
            val visible = sections.layoutInfo.visibleItemsInfo.any { it.index == navigator.section }
            if (!visible) sections.scrollToItem(navigator.section)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (detail == null) {
            LazyRow(
                Modifier.fillMaxWidth().testTag("visual-sections"),
                state = sections,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(module.sections.indices.toList(), key = { it }) { index ->
                    FilterChip(
                        selected = index == navigator.section,
                        onClick = { navigator.openSection(index) },
                        label = { Text(module.sections[index].title) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("visual-section-$index"),
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(top = 8.dp, bottom = 16.dp)
                .testTag("visual-active-section"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            key(module.id) {
                when {
                    module in visualCore && module.id != "health" -> VisualProductivityDesign(module, navigator)
                    module in visualUtilities -> VisualUtilityDesign(module, navigator, onRealApp)
                    detail != null -> VisualExampleDetail(module, detail)
                    module.id == "health" || module.id == "time" -> VisualHealthTimeDesign(module, navigator)
                    module in visualStudios -> VisualStudioDesign(module, navigator)
                    else -> VisualPrivacyAiDesign(module, navigator, guardian, people, onRealApp)
                }
            }
        }
    }
}

@Composable
private fun VisualExampleDetail(module: VisualModule, row: VisualRow) {
    Column(
        Modifier.fillMaxWidth().testTag("visual-detail").padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(module.icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(row.title, style = MaterialTheme.typography.headlineSmall)
        row.badge?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
        HorizontalDivider()
        Text(row.detail, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun VisualCatalogueEntry(tag: String, onOpen: () -> Unit) {
    TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)) {
        Icon(Icons.Default.Science, null)
        Spacer(Modifier.width(8.dp))
        Text("Visual Foundation · DEMO")
    }
}

@Composable
internal fun VisualDrawerTile(module: VisualModule, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            .padding(horizontal = 4.dp).testTag("drawer-visual-${module.id}"),
        shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer,
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(module.icon, null, tint = colors.primary)
            Text(module.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            Text("DEMO", style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
    }
}
