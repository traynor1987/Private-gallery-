package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.PinPage
import uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccess

@Composable
internal fun HiddenRestoreDialog(access:HiddenRestoreAccess,ids:List<String>,onComplete:(String)->Unit) {
    val scope=rememberCoroutineScope()
    val terminal=remember {AtomicBoolean(false)}
    var destination by remember {mutableStateOf<HiddenRestoreAccess.Destination?>(null)}
    var move by remember {mutableStateOf(false)}
    var authenticated by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)}
    var pin by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    var progress by remember {mutableStateOf(0 to ids.size)}
    fun finish(message:String) {if(terminal.compareAndSet(false,true)){access.close();pin="";onComplete(message)}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(access,lifecycle) {
        val observer=LifecycleEventObserver {_,event ->if(event==Lifecycle.Event.ON_STOP)access.close()}
        lifecycle.addObserver(observer)
        onDispose {terminal.set(true);access.close();lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(access) {while(access.isActive())delay(100);finish("Restore closed. Completed destination copies remain; check both destinations before retrying. Sources without verified completed moves remain in Hidden.")}
    Dialog(onDismissRequest={finish("Restore cancelled. Completed copies remain; incomplete moves retain their Hidden sources.")},properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn)) {
        Surface {
            val target=destination
            if(target==HiddenRestoreAccess.Destination.PRIMARY && !authenticated) {
                PinPage(title="Restore to Primary Vault",detail="Fresh Primary authentication is required. Hidden stays active.",
                    pin=pin,onPinChange={if(!busy)pin=it.take(12)},pinLabel="Primary PIN",action=if(busy)"Verifying…" else "Continue",
                    message=error,enabled=!busy,actionEnabled=!busy && pin.length>=6,
                    secondaryAction={TextButton(onClick={finish("Restore cancelled; Hidden originals unchanged.")}){Text("Cancel")}}) {
                    val input=pin.toCharArray();pin="";busy=true
                    scope.launch(start=kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                        val result=try {withContext(Dispatchers.IO){runCatching {access.authenticate(input)}}} finally {input.fill('\u0000')}
                        if(!terminal.get() && access.isActive()){busy=false;result.onSuccess {authenticated=true}.onFailure {error="Primary authentication failed. Check your PIN."}}
                    }
                }
            } else Column(Modifier.padding(20.dp).safeDrawingPadding(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Restore ${ids.size} items",style=MaterialTheme.typography.titleLarge)
                if(target==null) {
                    Text("Copy keeps the Hidden original. Move removes it only after verified transfer. Vault-only edits cannot be restored to Phone Gallery.")
                    HiddenRestoreAccess.Destination.entries.forEach {option ->
                        TextButton(onClick={destination=option;move=false}){Text("Copy to ${option.label}")}
                        TextButton(onClick={destination=option;move=true}){Text("Move to ${option.label}")}
                    }
                } else {
                    Text(if(move) "Move to ${target.label}? Verified sources go to Hidden Recently Deleted. Failed moves retain the source."
                        else "Copy to ${target.label}. Hidden originals remain unchanged.")
                    if(target==HiddenRestoreAccess.Destination.PHONE)Text("These copies will be visible to Phone Gallery and other authorized apps.")
                    if(busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text("Processed ${progress.first} of ${progress.second}")}
                    Button(enabled=!busy,onClick={
                        busy=true
                        scope.launch {
                            val result=withContext(Dispatchers.IO){runCatching {access.transfer(ids,target,move){done,total ->scope.launch {if(!terminal.get())progress=done to total}}}}
                            if(!terminal.get() && access.isActive())finish(result.fold(
                                {"Verified ${it.verified}/${it.total} in ${target.label}; moved ${it.moved}; failed ${it.failed}. ${if(move) "${it.verified-it.moved} copied but not moved." else "Hidden originals unchanged."}"},
                                {"Restore interrupted. Check completed destination copies; unverified sources remain in Hidden."}))
                        }
                    }){Text(if(move)"Confirm Move to ${target.label}" else "Copy to ${target.label}")}
                }
                TextButton(onClick={finish("Restore cancelled. Completed copies remain; incomplete moves retain their Hidden sources.")}){Text("Cancel")}
            }
        }
    }
}
