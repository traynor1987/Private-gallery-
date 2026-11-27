package uk.co.traynor.privategallery.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun LauncherThemeClock(
    appearance: LauncherAppearance,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val timeFormat = DateFormat.getTimeFormat(context)
    val dateFormat = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val now by produceState(System.currentTimeMillis(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val next = System.currentTimeMillis()
                value = next
                delay(60_000 - next % 60_000)
            }
        }
    }
    val time = timeFormat.format(Date(now))
    val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(dateFormat)
    val look = appearance.studio.look
    val frame = modifier.fillMaxWidth().testTag("launcher-theme-clock-${look.name.lowercase(Locale.ROOT)}")
    val timeStyle = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium
    val dateStyle = MaterialTheme.typography.bodyMedium

    when (look) {
        LauncherLook.NOVA -> Column(
            frame.clip(RoundedCornerShape(16.dp))
                .background(launcherAccent(appearance))
                .padding(start = 4.dp)
                .background(Color(0xFF101B2D))
                .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
        ) {
            Text(time, color = Color.White, style = timeStyle, fontWeight = FontWeight.SemiBold)
            Text(date, color = Color(0xFFD2DEEF), style = dateStyle)
        }
        LauncherLook.ROUNDED -> Surface(
            frame,
            shape = RoundedCornerShape(if (compact) 23.dp else 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 14.dp)) {
                Text(time, style = timeStyle, fontWeight = FontWeight.Bold)
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = dateStyle)
            }
        }
        LauncherLook.FLOATING -> Column(
            frame.padding(vertical = if (compact) 4.dp else 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(date, color = Color.White, textAlign = TextAlign.Center, style = dateStyle)
            Text(time, color = Color.White, textAlign = TextAlign.Center, style = timeStyle, fontWeight = FontWeight.Light)
        }
    }
}
