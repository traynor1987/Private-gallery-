package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Neutral global preferences contain no vault identity or authentication material. */
class PrivacyShieldPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("privacy-shield",Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled",false)
        set(value) { prefs.edit().putBoolean("enabled",value).apply() }
    var shortcut: Boolean
        get() = prefs.getBoolean("three-finger",true)
        set(value) { prefs.edit().putBoolean("three-finger",value).apply() }
    var quickLock: Boolean
        get() = prefs.getBoolean("quick-lock",false)
        set(value) { prefs.edit().putBoolean("quick-lock",value).apply() }
}

@Composable
fun PrivacyShieldSettings(onChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { PrivacyShieldPreferences(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var shortcut by remember { mutableStateOf(store.shortcut) }
    var quick by remember { mutableStateOf(store.quickLock) }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Privacy Shield",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Enable Panic Exit",Modifier.weight(1f)); Switch(enabled,{enabled=it;store.enabled=it;onChanged()})
        }
        if(enabled) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Three-finger touch shortcut",Modifier.weight(1f));Switch(shortcut,{shortcut=it;store.shortcut=it;onChanged()})
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Show Quick Lock button",Modifier.weight(1f));Switch(quick,{quick=it;store.quickLock=it;onChanged()})
            }
            Text("Touch the screen with three fingers together, or use Quick Lock. Media closes and the app returns to its locked entry. Unlock again to continue. Captures already in progress are retained for recovery. This does not delete media or guarantee protection against coercion.",style=MaterialTheme.typography.bodySmall)
        }
        Text("Protected content is covered when the app loses focus. Recents previews are disabled where Android supports it. Your screenshot preference is unchanged while using the app.",style=MaterialTheme.typography.bodySmall)
    }
}
