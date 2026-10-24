package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.compose.runtime.*
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.security.AutoLockTimeout

/** In-process Hidden owner; never saved as unlocked state or mixed with Primary key material. */
internal class HiddenVaultState {
    val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
    var selected by mutableStateOf(false)
    var visible by mutableStateOf(false)
    var destination by mutableStateOf(Route.VAULT)
    var authenticated by mutableStateOf(false)
    fun select(destination:Route=Route.VAULT) {selected=true;visible=true;this.destination=destination}
    fun outside() {session.revokeOperations();visible=false}
    fun lock() {session.lock();authenticated=false}
    fun leave() {session.lock();selected=false;visible=false;authenticated=false}
}

/** Non-secret policy only. No PIN, key, media, names or unlock state is stored here. App backup is disabled. */
internal class HiddenPreferences(context:Context) {
    private val preferences=context.getSharedPreferences("hidden-v1-policy",Context.MODE_PRIVATE)
    var allowScreenshots:Boolean
        get()=preferences.getBoolean("allow-screenshots",false)
        set(value) {preferences.edit().putBoolean("allow-screenshots",value).apply()}
    var timeout:AutoLockTimeout
        get()=runCatching {AutoLockTimeout.valueOf(preferences.getString("background-timeout",null) ?: "MINUTE_1")}.getOrDefault(AutoLockTimeout.MINUTE_1)
        set(value) {preferences.edit().putString("background-timeout",value.name).apply()}
}
