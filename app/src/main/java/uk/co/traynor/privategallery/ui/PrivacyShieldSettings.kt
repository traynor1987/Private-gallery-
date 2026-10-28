package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    var testing by remember { mutableStateOf(false) }
    val activity=context.playerActivity() as? uk.co.traynor.privategallery.MainActivity
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Quickly close media and return to a neutral locked screen. Unlock again to continue. Media and credentials are retained.",style=MaterialTheme.typography.bodySmall)
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
            OutlinedButton(onClick={activity?.panicExit()},modifier=Modifier.fillMaxWidth()) {Text("Quick Lock now")}
            Text("Touch the screen with three fingers together, or use Quick Lock. The three-finger shortcut is disabled during accessibility touch exploration; use Quick Lock instead. Media closes and the app returns to its locked entry. Unlock again to continue. Captures already in progress are retained for recovery. This does not delete media or guarantee protection against coercion.",style=MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick={testing=true},modifier=Modifier.fillMaxWidth()) {Text("Test Panic Exit safely")}
        Text("Privacy Shield conceals media after switching apps. Normal menus keep their content visible. Recents previews stay protected; foreground screenshots follow the current vault preference.",style=MaterialTheme.typography.bodySmall)
    }
    if(testing) AlertDialog(onDismissRequest={testing=false},title={Text("Test Panic Exit?")},
        text={Text("This test locks the app immediately. Media is retained. Any active recording is stopped safely and retained for recovery. You will need to unlock again.")},
        confirmButton={TextButton(modifier=Modifier.heightIn(min=48.dp),onClick={testing=false;activity?.panicExit()}){Text("Lock and test")}},
        dismissButton={TextButton(modifier=Modifier.heightIn(min=48.dp),onClick={testing=false}){Text("Cancel")}})
}
