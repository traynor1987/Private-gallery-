package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun VisualPrivacyAiDesign(
    module: VisualModule,
    navigator: VisualNavigator,
    guardian: VisualGuardianState?,
    people: VisualPeopleState?,
    onRealApp: (String) -> Unit,
) {
    val section = module.sections[navigator.section]
    when (module.id) {
        "keys" -> KeyLibrary(section, navigator)
        "app-permissions" -> PermissionReview(section, navigator)
        "privacy-dashboard" -> PrivacyReport(section, navigator)
        "spaces" -> WorkspaceGallery(section, navigator)
        "sanctuary" -> guardian?.let { FocusRoom(section, navigator, it) }
        "people" -> people?.let { CharacterRoom(section, navigator, it) }
        "ai" -> AssistantRoom(section, navigator, onRealApp)
        "ai-search" -> CitationSearch(section, navigator, onRealApp)
    }
}

@Composable
private fun FeatureLabel(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FeatureBadge(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(shape = RoundedCornerShape(7.dp), color = color) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun IconPlate(icon: ImageVector, background: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(Modifier.size(42.dp).background(background, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, modifier = Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SampleListRow(
    row: VisualRow,
    index: Int,
    navigator: VisualNavigator,
    icon: ImageVector,
    supporting: String? = row.badge,
) {
    Surface(
        onClick = { navigator.inspect(row) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("visual-row-$index"),
        color = Color.Transparent,
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyLibrary(section: VisualSection, navigator: VisualNavigator) {
    FeatureLabel(section.headline)
    when (navigator.section) {
        0 -> {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(query, { query = it.take(48) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Filter sample entries") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(16.dp))
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("SAMPLE WORKSHOP VAULT", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    val visible = section.rows.withIndex().filter { (_, row) ->
                        row.title.contains(query.trim(), ignoreCase = true)
                    }
                    if (visible.isEmpty()) Text("No matching sample entries", Modifier.padding(vertical = 12.dp))
                    visible.forEach { (index, row) ->
                        SampleListRow(row, index, navigator, when (index) {
                            0 -> Icons.Default.VpnKey
                            1 -> Icons.Default.Security
                            else -> Icons.Default.Description
                        })
                    }
                }
            }
            FilledTonalButton(onClick = { navigator.openSection(1) }) { Text("Preview new entry") }
        }
        1 -> {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sample sign-in", style = MaterialTheme.typography.titleLarge)
                    Text("Entry name   Aurora Workshop", style = MaterialTheme.typography.bodyMedium)
                    Text("Secret value   Unavailable", fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            section.rows.forEachIndexed { i, row -> SampleListRow(row, i, navigator, Icons.Default.VpnKey) }
        }
        else -> section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, if (navigator.section == 2) Icons.Default.Folder else Icons.Default.Lock)
        }
    }
}

@Composable
private fun PermissionReview(section: VisualSection, navigator: VisualNavigator) {
    FeatureLabel(section.headline)
    when (navigator.section) {
        0 -> {
            val icons = listOf(Icons.Default.CameraAlt, Icons.Default.Mic, Icons.Default.Notifications)
            val statuses = listOf("Ask each time", "Not granted", "Not requested")
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    section.rows.forEachIndexed { i, row ->
                        SampleListRow(row, i, navigator, icons[i], statuses[i])
                        if (i < section.rows.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
            TextButton(onClick = { navigator.openSection(1) }) { Text("Browse sample apps") }
        }
        1 -> {
            val icons = listOf(Icons.Default.DocumentScanner, Icons.Default.Mic, Icons.Default.DateRange)
            section.rows.forEachIndexed { i, row -> SampleListRow(row, i, navigator, icons[i]) }
        }
        2 -> {
            var step by remember { mutableIntStateOf(0) }
            LinearProgressIndicator(progress = { (step + 1f) / section.rows.size }, modifier = Modifier.fillMaxWidth())
            Text("Step ${step + 1} of ${section.rows.size}", style = MaterialTheme.typography.labelMedium)
            section.rows.forEachIndexed { i, row ->
                SampleListRow(row, i, navigator, if (i == step) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                    if (i == step) "Current sample step" else row.badge)
            }
            FilledTonalButton(onClick = { step = (step + 1) % section.rows.size }) { Text("Next sample step") }
        }
        else -> section.rows.forEachIndexed { i, row -> SampleListRow(row, i, navigator, Icons.Default.History) }
    }
}

@Composable
private fun PrivacyReport(section: VisualSection, navigator: VisualNavigator) {
    FeatureLabel(section.headline)
    val colors = MaterialTheme.colorScheme
    when (navigator.section) {
        0 -> {
            val eventColors = listOf(colors.primary, colors.tertiary, colors.secondary)
            Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SAMPLE ACTIVITY", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("3", style = MaterialTheme.typography.displaySmall)
                        Text("example events", Modifier.padding(bottom = 7.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        eventColors.forEach { color ->
                            Box(Modifier.weight(1f).height(12.dp).background(color, CircleShape))
                        }
                    }
                    Text("Access   ·   Sharing   ·   Retention", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { navigator.openSection(1) }) { Text("Open activity timeline") }
                }
            }
            section.rows.forEachIndexed { i, row ->
                SampleListRow(row, i, navigator, listOf(Icons.Default.Visibility, Icons.Default.Share, Icons.Default.Info)[i])
            }
        }
        1 -> section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, Icons.Default.Schedule, listOf("Access review", "Sharing review", "Retention review")[i])
        }
        2 -> section.rows.forEachIndexed { i, row -> SampleListRow(row, i, navigator, Icons.Default.Share) }
        else -> section.rows.forEachIndexed { i, row -> SampleListRow(row, i, navigator, Icons.Default.History) }
    }
}

@Composable
private fun WorkspaceGallery(section: VisualSection, navigator: VisualNavigator) {
    FeatureLabel(section.headline)
    val colors = MaterialTheme.colorScheme
    if (navigator.section < 2) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rows = section.rows.take(2).withIndex().toList()
            val tile: @Composable (Int, VisualRow, Modifier) -> Unit = { index, row, modifier ->
                Surface(
                    onClick = { navigator.inspect(row) },
                    modifier = modifier.testTag("visual-row-$index"),
                    shape = RoundedCornerShape(22.dp),
                    color = colors.surfaceContainer,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        WorkspaceThumbnail(expanded = index == 1, modifier = Modifier.fillMaxWidth().height(94.dp))
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (index == 0) "Compact workspace" else "Expanded workspace",
                            color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (maxWidth >= 560.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { (index, row) -> tile(index, row, Modifier.weight(1f)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    rows.forEach { (index, row) -> tile(index, row, Modifier.fillMaxWidth()) }
                }
            }
        }
        if (navigator.section == 0) TextButton(onClick = { navigator.openSection(1) }) { Text("Compare sample layouts") }
        section.rows.getOrNull(2)?.let { SampleListRow(it, 2, navigator, Icons.Default.Layers) }
    } else {
        section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, if (navigator.section == 2) Icons.Default.Layers else Icons.Default.Lock)
        }
    }
}

@Composable
private fun WorkspaceThumbnail(expanded: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.background(colors.surfaceContainerHighest, RoundedCornerShape(12.dp))) {
        val unit = size.height / 10f
        val start = if (expanded) size.width * .26f else size.width * .36f
        val columns = if (expanded) 3 else 2
        repeat(2) { y ->
            repeat(columns) { x ->
                drawRoundRect(if ((x + y) % 2 == 0) colors.primaryContainer else colors.tertiaryContainer,
                    Offset(start + unit * x * 1.75f, unit * (2f + y * 2.1f)), Size(unit * 1.35f, unit * 1.5f),
                    androidx.compose.ui.geometry.CornerRadius(unit * .35f))
            }
        }
        if (expanded) {
            drawRoundRect(colors.secondaryContainer, Offset(size.width * .65f, unit * 2f),
                Size(size.width * .23f, unit * 3.6f), androidx.compose.ui.geometry.CornerRadius(unit * .4f))
        }
        drawLine(colors.outline, Offset(size.width * .42f, unit * 8f),
            Offset(size.width * .58f, unit * 8f), strokeWidth = unit * .23f, cap = StrokeCap.Round)
    }
}

@Composable
private fun FocusRoom(section: VisualSection, navigator: VisualNavigator, state: VisualGuardianState) {
    FeatureLabel(section.headline)
    when (navigator.section) {
        0 -> {
            GuardianStatusStrip()
            FocusClock(state, dashboard = true)
            Button(onClick = { navigator.openSection(1) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a demo duration")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { navigator.openSection(2) }, modifier = Modifier.weight(1f)) { Text("Android allowlist") }
                TextButton(onClick = { navigator.openSection(3) }, modifier = Modifier.weight(1f)) { Text("NovaOS allowlist") }
            }
        }
        5 -> {
            GuardianStatusStrip()
            FocusClock(state, dashboard = false)
            if ((state.remaining ?: 0) > 0) {
                Button(onClick = state::advance, modifier = Modifier.testTag("guardian-advance")) {
                    Text("Advance sample by 5 minutes")
                }
                TextButton(onClick = { navigator.openSection(4) }) { Text("See example blocked screen") }
                TextButton(onClick = { navigator.openSection(6) }, modifier = Modifier.testTag("guardian-open-exit")) {
                    Text("Preview early exit")
                }
            } else {
                FilledTonalButton(onClick = { navigator.openSection(1) }) { Text("Choose a demo duration") }
            }
        }
        else -> {
            when (navigator.section) {
                1 -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconPlate(Icons.Default.Schedule)
                    Text("${state.duration} min", style = MaterialTheme.typography.headlineMedium)
                }
                2, 3 -> Text("Sample allowlist · edits affect this screen only", style = MaterialTheme.typography.labelMedium)
                9 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureBadge("Monitor off")
                    FeatureBadge("Overlay off")
                }
            }
            VisualGuardianControls(navigator.section, state, navigator::openSection)
        }
    }
    section.rows.forEachIndexed { i, row ->
        SampleListRow(row, i, navigator, when (navigator.section) {
            2, 3 -> Icons.Default.CheckCircle
            8 -> Icons.Default.Settings
            else -> Icons.Default.Security
        })
    }
}

@Composable
private fun GuardianStatusStrip() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp).testTag("guardian-inactive"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Security, null, tint = colors.tertiary, modifier = Modifier.size(18.dp))
        Text("Protection is not active", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun FocusClock(state: VisualGuardianState, dashboard: Boolean) {
    val colors = MaterialTheme.colorScheme
    val remaining = state.remaining
    val progress = if (dashboard) 1f else ((remaining ?: 0).toFloat() / state.duration).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().height(188.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(168.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            drawCircle(colors.surfaceContainerHighest, radius = size.minDimension / 2f - inset, style = Stroke(stroke))
            drawArc(colors.primary, -90f, 360f * progress, false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val label = when {
                dashboard -> "${state.duration} min"
                remaining == null -> "No demo session"
                remaining == 0 -> "Demo session ended"
                else -> "${remaining.toString().padStart(2, '0')}:00"
            }
            Text(
                label,
                modifier = if (dashboard) Modifier else Modifier.testTag("guardian-remaining"),
                style = if (dashboard || progress > 0f) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                fontFamily = if (dashboard || progress > 0f) FontFamily.Monospace else FontFamily.Default,
                textAlign = TextAlign.Center,
            )
            Text(if (dashboard) "Sample focus" else "Simulated timer", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CharacterRoom(section: VisualSection, navigator: VisualNavigator, state: VisualPeopleState) {
    FeatureLabel(section.headline)
    if (navigator.section == 0) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 560.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CharacterTile(0, Modifier.weight(1f), state, navigator)
                    CharacterTile(1, Modifier.weight(1f), state, navigator)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CharacterTile(0, Modifier.fillMaxWidth(), state, navigator)
                    CharacterTile(1, Modifier.fillMaxWidth(), state, navigator)
                }
            }
        }
        FilledTonalButton(onClick = { navigator.openSection(3) }) { Text("Open character studio") }
    } else {
        if (navigator.section == 1) CharacterPortrait(state.characterIndex, Modifier.fillMaxWidth().height(132.dp))
        VisualPeopleControls(navigator.section, state, navigator::openSection)
    }
}

@Composable
private fun CharacterTile(index: Int, modifier: Modifier, state: VisualPeopleState, navigator: VisualNavigator) {
    val id = if (index == 0) "lyra" else "orbit"
    Surface(
        onClick = { state.chooseCharacter(index); navigator.openSection(1) },
        modifier = modifier.testTag("people-character-$id"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CharacterPortrait(index, Modifier.width(104.dp).height(105.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (index == 0) "Lyra" else "Orbit", style = MaterialTheme.typography.headlineSmall)
                Text(if (index == 0) "Reflection companion" else "Learning mentor",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Fictional character", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun CharacterPortrait(index: Int, modifier: Modifier) {
    val lyra = index == 0
    val backdrop = if (lyra) listOf(Color(0xff284c57), Color(0xff182937)) else listOf(Color(0xff50315e), Color(0xff24243f))
    val luminous = if (lyra) Color(0xff8ee0c4) else Color(0xffdcb3ff)
    Canvas(modifier.background(Brush.linearGradient(backdrop), RoundedCornerShape(16.dp))) {
        val unit = size.minDimension / 100f
        val centre = Offset(size.width / 2f, size.height / 2f)
        fun point(x: Float, y: Float) = centre + Offset(x * unit, y * unit)
        drawCircle(luminous.copy(alpha = .16f), radius = 36f * unit, center = centre)
        drawCircle(luminous.copy(alpha = .25f), radius = 42f * unit, center = centre, style = Stroke(unit))
        val shoulders = Path().apply {
            moveTo(point(-31f, 40f).x, point(-31f, 40f).y)
            cubicTo(point(-20f, 2f).x, point(-20f, 2f).y, point(20f, 2f).x, point(20f, 2f).y, point(31f, 40f).x, point(31f, 40f).y)
            close()
        }
        drawPath(shoulders, luminous.copy(alpha = .8f))
        drawOval(backdrop.last(), point(-19f, -27f), Size(38f * unit, 48f * unit))
        drawOval(luminous, point(-13f, -21f), Size(26f * unit, 37f * unit))
        if (lyra) {
            drawArc(backdrop.last(), 176f, 184f, false, point(-18f, -27f), Size(37f * unit, 28f * unit),
                style = Stroke(7f * unit, cap = StrokeCap.Round))
        } else {
            drawLine(backdrop.last(), point(-15f, -17f), point(14f, -14f), 6f * unit, StrokeCap.Round)
            drawCircle(luminous, 2f * unit, point(0f, -36f))
        }
        drawCircle(backdrop.last(), 1.7f * unit, point(-5f, -5f))
        drawCircle(backdrop.last(), 1.7f * unit, point(5f, -5f))
        drawLine(backdrop.last(), point(-3f, 5f), point(3f, 5f), 1.2f * unit, StrokeCap.Round)
    }
}

@Composable
private fun AssistantRoom(section: VisualSection, navigator: VisualNavigator, onRealApp: (String) -> Unit) {
    FeatureLabel(section.headline)
    if (navigator.section == 0) {
        var selected by remember { mutableIntStateOf(0) }
        val scripts = listOf(
            "Help organise the Aurora Workshop handover." to "List the open decisions, agree the review date, then leave a short handover note.",
            "Compare a checklist with a timeline." to "A checklist makes completion visible. A timeline shows handoffs and dependencies.",
            "Summarise the workshop room change." to "The workshop moved to Room C. Update the invitation and confirm the new room before the event.",
        )
        val colors = MaterialTheme.colorScheme
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.align(Alignment.End).widthIn(max = 420.dp),
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ) {
                Text(scripts[selected].first, Modifier.padding(13.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Surface(
                modifier = Modifier.align(Alignment.Start).widthIn(max = 480.dp),
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                color = colors.surfaceContainer,
            ) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("NOVA · PREWRITTEN", style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    Text(scripts[selected].second, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("Switch sample conversation", style = MaterialTheme.typography.labelMedium)
            section.rows.forEachIndexed { i, row ->
                SampleListRow(row, i, navigator, Icons.Default.ChatBubbleOutline, "Inspect example")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { selected = (selected + 1) % scripts.size }) { Text("Next sample") }
                TextButton(onClick = { onRealApp("mind") }) { Text("Open Nova Mind") }
            }
        }
    } else {
        section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, when (navigator.section) {
                1 -> Icons.Default.FactCheck
                2 -> Icons.Default.Link
                else -> Icons.Default.Lock
            })
        }
    }
}

@Composable
private fun CitationSearch(section: VisualSection, navigator: VisualNavigator, onRealApp: (String) -> Unit) {
    FeatureLabel(section.headline)
    val colors = MaterialTheme.colorScheme
    when (navigator.section) {
        0 -> {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(64) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter example questions") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(20.dp),
            )
            val filtered = section.rows.withIndex().filter { (_, row) ->
                row.title.contains(query.trim(), ignoreCase = true)
            }
            Text("${filtered.size} sample results", color = colors.primary, style = MaterialTheme.typography.labelMedium)
            if (filtered.isEmpty()) Text("No matches in the sample collection", style = MaterialTheme.typography.bodyMedium)
            filtered.forEach { (i, row) ->
                SampleListRow(row, i, navigator, Icons.Default.Search, listOf("Aurora handover · 1 citation",
                    "Studio calendar · 1 citation", "No matching source")[i])
            }
            TextButton(onClick = { onRealApp("evidence") }) { Text("Open Evidence Locker") }
        }
        1 -> section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, Icons.Default.Description, "Citation ${i + 1} · ${row.badge}")
        }
        else -> section.rows.forEachIndexed { i, row ->
            SampleListRow(row, i, navigator, if (navigator.section == 2) Icons.Default.Folder else Icons.Default.Lock)
        }
    }
}
