package uk.co.traynor.privategallery.ui

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import uk.co.traynor.privategallery.core.browser.v2.BrowserExternalResultGate

/** Retained only in memory across Activity recreation. No saved browser authority or credentials. */
internal class BrowserPermissionOwners:ViewModel() {
    val capability=BrowserExternalResultGate<Map<String,Boolean>>()
    val location=BrowserExternalResultGate<Map<String,Boolean>>()
    val device=BrowserExternalResultGate<List<android.net.Uri>>()
    val deviceSingleKey="browser-device-single-"+java.util.UUID.randomUUID().toString()
    val deviceMultipleKey="browser-device-multiple-"+java.util.UUID.randomUUID().toString()
    val capabilityKey="browser-capability-"+java.util.UUID.randomUUID().toString()
    val locationKey="browser-location-"+java.util.UUID.randomUUID().toString()
    override fun onCleared(){capability.clear();location.clear();device.clear()}
}

/** Callback and immutable request metadata are disposed together; the gate retains only its marker. */
internal class BrowserWebsitePermission(
    val origin:String,
    permissions:List<String>,
    val decision:BrowserExternalResultGate.Owner<Map<String,Boolean>>,
) {
    val androidPermissions:List<String> = java.util.Collections.unmodifiableList(java.util.ArrayList(permissions))
}

private class BrowserExternalLauncherHolder<I> {var launcher:ActivityResultLauncher<I>?=null}

/** Retained keys reconnect cancelled markers on remount/recreate; no current mutable UI lookup. */
@Composable internal fun <I,O,T> rememberBrowserExternalLauncher(
    key:String,contract:ActivityResultContract<I,O>,gate:BrowserExternalResultGate<T>,
    channel:String="",transform:(O)->T,
):ActivityResultLauncher<I> {
    val registry=checkNotNull(LocalActivityResultRegistryOwner.current).activityResultRegistry
    val holder=remember(registry,key){BrowserExternalLauncherHolder<I>()}
    DisposableEffect(registry,key,contract,gate) {
        val registered=registry.register(key,contract){result ->gate.result(channel,transform(result))}
        holder.launcher=registered
        onDispose {if(holder.launcher===registered)holder.launcher=null;registered.unregister()}
    }
    val launchContract=contract
    return remember(holder,launchContract) {object:ActivityResultLauncher<I>() {
        override fun launch(input:I,options:ActivityOptionsCompat?){checkNotNull(holder.launcher){"Browser request UI is no longer active"}.launch(input,options)}
        override fun unregister(){throw UnsupportedOperationException("Managed browser launcher")}
        override val contract:ActivityResultContract<I,*> get() = launchContract
    }}
}
