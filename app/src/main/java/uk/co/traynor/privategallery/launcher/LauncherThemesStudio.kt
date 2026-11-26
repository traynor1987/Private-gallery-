package uk.co.traynor.privategallery.launcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.home2.Wallpaper
import uk.co.traynor.privategallery.home2.WallpaperSurface

private val appearanceDraftSaver = Saver<LauncherAppearance, String>(
    save = { LauncherThemePresetCodec.encode(it).decodeToString() },
    restore = { runCatching { LauncherThemePresetCodec.decode(it.encodeToByteArray()) }.getOrNull() },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LauncherThemesStudio(
    appearance: LauncherAppearance,
    previewApps: List<LauncherApp>,
    onApply: (LauncherAppearance) -> Boolean,
    onDeviceWallpaper: () -> Unit,
    enableDocuments: Boolean = true,
    ownScroll: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documentsAllowed by rememberUpdatedState(enableDocuments)
    val legacyFolder = remember(context) { LauncherFolderAppearance.read(context) }
    var draft by rememberSaveable(stateSaver = appearanceDraftSaver) { mutableStateOf(appearance) }
    var inner by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }
    val folder = draft.studio.folder ?: appearance.studio.folder ?: legacyFolder
    val prepared = draft.copy(studio = draft.studio.copy(folder = folder))

    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && documentsAllowed) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        LauncherThemePresetCodec.decode(input.readNBytes(LauncherThemePresetCodec.MAX_BYTES + 1))
                    }
                }.getOrNull()
            }
            if (documentsAllowed && imported != null) draft = imported
            notice = if (imported != null) "Preset ready to preview. Apply it to use it on Home."
                else "This document is not a supported NovaOS theme preset."
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null && snapshot != null && documentsAllowed) scope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                        output.write(snapshot.encodeToByteArray())
                    }
                }.isSuccess
            }
            notice = if (saved) "Theme preset exported." else "The theme preset could not be saved."
        }
    }

    fun style(update: LauncherStudioStyle.() -> LauncherStudioStyle) {
        draft = draft.copy(studio = draft.studio.update())
    }

    Column(
        Modifier.fillMaxWidth().then(if (ownScroll) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp) else Modifier)
            .padding(bottom = 20.dp).testTag("nova-themes-studio"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!ownScroll) Text("Themes Studio", style = MaterialTheme.typography.headlineSmall)
        LauncherOptions("Appearance preset", LauncherLook.entries, draft.studio.look, {
            draft = it.preset(draft)
            notice = null
        })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!inner, onClick = { inner = false }, label = { Text("Cover preview") })
            FilterChip(inner, onClick = { inner = true }, label = { Text("Inner preview") })
        }
        LauncherThemePreview(draft, folder, previewApps, inner)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val next = prepared.copy(dock = if (prepared.dock == LauncherDock.HIDDEN) LauncherDock.GLASS else prepared.dock)
                notice = if (onApply(next)) { draft = next; "Theme applied." } else "The theme could not be saved."
            }, enabled = draft != appearance) { Text("Apply theme") }
            OutlinedButton(onClick = {
                draft = LauncherAppearance(studio = LauncherStudioStyle(folder = LauncherFolderAppearance()))
                notice = "Default appearance ready to preview."
            }) { Text("Restore default") }
        }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()
        LauncherOptions("Wallpaper preset", LauncherWallpaper.entries, draft.wallpaper, { draft = draft.copy(wallpaper = it) })
        if (draft.wallpaper == LauncherWallpaper.DEVICE) TextButton(onClick = onDeviceWallpaper, enabled = enableDocuments) {
            Text("Android wallpaper settings")
        }
        LauncherOptions("Light and dark appearance", LauncherTheme.entries, draft.theme, {
            draft = draft.copy(theme = it, studio = draft.studio.copy(schedule = null))
        })
        val schedule = draft.studio.schedule
        LauncherToggle("Schedule light and dark appearance", schedule != null) {
            style { copy(schedule = if (it) LauncherColourSchedule() else null) }
        }
        if (schedule != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeHour("Light starts", schedule.lightHour, schedule.darkHour) { hour ->
                    style { copy(schedule = schedule.copy(lightHour = hour)) }
                }
                ThemeHour("Dark starts", schedule.darkHour, schedule.lightHour) { hour ->
                    style { copy(schedule = schedule.copy(darkHour = hour)) }
                }
            }
        }
        LauncherOptions("Accent palette", LauncherAccent.entries, draft.accent, {
            draft = draft.copy(accent = it, studio = draft.studio.copy(customAccent = null))
        })
        ThemeAccentPicker(draft.studio.customAccent) { rgb -> style { copy(customAccent = rgb) } }
        LauncherOptions("Typography", LauncherTypeface.entries, draft.studio.typeface, { font -> style { copy(typeface = font) } })
        LauncherOptions("Icon pack", LauncherIconStyle.entries, draft.iconStyle, { draft = draft.copy(iconStyle = it) })
        if (draft.iconStyle == LauncherIconStyle.NOVA_TILES) {
            LauncherOptions("Icon shape", LauncherIconShape.entries, draft.iconShape, { draft = draft.copy(iconShape = it) })
        }
        LauncherOptions("Icon size", LauncherIconSize.entries, draft.iconSize, { draft = draft.copy(iconSize = it) })
        LauncherToggle("Show Home icon labels", draft.labels) { draft = draft.copy(labels = it) }
        LauncherOptions("Cover grid density", LauncherGrid.entries, draft.grid, { draft = draft.copy(grid = it) })
        LauncherToggle("Use a different inner grid", draft.studio.innerGrid != null) {
            style { copy(innerGrid = if (it) draft.grid else null) }
        }
        draft.studio.innerGrid?.let { grid ->
            LauncherOptions("Inner grid density", LauncherGrid.entries, grid, { next -> style { copy(innerGrid = next) } })
        }
        LauncherOptions("Dock appearance", LauncherDock.entries.filter { it != LauncherDock.HIDDEN }, draft.dock,
            { draft = draft.copy(dock = it) })
        LauncherFolderAppearanceSettings(folder) { next -> style { copy(folder = next) } }
        LauncherOptions("Widget framing", LauncherWidgetFrame.entries, draft.studio.widgetFrame, { frame -> style { copy(widgetFrame = frame) } })
        LauncherOptions("Animation intensity", LauncherMotion.entries, draft.studio.motion, { motion -> style { copy(motion = motion) } })

        HorizontalDivider()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = enableDocuments, onClick = {
                runCatching { import.launch(arrayOf("application/json", "text/plain")) }.onFailure {
                    notice = "Android could not open the document picker."
                }
            }) {
                Text("Import theme preset")
            }
            OutlinedButton(enabled = enableDocuments, onClick = {
                pendingExport = LauncherThemePresetCodec.encode(prepared).decodeToString()
                runCatching { export.launch("novaos-theme.json") }.onFailure {
                    pendingExport = null
                    notice = "Android could not open the document picker."
                }
            }) { Text("Export theme preset") }
        }
    }
}

@Composable
private fun ThemeHour(title: String, hour: Int, excluded: Int, onChoose: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    fun label(value: Int) = "%02d:00".format(Locale.getDefault(), value)
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$title ${label(hour)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (option in 0..23) if (option != excluded) {
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { open = false; onChoose(option) })
            }
        }
    }
}

@Composable
private fun ThemeAccentPicker(rgb: Int?, onApply: (Int) -> Unit) {
    var input by rememberSaveable(rgb) { mutableStateOf(rgb?.let { "%06X".format(Locale.ROOT, it) } ?: "") }
    val colour = input.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) }?.toIntOrNull(16)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = input, onValueChange = { input = it.trimStart('#').take(7) },
            modifier = Modifier.weight(1f), singleLine = true,
            label = { Text("Custom accent hex") }, prefix = { Text("#") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            isError = input.isNotEmpty() && colour == null,
            supportingText = if (input.isNotEmpty() && colour == null) ({ Text("Enter six hexadecimal digits.") }) else null,
        )
        if (colour != null) Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(Color(colour or 0xFF000000.toInt())))
        OutlinedButton(onClick = { colour?.let(onApply) }, enabled = colour != null) { Text("Use colour") }
    }
}

@Composable
private fun LauncherThemePreview(
    appearance: LauncherAppearance,
    folder: LauncherFolderAppearance,
    apps: List<LauncherApp>,
    inner: Boolean,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)
    val selectedWallpaper = when (appearance.wallpaper) {
        LauncherWallpaper.OCEAN -> "Aurora"
        LauncherWallpaper.MIDNIGHT -> "Midnight"
        LauncherWallpaper.PLUM -> "Dusk"
        LauncherWallpaper.DEVICE -> null
    }
    val timeLabel = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }
    val grid = appearance.gridFor(inner)
    LauncherStudioTheme(appearance) {
        Box(Modifier.fillMaxWidth().clip(shape).testTag(if (inner) "theme-preview-inner" else "theme-preview-cover")) {
            if (selectedWallpaper == null) Box(Modifier.matchParentSize().background(Color(0xFF172638)))
            else WallpaperSurface(Wallpaper(builtin = selectedWallpaper), context.filesDir, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(timeLabel, style = MaterialTheme.typography.displaySmall, color = Color.White)
                LauncherStyledWidget(appearance.studio.widgetFrame, minHeight = 48) { modifier ->
                    Surface(modifier, shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text(appearance.studio.look.label, Modifier.padding(12.dp),
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val shown = apps.take(grid.columns(inner).coerceAtMost(5)).distinctBy(LauncherApp::component)
                if (shown.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    shown.forEach { app ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            LauncherArtworkIcon(app, appearance, Modifier.size((44 * appearance.iconSize.previewScale).dp))
                            if (appearance.labels) Text(app.label, color = Color.White, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val floating = appearance.studio.look == LauncherLook.FLOATING
                Surface(
                    Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (floating) .87f else 1f),
                    color = when (appearance.dock) {
                        LauncherDock.GLASS -> Color.White.copy(alpha = .19f)
                        LauncherDock.SOLID -> Color(0xFF273550)
                        LauncherDock.TRANSPARENT, LauncherDock.HIDDEN -> Color.Transparent
                    },
                    shape = RoundedCornerShape(if (floating) 29.dp else 18.dp),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(color = if (folder.opaque) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .73f), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Folder, "Folder preview", Modifier.padding(7.dp).size(21.dp), tint = launcherAccent(appearance))
                        }
                        if (folder.labels) Text(folder.mode.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (selectedWallpaper == null) Text("Home will use the Android wallpaper", color = Color.White,
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun LauncherStyledWidget(
    frame: LauncherWidgetFrame,
    minHeight: Int = 104,
    content: @Composable (Modifier) -> Unit,
) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp)
    if (frame == LauncherWidgetFrame.NATIVE) content(modifier.padding(bottom = 8.dp))
    else Surface(
        Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(22.dp),
        color = if (frame == LauncherWidgetFrame.GLASS) Color.White.copy(alpha = .16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        content(modifier.padding(6.dp))
    }
}
