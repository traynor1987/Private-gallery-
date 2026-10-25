package uk.co.traynor.privategallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.ProtectedAppShell
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.VaultHome
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppNavigationDestination
import uk.co.traynor.privategallery.core.ui.MediaViewerSource
import uk.co.traynor.privategallery.core.vault.VaultItem

/** Opt-in ownership for shared tiles. Primary presentation keeps its existing ownership path. */
internal val LocalSensitivePreviews = staticCompositionLocalOf {false}

private class HiddenViewerSelection(val first:List<ViewerMediaEntry>,initialIndex:Int) {
    var second by mutableIntStateOf(initialIndex)
}

@Composable
internal fun HiddenVaultDialog(
    storageContext:Context=LocalContext.current.applicationContext,
    onPickerActive:(Boolean)->Unit={},
    primaryAvailable:Boolean=true,
    externalSession:HiddenSession?=null,
    showEntry:Boolean=true,
    initialDestination:Route=Route.VAULT,
    onScopedDestinationChanged:(Route)->Unit={},
    onAuthenticationChanged:(Boolean)->Unit={},
    onNavigateOutside:(AppNavigationDestination)->Unit={},
    onDismiss:()->Unit,
    onReturnToPrimary: (() -> Unit)? = null,
    onBrowseDeviceGallery: (() -> Unit)? = null,
    createPrimaryCopy: ((HiddenSession) -> uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?)? = null,
) {
    if(!BuildConfig.HIDDEN_ENABLED)return
    // Only the in-process selection survives a stop, never decoded media or saved unlock state.
    val viewerState = remember {mutableStateOf<HiddenViewerSelection?>(null)}
    var requireReopen by remember {mutableStateOf(false)}
    val failedViewerEntries=remember {mutableStateOf<Set<String>>(emptySet())}
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->
            // Conceal the selection that was actually displayed, not the next grid tap.
            if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP && viewerState.value!=null) requireReopen=true
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);viewerState.value=null}
    }
    val pickerActive = remember {AtomicBoolean(false)}
    var pending by remember {mutableStateOf<List<Uri>>(emptyList())}
    val alive=remember {AtomicBoolean(true)}
    val pickerChanged by rememberUpdatedState(onPickerActive)
    DisposableEffect(Unit) {onDispose {alive.set(false);pickerActive.set(false);pending=emptyList();pickerChanged(false)}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) {uris ->
        pickerActive.set(false);pickerChanged(false)
        if(alive.get())pending=uris
    }
    HiddenFoundationDialog(storageContext=storageContext,keepEntryOnStop=pickerActive::get,entryVisible=primaryAvailable,
        externalSession=externalSession,showEntry=showEntry,onAuthenticationChanged={authenticated ->
            if(!authenticated) {viewerState.value=null;requireReopen=false;failedViewerEntries.value=emptySet()}
            onAuthenticationChanged(authenticated)
        },
        unlockedContent={session,close,lockForPicker ->
            HiddenVaultContent(storageContext,session,pending,initialDestination,viewerState,requireReopen,{requireReopen=false},failedViewerEntries,createPrimaryCopy,onBrowseDeviceGallery,onReturnToPrimary,onImportsConsumed={pending=emptyList()},onClose=close,onLock=lockForPicker,onScopedDestinationChanged=onScopedDestinationChanged,onNavigateOutside=onNavigateOutside,onPick={
                // The picker lives above authenticated content so its result survives session revocation.
                pickerActive.set(true);pickerChanged(true);lockForPicker()
                try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}
                catch(_:Exception) {pickerActive.set(false);pickerChanged(false)}
            })
        },onDismiss=onDismiss)
}

@Composable
private fun HiddenVaultContent(
    context:Context,session:HiddenSession,pending:List<Uri>,initialDestination:Route,viewerState:MutableState<HiddenViewerSelection?>,requireReopen:Boolean,onReopened:()->Unit,failedViewerEntries:MutableState<Set<String>>,createPrimaryCopy:((HiddenSession)->uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?)?,onBrowseDeviceGallery:(()->Unit)?,onReturnToPrimary:(()->Unit)?,onImportsConsumed:()->Unit,onClose:()->Unit,onLock:()->Unit,onScopedDestinationChanged:(Route)->Unit,onNavigateOutside:(AppNavigationDestination)->Unit,onPick:()->Unit,
) {
    val cameraActivity=androidx.activity.compose.LocalActivity.current as? uk.co.traynor.privategallery.MainActivity
    val repository=remember(context,session) {runCatching {HiddenRepository(context,session)}.getOrNull()}
    if(repository==null) {LaunchedEffect(Unit) {session.lock();onClose()};return}
    val alive=remember {AtomicBoolean(true)}
    val scope=rememberCoroutineScope()
    val editor=remember(context,session) {uk.co.traynor.privategallery.core.hidden.HiddenEditorAccess(context,session,authorized=alive::get)}
    DisposableEffect(editor) {onDispose {editor.close()}}
    var revision by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf("")}
    var primaryCopy by remember {mutableStateOf<uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?>(null)}
    DisposableEffect(Unit) { onDispose { primaryCopy?.close() } }
    var restoreRequest by remember {mutableStateOf<Pair<uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccess,List<String>>?>(null)}
    DisposableEffect(Unit) {onDispose {restoreRequest?.first?.close()}}
    var favouriteName by remember {mutableStateOf<String?>(null)}
    var favouriteIds by remember {mutableStateOf<Set<String>>(emptySet())}
    var route by remember {mutableStateOf(initialDestination)}
    var viewer by viewerState
    var viewerCollectionItem by remember {mutableStateOf<String?>(null)}
    var collections by remember {mutableStateOf<List<uk.co.traynor.privategallery.core.vault.VaultCollection>>(emptyList())}
    val close={session.lock();viewer=null;onClose()}
    DisposableEffect(Unit) {onDispose {alive.set(false);session.revokeOperations()}}
    BackHandler {close()}
    fun release(value:Any?) {when(value){is ByteArray->value.fill(0);is Bitmap->value.recycle();is AutoCloseable->value.close()}}
    fun <T> request(block:()->T,complete:(Result<T>)->Unit) {
        scope.launch(Dispatchers.IO) {
            val result=runCatching {block()}
            var delivered=false
            try {
                withContext(Dispatchers.Main) {
                    if(alive.get() && session.isUnlocked) {
                        complete(result);delivered=true
                        if(result.isFailure)error="Hidden operation failed. Existing encrypted media and originals were retained."
                    }
                }
            } finally {if(!delivered)release(result.getOrNull())}
        }
    }
    fun saveEdit(id:String,bytes:ByteArray,provenance:uk.co.traynor.privategallery.core.editor.AiEditProvenance?,cancelled:()->Boolean,done:(Result<Unit>)->Unit) {
        val owner=viewer
        scope.launch(Dispatchers.IO) {
            val result=runCatching {editor.saveCopy(id,bytes,provenance) {cancelled() || viewer !== owner}}
            withContext(Dispatchers.Main) {
                if(alive.get() && editor.isActive() && !cancelled() && viewer === owner) {
                    done(result.map {Unit})
                    result.onSuccess {copy ->revision++;viewer=HiddenViewerSelection(listOf(ViewerMediaEntry(copy.id,copy.mimeType)),0)}
                }
            }
        }.invokeOnCompletion {bytes.fill(0)}
    }
    fun generate(request:uk.co.traynor.privategallery.core.editor.GenerationRequest,stage:(String)->Unit,done:(Result<VaultItem>)->Unit):()->Unit {
        val job=scope.launch(Dispatchers.IO) {
            val result=runCatching {editor.generate(request) {message ->scope.launch(Dispatchers.Main) {if(alive.get() && editor.isActive())stage(message)}}}
            withContext(Dispatchers.Main) {if(alive.get() && editor.isActive())done(result)}
        }
        return {job.cancel()}
    }
    fun refreshFavourite() {
        request({repository.favouriteCollection()}) {result ->
            val favourite=result.getOrNull();favouriteName=favourite?.name
            if(favourite==null)favouriteIds=emptySet()
            else request({repository.itemsInCollection(favourite.id).map {it.id}.toSet()}) {favouriteIds=it.getOrDefault(emptySet())}
        }
    }
    fun change(block:()->Unit,done:(String)->Unit) {request(block) {result ->
        done(if(result.isSuccess)"Hidden updated." else "Hidden update failed; encrypted records retained.")
        if(result.isSuccess){revision++;refreshFavourite()}
    }}
    LaunchedEffect(Unit) {request({repository.reconcile();uk.co.traynor.privategallery.core.hidden.HiddenPhoneExport(context).reconcile()}) {};refreshFavourite()}
    LaunchedEffect(pending) {
        if(pending.isNotEmpty()) {
            val selected=pending.toList();onImportsConsumed()
            request({
                var imported=0
                selected.forEach {uri -> try {repository.import(uri);imported++} catch(_:Exception) { /* Originals retained; report partial success. */ } }
                imported
            }) {result ->
                revision++;refreshFavourite()
                result.onSuccess {count -> error="Imported $count of ${selected.size} selected items. Original files were retained."}
            }
        }
    }
    // Reuse the Activity viewport and screenshot policy, like Gallery/Browser/Settings.
    // Credential entry remains a separate SecureOn dialog in HiddenFoundationDialog.
        CompositionLocalProvider(LocalSensitivePreviews provides true) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    if(error.isNotEmpty())Text(error,Modifier.padding(horizontal=12.dp),color=MaterialTheme.colorScheme.error)
                    Box(Modifier.weight(1f)) {
                        ProtectedAppShell(selected=route,favouriteLabel=favouriteName,
                            onNavigate={destination ->
                                when(destination) {
                                    AppNavigationDestination.VAULT -> {route=Route.VAULT;onScopedDestinationChanged(route);refreshFavourite()}
                                    AppNavigationDestination.FAVOURITE -> {route=Route.FAVOURITE;onScopedDestinationChanged(route);refreshFavourite()}
                                    else -> {viewer=null;session.revokeOperations();onNavigateOutside(destination)} // Hide scoped content; keep independent authentication.
                                }
                            }) {padding ->
                            key(route) {VaultHome(
                                onLock={viewer=null;onLock()},onImport={_,done ->done("Use Add media to choose Hidden imports.")},
                                onLoadItems={done ->request({repository.items()}) {result ->result.onSuccess {items ->done(if(route==Route.FAVOURITE)items.filter {it.id in favouriteIds} else items)}}},
                                onLoadCollections={done ->request({repository.collections()}) {it.onSuccess(done)}},
                                onCreateCollection={name,done ->request({repository.createCollection(name)}) {result ->done(result);if(result.isSuccess){revision++;refreshFavourite()}}},
                                onAddItemsToCollection={id,ids,done ->change({repository.addItemsToCollection(id,ids)},done)},
                                onRemoveItemsFromCollection={id,ids,done ->change({repository.removeItemsFromCollection(id,ids)},done)},
                                onRenameCollection={id,name,done ->change({repository.renameCollection(id,name)},done)},
                                onDeleteCollection={id,done ->change({repository.deleteCollection(id)},done)},
                                onLoadCollectionItems={id,done ->request({repository.itemsInCollection(id)}) {it.onSuccess(done)}},
                                onLoadPreview={item,done ->request({repository.preview(item.id)},done)},
                                cropRevision=revision+favouriteIds.hashCode(),biometricEnabled=false,onEnrollBiometrics={},
                                onSetFavouriteCollection={id,done ->change({repository.setFavouriteCollection(id)},done)},
                                onFavouriteStateChanged={refreshFavourite()},
                                onLoadFavouriteIds={done -> done(favouriteIds)},
                                onOpenViewer={entries,_,index ->
                                    onReopened() // An explicit fresh selection must not inherit an old preview's latch.
                                    failedViewerEntries.value=emptySet();viewer=HiddenViewerSelection(entries,index)
                                },modifier=Modifier.padding(padding),
                                onLoadRecentlyDeleted={done ->request({repository.recentlyDeleted()}) {it.onSuccess(done)}},
                                onMoveItemsToRecentlyDeleted={ids,done ->change({repository.trash(ids)},done)},
                                onChangeRecentlyDeleted={item,delete,done ->change({if(delete)repository.deletePermanently(item.id) else repository.restore(item.id)},done)},
                                vaultTitle=if(route==Route.FAVOURITE)favouriteName ?: "Favourite" else "Hidden Vault",
                                statusLabel=if(BuildConfig.DEBUG) "Hidden · TEST ONLY" else "Hidden · v${BuildConfig.VERSION_NAME}",
                                allowGeneration=true,allowBiometrics=false,allowRestore=false,
                                onGenerateImage=::generate,editingVaultLabel="Hidden Vault",
                                onCamera={cameraActivity?.openVaultCamera(session)},
                                onRecoverCamera={cameraActivity?.recoverVaultCamera(session)},
                                onLoadSort={done->request({uk.co.traynor.privategallery.core.ui.MediaSort.decode(repository.presentationSort())}) {it.onSuccess(done)}},
                                onSaveSort={value->request({repository.presentationSort(value.name)}) { }},
                                onRequestImport={ (onBrowseDeviceGallery ?: onPick).invoke() },
                                onReturnToPrimary=onReturnToPrimary?.let { action -> { viewer=null; primaryCopy?.close(); session.revokeOperations(); action() } },
                                onRestoreFromHidden={ids ->
                                    restoreRequest?.first?.close()
                                    restoreRequest=runCatching {uk.co.traynor.privategallery.core.hidden.HiddenRestoreAccess(context,session) to ids}.getOrNull()
                                },
                                onCopyFromPrimary={
                                    primaryCopy=runCatching {createPrimaryCopy?.invoke(session)}.getOrNull()
                                    if(primaryCopy==null) error="Unlock Primary and Hidden before copying."
                                },
                            )}
                        }
                        viewer?.let {view ->FullscreenMediaViewer(view.first,MediaViewerSource.VAULT,view.second,onClose={viewer=null},
                            initiallyConcealed=requireReopen,
                            onPrivacyConcealed={view.second=it},
                            initialFailedEntries=failedViewerEntries.value,
                            onImageFailure={failedViewerEntries.value=failedViewerEntries.value+it},
                            onReopen={
                                if(session.isUnlocked && viewer === view && alive.get() && editor.isActive()) {onReopened();true}
                                else {viewer=null;onLock();false}
                            },
                            onLoadFavouriteState={id,done ->request({repository.favouriteCollection()?.let {collection->repository.itemsInCollection(collection.id).any {it.id==id}}}) {result->done(result.getOrNull())}},
                            onToggleFavourite={id,done ->request({
                                repository.favouriteCollection()?.let {collection ->
                                    val selected=repository.itemsInCollection(collection.id).any {it.id==id}
                                    if(selected)repository.removeItemsFromCollection(collection.id,listOf(id)) else repository.addItemsToCollection(collection.id,listOf(id))
                                    repository.itemsInCollection(collection.id).any {it.id==id}
                                }
                            }) {result ->done(result.getOrNull());revision++;refreshFavourite()}},
                            onLoadMediaDetails={id,done->request({repository.items().first {it.id==id}},done)},
                            onAdjustMediaDate={id,time,zone,done->request({repository.adjustMediaDate(id,time,zone)}) {r->r.onSuccess {revision++};done(r)}},
                            onLoadProtectedBytes={id,done ->request({repository.readForDisplay(id)}) {result->if(viewer===view)done(result) else result.getOrNull()?.fill(0)}},
                            onLoadVideoSession={id,cancelled,done ->request({check(!cancelled());repository.mediaSize(id);uk.co.traynor.privategallery.core.hidden.HiddenVideoSession(repository,id,cancelled)},done)},
                            onLoadEditorBytes={id,cancelled,done ->request({editor.read(id) {cancelled() || viewer !== view}}) {result->if(viewer===view && !cancelled())done(result) else result.getOrNull()?.fill(0)}},
                            editingVaultLabel="Hidden Vault",
                            onSaveEditedCopy={id,bytes,cancelled,done ->saveEdit(id,bytes,null,cancelled,done)},
                            onSaveAiCopy={id,bytes,provenance,cancelled,done ->saveEdit(id,bytes,provenance,cancelled,done)},
                            onLoadVideoBytes={id,cancelled,progress,done ->request({check(!cancelled());repository.readForDisplay(id).also {if(cancelled()){it.fill(0);throw kotlinx.coroutines.CancellationException("Cancelled")}}}) {result ->progress(100);done(result)}},
                            onDeleteFromVault={entry ->change({repository.trash(listOf(entry.id))}) {viewer=null}},
                            onAddToCollection={entry ->viewerCollectionItem=entry.id;request({repository.collections()}) {it.onSuccess {list ->collections=list}}},
                        )}
                    }
                }
            }
            restoreRequest?.let {request ->HiddenRestoreDialog(request.first,request.second) {message ->
                restoreRequest=null;error=message;revision++;refreshFavourite()
            }}
            primaryCopy?.let {access -> PrimaryCopyDialog(access,
                onClose={primaryCopy=null;revision++;refreshFavourite();error="Copy closed. Completed items remain in Hidden; Primary originals unchanged."}, onComplete={message ->primaryCopy=null;error=message;revision++;refreshFavourite()}) }
            viewerCollectionItem?.let {id ->AlertDialog(onDismissRequest={viewerCollectionItem=null},title={Text("Add to collection")},
                text={Column {if(collections.isEmpty())Text("Create a collection in Hidden first.")
                    collections.forEach {collection ->TextButton(onClick={change({repository.addItemsToCollection(collection.id,listOf(id))}) {viewerCollectionItem=null}}) {Text(collection.name)}}}},
                confirmButton={TextButton(onClick={viewerCollectionItem=null}) {Text("Close")}})}
        }
}
