package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val visualStudios: List<VisualModule> = listOf(
    VisualModule(
        "desktop-mode", "Nova Desktop Mode", "Workspace layouts and window previews", Icons.Default.DesktopWindows,
        listOf(
            VisualSection(
                "Workspace", "A workspace for the unfolded screen",
                "Arrange fictional app windows in the preview. The current NovaOS workspace and window positions stay as they are.",
                rows = listOf(VisualRow("Sample dock", "A future desktop could keep frequently used apps within reach.", "DEMO")),
            ),
            VisualSection(
                "Displays", "Display setup preview",
                "No screens have been detected or queried. These cards illustrate how a future setup could distinguish the Fold screen and a separate display.",
                rows = listOf(
                    VisualRow("Fold workspace", "Example expanded workspace using the inner display.", "Example"),
                    VisualRow("External display", "A future connection flow would let you review the display before opening a workspace.", "Unavailable"),
                ),
            ),
            VisualSection(
                "Layouts", "Workspace layout library",
                "These example arrangements illustrate different ways to organise NovaOS windows. Layout saving is unavailable in this demo.",
                rows = listOf(
                    VisualRow("Split", "Use two sample apps side by side.", "Example"),
                    VisualRow("Focus", "Give the sample writing window the whole workspace.", "Example"),
                    VisualRow("Privacy on shared displays", "A future release will need an explicit privacy review before displaying protected content externally.", "Planned"),
                ),
            ),
        ),
    ),
    VisualModule(
        "control-centre", "Nova Control Centre 2.0", "Quick tiles, layout and visibility", Icons.Default.Tune,
        listOf(
            VisualSection(
                "Overview", "Quick controls preview",
                "Try the example tiles and brightness control. Changes are confined to this demo screen.",
                rows = listOf(VisualRow("Current Quick Panel", "The working Quick Panel still opens through its existing Home entry point.", "Existing app")),
            ),
            VisualSection(
                "Arrangement", "Arrange example tiles",
                "A future editor could let you select visible controls, group them and choose their order.",
                rows = listOf(
                    VisualRow("First row", "The sample layout groups focus and audio together.", "Sample layout"),
                    VisualRow("Visibility", "A future screen could review which controls should appear in sensitive contexts.", "Planned"),
                ),
            ),
            VisualSection(
                "Privacy", "Control ownership and access",
                "These statuses are fictional. This demo cannot read connectivity state or change Android settings.",
                rows = listOf(VisualRow("Permission review", "Live controls would require supported integrations and separate consent where applicable.", "Unavailable")),
            ),
        ),
    ),
    VisualModule(
        "themes-studio", "Nova Themes Studio", "Colour, wallpaper and appearance previews", Icons.Default.Palette,
        listOf(
            VisualSection(
                "Palette", "Make a sample Home your own",
                "Choose a colourway and inspect its contrast in an example Home. The current Home theme is unchanged.",
                rows = listOf(VisualRow("Apply theme", "Applying a theme from the studio is unavailable during visual review.", "Unavailable")),
            ),
            VisualSection(
                "Wallpaper", "Wallpaper and material samples",
                "Review fictional combinations for the cover and unfolded screens. These samples do not read the Vault or photos.",
                rows = listOf(
                    VisualRow("Aurora gradient", "A bundled colour study with large contrast areas for icons.", "Sample"),
                    VisualRow("Separate postures", "A future design can preview how wallpaper cropping differs across Fold sizes.", "Planned"),
                ),
            ),
            VisualSection(
                "Accessibility", "Contrast and type",
                "Inspect example labels and surfaces. Changing device text, contrast or Home preferences is unavailable here.",
                rows = listOf(VisualRow("Reading contrast", "A future contrast review could help show how selected colours work together.", "Example")),
            ),
        ),
    ),
    VisualModule(
        "lock-studio", "Nova Lock Screen Studio", "Clock and privacy layout previews", Icons.Default.Lock,
        listOf(
            VisualSection(
                "Preview", "Design a sample lock layout",
                "Try a sample clock style and decide whether the fictional alert is summarised. Real lock and authentication rules stay in effect.",
                rows = listOf(VisualRow("Sample content", "The clock is fixed and the alert is fictional.", "DEMO")),
            ),
            VisualSection(
                "Privacy", "Content visibility preview",
                "This screen illustrates disclosure choices. It cannot read notifications or change privacy settings.",
                rows = listOf(
                    VisualRow("Minimal content", "A sample layout showing the clock and no alert text.", "Example"),
                    VisualRow("Protected previews", "A future editor would need to respect NovaOS security boundaries.", "Planned"),
                ),
            ),
            VisualSection(
                "Shortcuts", "Shortcut positions",
                "Review how two fictional shortcuts could fit around the clock. This studio cannot add lock screen actions.",
                rows = listOf(VisualRow("Shortcut slots", "Left and right positions are shown as design options only.", "Unavailable")),
            ),
        ),
    ),
    VisualModule(
        "pip-controls", "Nova PiP Controls", "Picture-in-picture layout previews", Icons.Default.PictureInPicture,
        listOf(
            VisualSection(
                "Preview", "A sample player in a compact window",
                "Change the corner and size of a fictional player inside this card. Android picture-in-picture and media playback are inactive.",
                rows = listOf(VisualRow("Sample controls", "Playback and dismissal controls illustrate the layout for a future review.", "DEMO")),
            ),
            VisualSection(
                "Placement", "Compact window behaviour",
                "Explore how a future compact player might fit around the workspace. The sample is entirely inside this demo page.",
                rows = listOf(
                    VisualRow("Safe corners", "A future implementation would respect display edges and system controls.", "Planned"),
                    VisualRow("Folded and unfolded", "The current preview scales within the available screen width.", "Sample"),
                ),
            ),
            VisualSection(
                "Privacy", "Playback and protected content",
                "The preview has no media access and creates no floating window. Existing media playback controls remain available in the working app.",
                rows = listOf(VisualRow("Future content policy", "Live PiP would need a separate review of what can leave the protected app surface.", "Unavailable")),
            ),
        ),
    ),
)

@Composable
internal fun VisualStudioPreview(id: String, section: Int) {
    if (section != 0) return
    when (id) {
        "desktop-mode" -> StudioDesktopPreview()
        "control-centre" -> StudioControlPreview()
        "themes-studio" -> StudioThemePreview()
        "lock-studio" -> StudioLockPreview()
        "pip-controls" -> StudioPipPreview()
    }
}

@Composable
private fun StudioDesktopPreview() {
    var split by remember { mutableStateOf(true) }
    StudioPanel("studio-desktop-preview", "Sample workspace") {
        StudioChoices(listOf("Split", "Focus"), if (split) 0 else 1, "studio-desktop-layout") { split = it == 0 }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { index -> Box(Modifier.size(8.dp).background(studioAccent(index), CircleShape)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioWindow("Sample notes", "Plan the reading room", Modifier.weight(1f))
                    if (split) StudioWindow("Sample calendar", "10:30 · Design review", Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioControlPreview() {
    val tiles = listOf("Focus", "Audio", "Layout")
    var enabled by remember { mutableStateOf(setOf("Audio")) }
    var brightness by remember { mutableFloatStateOf(.6f) }
    StudioPanel("studio-control-preview", "Example control tiles") {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tiles.forEachIndexed { index, tile ->
                FilterChip(
                    selected = tile in enabled,
                    onClick = { enabled = if (tile in enabled) enabled - tile else enabled + tile },
                    label = { Text(tile) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("studio-control-tile-$index"),
                )
            }
        }
        Text("Sample brightness · ${(brightness * 100).toInt()}%")
        Slider(brightness, { brightness = it }, Modifier.testTag("studio-control-brightness"))
    }
}

private val studioPalettes = listOf(
    listOf(Color(0xFF122737), Color(0xFF1D4F54), Color(0xFF8AF2D3)),
    listOf(Color(0xFF251B38), Color(0xFF55406D), Color(0xFFF2B8FF)),
    listOf(Color(0xFF34261B), Color(0xFF694531), Color(0xFFFFC88C)),
)

@Composable
private fun StudioThemePreview() {
    var palette by remember { mutableIntStateOf(0) }
    val colors = studioPalettes[palette]
    StudioPanel("studio-theme-preview", "Sample Home palette") {
        StudioChoices(listOf("Aurora", "Dusk", "Amber"), palette, "studio-theme-palette") { palette = it }
        Box(
            Modifier.fillMaxWidth().heightIn(min = 124.dp)
                .background(Brush.linearGradient(colors.take(2)), RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sample Home", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { Box(Modifier.size(32.dp).background(colors[2], RoundedCornerShape(10.dp))) }
                }
            }
        }
    }
}

@Composable
private fun StudioLockPreview() {
    var detailed by remember { mutableStateOf(false) }
    var showAlert by remember { mutableStateOf(false) }
    StudioPanel("studio-lock-preview", "Sample lock preview") {
        StudioChoices(listOf("Minimal", "Date"), if (detailed) 1 else 0, "studio-lock-clock") { detailed = it == 1 }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Lock, null)
                Text("10:24", style = MaterialTheme.typography.displaySmall)
                if (detailed) Text("Thursday · 14 May")
                if (showAlert) Text("Example alert · Calendar")
            }
        }
        FilterChip(
            selected = showAlert, onClick = { showAlert = !showAlert },
            label = { Text("Show example alert") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("studio-lock-alert"),
        )
    }
}

@Composable
private fun StudioPipPreview() {
    var right by remember { mutableStateOf(true) }
    var larger by remember { mutableStateOf(false) }
    StudioPanel("studio-pip-preview", "Fictional player placement") {
        StudioChoices(listOf("Left", "Right"), if (right) 1 else 0, "studio-pip-corner") { right = it == 1 }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxWidth().height(152.dp).padding(12.dp)) {
                Text("Sample workspace", style = MaterialTheme.typography.labelMedium)
                Surface(
                    Modifier.align(if (right) Alignment.BottomEnd else Alignment.BottomStart)
                        .size(width = if (larger) 144.dp else 112.dp, height = if (larger) 88.dp else 66.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayCircle, "Fictional player") }
                }
            }
        }
        FilterChip(
            selected = larger, onClick = { larger = !larger },
            label = { Text("Larger sample window") },
            modifier = Modifier.heightIn(min = 48.dp).testTag("studio-pip-size"),
        )
    }
}

@Composable
private fun StudioPanel(tag: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("DEMO · Preview controls", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioChoices(choices: List<String>, selected: Int, tag: String, onChoice: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEachIndexed { index, choice ->
            FilterChip(
                selected = selected == index, onClick = { onChoice(index) }, label = { Text(choice) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-$index"),
            )
        }
    }
}

@Composable
private fun StudioWindow(title: String, description: String, modifier: Modifier = Modifier) {
    Surface(modifier.heightIn(min = 92.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun studioAccent(index: Int): Color = when (index) {
    0 -> MaterialTheme.colorScheme.primary
    1 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.tertiary
}
