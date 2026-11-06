package uk.co.traynor.privategallery.home2
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.backup.AutomaticBackupController
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog

@Composable internal fun AutomaticBackupHost(access:EvidenceAccess,visible:Boolean){
 if(access.owner!=EvidenceCatalog.Owner.PRIMARY)return
 val context=LocalContext.current;val lifecycle=LocalLifecycleOwner.current.lifecycle
 val controller=remember(access){AutomaticBackupController(context,access)}
 var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
 DisposableEffect(controller,lifecycle){val observer=LifecycleEventObserver{_,event->
  if(event==Lifecycle.Event.ON_RESUME)resumed=true
  if(event==Lifecycle.Event.ON_PAUSE||event==Lifecycle.Event.ON_STOP){resumed=false;controller.pause()}
 };lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer);controller.close()}}
 DisposableEffect(controller,visible,resumed){if(visible&&resumed)controller.resume()else controller.pause();onDispose{controller.pause()}}
 LaunchedEffect(controller,visible,resumed){if(visible&&resumed)while(isActive){withContext(Dispatchers.IO){controller.runIfEligible()};delay(10000)}}
}
