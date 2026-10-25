package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.PinPage
import uk.co.traynor.privategallery.core.crypto.InvalidPinException
import uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

/** Shared PIN presentation, independent credential authority. Never persist PIN or unlocked state. */
@Composable
internal fun HiddenFoundationDialog(
    storageContext:android.content.Context=LocalContext.current.applicationContext,
    keepEntryOnStop:()->Boolean={false},
    entryVisible:Boolean=true,
    externalSession:HiddenSession?=null,
    showEntry:Boolean=true,
    backgroundTimeoutMillis:Long=60_000,
    onAuthenticationChanged:(Boolean)->Unit={},
    unlockedContent:(@Composable (HiddenSession,()->Unit,()->Unit)->Unit)?=null,
    onDismiss:()->Unit,
) {
    val context=storageContext
    val store=remember(context){HiddenCredentialStore(context)}
    val owned=remember{HiddenSession(android.os.SystemClock::elapsedRealtime)}
    val session=externalSession ?: owned
    val alive=remember{AtomicBoolean(true)}
    val scope=rememberCoroutineScope()
    val lifecycleOwner=LocalLifecycleOwner.current
    var configured by remember{mutableStateOf(store.isConfigured)}
    var pin by remember{mutableStateOf("")}
    var confirmation by remember{mutableStateOf("")}
    var busy by remember{mutableStateOf(false)}
    var unlocked by remember{mutableStateOf(session.isUnlocked)}
    var foreground by remember{mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    var stopGeneration by remember{mutableIntStateOf(0)}
    var presentedGeneration by remember{mutableIntStateOf(0)}
    var message by remember{mutableStateOf("")}
    val dismiss by rememberUpdatedState(onDismiss)
    val retainForPicker by rememberUpdatedState(keepEntryOnStop)
    val timeout by rememberUpdatedState(backgroundTimeoutMillis)
    val report by rememberUpdatedState(onAuthenticationChanged)
    val close={session.lock();pin="";confirmation="";unlocked=false;report(false);dismiss()}
    DisposableEffect(lifecycleOwner,session) {
        val observer=LifecycleEventObserver {_,event ->
            when(event) {
                Lifecycle.Event.ON_STOP -> {
                    stopGeneration++;foreground=false;pin="";confirmation=""
                    if(externalSession==null)session.onBackground(timeout)
                    if(retainForPicker())session.lock()
                }
                Lifecycle.Event.ON_START -> {
                    if(externalSession==null)session.onForeground()
                    foreground=true;unlocked=session.isUnlocked
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            alive.set(false);pin="";confirmation="";session.revokeOperations()
            if(externalSession==null)session.lock()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(session) {
        while(alive.get()) {
            unlocked=session.isUnlocked;report(unlocked)
            delay(250)
        }
    }
    LaunchedEffect(entryVisible) {if(!entryVisible){if(externalSession==null)session.lock();unlocked=false;pin="";confirmation="";report(false)}}
    if(!entryVisible || !showEntry || !foreground)return
    if(unlocked && unlockedContent!=null) {
        if(presentedGeneration!=stopGeneration) {
            // Stop/start can coalesce before Compose removes the old child. Apply its
            // disposal (which revokes operations) BEFORE a new child captures authority.
            val generation=stopGeneration
            LaunchedEffect(generation) {presentedGeneration=generation}
            return
        }
        unlockedContent(session,close){session.lock();unlocked=false;report(false)}
        return
    }
    fun submit() {
        if(busy || pin.length !in 6..12 || (!configured && pin!=confirmation))return
        val input=pin.toCharArray();pin="";confirmation="";message="";busy=true
        val attempt=runCatching{session.beginAuthentication()}.getOrElse{input.fill('\u0000');busy=false;return}
        scope.launch(Dispatchers.IO) {
            var candidate:ByteArray?=null
            try {
                val exists=store.isConfigured
                check(alive.get()){"Hidden entry closed"}
                val key=if(exists)store.unlock(input) else {
                    val primary=PinVaultKeyStore(context)
                    check(primary.isConfigured){"Primary must be configured first"}
                    store.createDifferentFromPrimary(input){proposed ->
                        try {primary.unlock(proposed).also{it.fill(0)};true}
                        catch(_:InvalidPinException){false}
                    }
                }
                candidate=key
                val accepted=session.complete(attempt,key)
                withContext(Dispatchers.Main){if(alive.get()){configured=true;unlocked=accepted;busy=false;report(accepted)}}
            } catch(_:Exception) {
                session.rejectAuthentication(attempt)
                withContext(Dispatchers.Main){if(alive.get()){
                    configured=store.isConfigured;busy=false
                    message="Could not unlock or create Hidden. Check the PIN; setup requires a different PIN from Primary. Existing credentials were retained."
                }}
            } finally {input.fill('\u0000');candidate?.fill(0)}
        }
    }
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            if(unlocked) Column {Text("Hidden session unlocked.");TextButton(onClick={session.lock();unlocked=false}){Text("Lock Hidden")}}
            else if(configured) PasscodeKeypad(pin=pin,onPinChange={if(!busy)pin=it.filter{c->c in '0'..'9'}.take(12)},
                onSubmit=::submit,enabled=!busy,message=message.takeIf{it.isNotEmpty()},appTitle="Private Gallery",
                secondary={TextButton(onClick=close){Text("Close")}})
            else PinPage(
                title=if(uk.co.traynor.privategallery.BuildConfig.DEBUG) "Hidden Vault · TEST ONLY" else "Hidden Vault",
                detail=if(configured)"Enter your independent Hidden PIN." else "Choose a different PIN from Primary: 6–12 digits. Hidden has no backup or recovery yet.",
                pin=pin,onPinChange={if(!busy)pin=it.filter{c ->c in '0'..'9'}.take(12)},
                confirmation=if(configured)null else confirmation,
                onConfirmationChange=if(configured)null else ({value ->if(!busy)confirmation=value.filter{it in '0'..'9'}.take(12)}),
                action=if(busy)"Verifying…" else if(configured)"Unlock" else "Create Hidden",
                message=message.takeIf{it.isNotEmpty()},enabled=!busy,
                actionEnabled=!busy && pin.length in 6..12 && (configured || pin==confirmation),
                pinLabel="Hidden PIN",confirmationLabel="Confirm Hidden PIN",
                secondaryAction={TextButton(onClick=close){Text("Close")}},onAction=::submit,
            )
        }
    }
}
