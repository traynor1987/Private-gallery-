package uk.co.traynor.privategallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Nonsecret acknowledgement only: never stores an origin, cookie or Space identity. */
internal class BrowserBetaAcknowledgement(context:android.content.Context) {
    private val preferences=context.applicationContext.getSharedPreferences("nova-browser-beta",android.content.Context.MODE_PRIVATE)
    fun accepted()=preferences.getInt("notice-revision",0)==1
    fun accept()=preferences.edit().putInt("notice-revision",1).commit()
}

/** Returns true while notice owns presentation. Call before composing browser/runtime effects. */
@Composable internal fun BrowserBetaNotice(onExit:()->Unit,modifier:Modifier=Modifier,appName:String?=null):Boolean {
    val context=LocalContext.current
    val store=remember(context){BrowserBetaAcknowledgement(context)}
    var accepted by remember(store){mutableStateOf(store.accepted())}
    var failed by remember{mutableStateOf(false)}
    if(accepted)return false
    val security=novaSecurityActions()
    val exit=security?.home ?: onExit
    BackHandler(onBack=exit)
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            NovaAppHeader(appName ?: "Browser & Tor — Beta",onHome=exit,onLock=security?.lock,onPanic=security?.panic)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(if(appName!=null)"Saved app privacy"else"Beta browser privacy",style=MaterialTheme.typography.headlineSmall)
                Text("WebView may write cookies and site data to app-private disk without NovaOS vault-key encryption. Files may remain after exit or a crash until cleanup runs. Browser profiles are not memory-only.")
                Text("Space authentication and the selected VPN/Tor routing still apply. Tor does not provide the full anonymity protections of Tor Browser.",style=MaterialTheme.typography.bodyMedium)
                Text("Advanced encrypted browser storage is planned for a future version.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(failed)Text("The acknowledgement could not be saved. Retry or return Home.",color=MaterialTheme.colorScheme.error)
                Button(onClick={if(store.accept())accepted=true else failed=true},modifier=Modifier.fillMaxWidth().testTag("browser-beta-continue")){Text(if(appName!=null)"Continue to app"else"Continue to Beta")}
                OutlinedButton(onClick=exit,modifier=Modifier.fillMaxWidth()){Text("Return Home")}
            }
            NovaNativeNavigation(onBack=exit)
        }
    }
    return true
}
