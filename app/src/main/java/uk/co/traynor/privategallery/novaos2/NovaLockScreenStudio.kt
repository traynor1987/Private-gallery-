package uk.co.traynor.privategallery.novaos2

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.BatteryManager
import android.provider.Settings
import android.text.format.DateFormat
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.io.IOException
import android.media.AudioManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class NovaLockWallpaperResult(val message: String) {
    APPLIED("Android accepted the lock-screen wallpaper."),
    NOT_SUPPORTED("Android reports that wallpaper changes are unavailable on this device."),
    DENIED("Android does not allow NovaOS to change the wallpaper here."),
    NOT_ACTIVE("Return to the studio and leave Sanctuary before applying wallpaper."),
    FAILED("Android did not confirm the wallpaper change. Check your system wallpaper settings."),
}

/** Generated artwork is independent of private media, clock styling and notification content. */
private fun applyNovaLockWallpaper(
    context: Context,
    preset: NovaLockWallpaper,
    authorised: () -> Boolean,
): NovaLockWallpaperResult {
    if (!authorised()) return NovaLockWallpaperResult.NOT_ACTIVE
    return try {
        val manager = context.getSystemService(WallpaperManager::class.java) ?: return NovaLockWallpaperResult.NOT_SUPPORTED
        if (!manager.isWallpaperSupported) return NovaLockWallpaperResult.NOT_SUPPORTED
        if (!manager.isSetWallpaperAllowed) return NovaLockWallpaperResult.DENIED
        val bitmap = Bitmap.createBitmap(1080, 2160, Bitmap.Config.ARGB_8888)
        try {
            val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(),
                    preset.shades.map(Long::toInt).toIntArray(), null, Shader.TileMode.CLAMP)
            }
            Canvas(bitmap).drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), brush)
            if (!authorised()) NovaLockWallpaperResult.NOT_ACTIVE
            else if (manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK) > 0) NovaLockWallpaperResult.APPLIED
            else NovaLockWallpaperResult.FAILED
        } finally {
            bitmap.recycle()
        }
    } catch (_: SecurityException) {
        NovaLockWallpaperResult.DENIED
    } catch (_: IOException) {
        NovaLockWallpaperResult.FAILED
    } catch (_: RuntimeException) {
        NovaLockWallpaperResult.FAILED
    }
}

@Composable
internal fun NovaLockScreenStudio(host: NovaSystemHost) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val config = remember(context) { NovaSystemConfig(context.applicationContext, "lock-screen-studio") }
    var style by remember { mutableStateOf(NovaLockStyle()) }
    var ready by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var musicActive by remember { mutableStateOf<Boolean?>(null) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<NovaLockWallpaper?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(config) {
        try {
            withContext(Dispatchers.IO) { config.read()?.let(NovaLockStyle::decode) }?.let { style = it }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            message = "The saved style could not be opened. Preview controls are available."
        } finally {
            ready = true
        }
    }
    LaunchedEffect(lifecycle, context, style.showMediaStatus) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = LocalDateTime.now()
                musicActive = if (style.showMediaStatus) context.getSystemService(AudioManager::class.java)?.isMusicActive else null
                battery = context.getSystemService(BatteryManager::class.java)
                    ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
                delay((60 - now.second) * 1000L - now.nano / 1_000_000)
            }
        }
    }

    fun openSettings(action: String) {
        if (!host.openSystemSettings(action)) message = "This Android settings page is unavailable."
    }

    val preview = @Composable { NovaLockPreview(style, lockPreviewTime(now.toLocalTime(), DateFormat.is24HourFormat(context),
        Locale.getDefault()), battery, calendarDate = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        musicActive = musicActive) }
    val controls = @Composable {
        NovaLockControls(style, ready && !busy, onChange = { style = it }, onSave = {
            val draft = style
            scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { config.write(draft.encode()) }
                    message = "Studio style saved on this device."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    message = "The studio style could not be saved."
                } finally {
                    busy = false
                }
            }
        }, onApply = { pending = style.wallpaper }, status = message)
        NovaSystemSection("Android lock screen") {
            Text("Samsung and Android manage the system clock, always-on display, calendar and media widgets.",
                style = MaterialTheme.typography.bodyMedium)
            Text("To add available Weather or Calendar widgets on Galaxy, open Settings → Wallpaper and style → current lock screen → Add widgets.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { openSettings(Settings.ACTION_SETTINGS) }) { Text("Open Galaxy settings") }
            TextButton(onClick = { openSettings(Settings.ACTION_SECURITY_SETTINGS) }) { Text("Open Android lock settings") }
            TextButton(onClick = { openSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) { Text("Open NovaOS notification settings") }
        }
    }

    NovaLockStudioLayout(host.wide, preview, controls)

    pending?.let { wallpaper ->
        NovaLockWallpaperConfirmation(wallpaper, onDismiss = { pending = null }, onConfirm = {
            pending = null
            scope.launch {
                busy = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        applyNovaLockWallpaper(context.applicationContext, wallpaper) {
                            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && host.foregroundAllowed()
                        }
                    }
                    message = result.message
                } finally {
                    busy = false
                }
            }
        })
    }
}

@Composable
internal fun NovaLockStudioLayout(wide: Boolean, preview: @Composable () -> Unit, controls: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("nova-lock-studio"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { preview() }
                Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(14.dp)) { controls() }
            }
        } else {
            preview()
            controls()
        }
    }
}

@Composable
internal fun NovaLockWallpaperConfirmation(wallpaper: NovaLockWallpaper, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Apply ${wallpaper.title}?") }, text = {
        Text("Apply this artwork to Android’s lock screen. Clock and widget choices stay in the NovaOS preview.")
    }, confirmButton = { TextButton(onClick = onConfirm) { Text("Apply lock wallpaper") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
internal fun NovaLockPreview(style: NovaLockStyle, time: String, battery: Int?, modifier: Modifier = Modifier,
    calendarDate: String = "", musicActive: Boolean? = null) {
    val shades = if (style.ambientPreview) listOf(Color.Black, Color(0xFF0B1120))
        else style.wallpaper.shades.map(::Color)
    val foreground = Color(style.colour.argb)
    val clockFont = when (style.clock) {
        NovaLockClock.STUDIO -> FontFamily.SansSerif
        NovaLockClock.EDITORIAL -> FontFamily.Serif
        NovaLockClock.DIGITAL -> FontFamily.Monospace
    }
    Box(modifier.fillMaxWidth().heightIn(min = 264.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(shades)).padding(20.dp).testTag("nova-lock-preview")) {
        Text(if (style.ambientPreview) "Ambient preview" else "Lock preview",
            color = foreground, style = MaterialTheme.typography.labelMedium)
        Column(Modifier.align(Alignment.Center).padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(time, color = foreground, fontFamily = clockFont, fontWeight = FontWeight.Medium,
                fontSize = 43.sp, lineHeight = 49.sp)
            if (style.showCalendarDate && calendarDate.isNotEmpty()) Text(calendarDate, color = foreground,
                style = MaterialTheme.typography.bodyMedium)
            if (style.showMediaStatus && !style.ambientPreview) Text(when (musicActive) {
                true -> "Audio playing at $time"
                false -> "Audio idle at $time"
                null -> "Audio status unavailable"
            }, color = foreground, style = MaterialTheme.typography.bodyMedium)
            if (style.showBattery) Text(battery?.let { "Battery $it%" } ?: "Battery unavailable",
                color = foreground, style = MaterialTheme.typography.bodyMedium)
            if (style.showNotificationExample && !style.ambientPreview) {
                Text("Notification hidden", Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x66111A2A))
                    .padding(horizontal = 12.dp, vertical = 8.dp), color = foreground,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovaLockControls(style: NovaLockStyle, enabled: Boolean, onChange: (NovaLockStyle) -> Unit,
    onSave: () -> Unit, onApply: () -> Unit, status: String? = null) {
    NovaSystemSection("Preview style") {
        NovaLockChoices("Wallpaper", NovaLockWallpaper.entries, style.wallpaper, NovaLockWallpaper::title, enabled) {
            onChange(style.copy(wallpaper = it))
        }
        NovaLockChoices("Clock type", NovaLockClock.entries, style.clock, NovaLockClock::title, enabled) {
            onChange(style.copy(clock = it))
        }
        NovaLockChoices("Clock colour", NovaLockColour.entries, style.colour, NovaLockColour::title, enabled) {
            onChange(style.copy(colour = it))
        }
        NovaLockSwitch("Calendar date", style.showCalendarDate, enabled) { onChange(style.copy(showCalendarDate = it)) }
        NovaLockSwitch("Media status", style.showMediaStatus, enabled) { onChange(style.copy(showMediaStatus = it)) }
        NovaLockSwitch("Battery widget", style.showBattery, enabled) { onChange(style.copy(showBattery = it)) }
        NovaLockSwitch("Redacted notification example", style.showNotificationExample, enabled) {
            onChange(style.copy(showNotificationExample = it))
        }
        NovaLockSwitch("Ambient appearance", style.ambientPreview, enabled) { onChange(style.copy(ambientPreview = it)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApply, enabled = enabled) { Text("Apply wallpaper") }
            TextButton(onClick = onSave, enabled = enabled) { Text("Save studio style") }
        }
        status?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> NovaLockChoices(title: String, choices: List<T>, selected: T, label: (T) -> String,
    enabled: Boolean, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice -> FilterChip(choice == selected, { onSelect(choice) }, { Text(label(choice)) },
            enabled = enabled) }
    }
}

@Composable
private fun NovaLockSwitch(label: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled,
        role = Role.Switch, onValueChange = onCheckedChange), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
