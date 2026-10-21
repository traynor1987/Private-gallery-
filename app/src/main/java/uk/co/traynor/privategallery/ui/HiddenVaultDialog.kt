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
    onBrowseDeviceGallery: (() -> Unit)? = null,
    createPrimaryCopy: ((HiddenSession) -> uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?)? = null,
) {
    if(!BuildConfig.HIDDEN_PROTOTYPE)return
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
        externalSession=externalSession,showEntry=showEntry,onAuthenticationChanged=onAuthenticationChanged,
        unlockedContent={session,close,lockForPicker ->
            HiddenVaultContent(storageContext,session,pending,initialDestination,createPrimaryCopy,onBrowseDeviceGallery,onImportsConsumed={pending=emptyList()},onClose=close,onLock=lockForPicker,onScopedDestinationChanged=onScopedDestinationChanged,onNavigateOutside=onNavigateOutside,onPick={
                // The picker lives above authenticated content so its result survives session revocation.
                pickerActive.set(true);pickerChanged(true);lockForPicker()
                try {picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}
                catch(_:Exception) {pickerActive.set(false);pickerChanged(false)}
            })
        },onDismiss=onDismiss)
}

@Composable
private fun HiddenVaultContent(
    context:Context,session:HiddenSession,pending:List<Uri>,initialDestination:Route,createPrimaryCopy:((HiddenSession)->uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?)?,onBrowseDeviceGallery:(()->Unit)?,onImportsConsumed:()->Unit,onClose:()->Unit,onLock:()->Unit,onScopedDestinationChanged:(Route)->Unit,onNavigateOutside:(AppNavigationDestination)->Unit,onPick:()->Unit,
) {
    val repository=remember(context,session) {runCatching {HiddenRepository(context,session)}.getOrNull()}
    if(repository==null) {LaunchedEffect(Unit) {session.lock();onClose()};return}
    val alive=remember {AtomicBoolean(true)}
    val scope=rememberCoroutineScope()
    var revision by remember {mutableIntStateOf(0)}
    var error by remember {mutableStateOf("")}
    var chooseSource by remember {mutableStateOf(false)}
    var primaryCopy by remember {mutableStateOf<uk.co.traynor.privategallery.core.hidden.PrimaryCopyAccess?>(null)}
    DisposableEffect(Unit) { onDispose { primaryCopy?.close() } }
    var favouriteName by remember {mutableStateOf<String?>(null)}
    var favouriteIds by remember {mutableStateOf<Set<String>>(emptySet())}
    var route by remember {mutableStateOf(initialDestination)}
    var viewer by remember {mutableStateOf<Pair<List<ViewerMediaEntry>,Int>?>(null)}
    var viewerCollectionItem by remember {mutableStateOf<String?>(null)}
    var collections by remember {mutableStateOf<List<uk.co.traynor.privategallery.core.vault.VaultCollection>>(emptyList())}
    val close={session.lock();viewer=null;onClose()}
    DisposableEffect(Unit) {onDispose {alive.set(false);session.revokeOperations()}}
    BackHandler {close()}
    fun release(value:Any?) {when(value){is ByteArray->value.fill(0);is Bitmap->value.recycle()}}
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
    LaunchedEffect(Unit) {request({repository.reconcile()}) {};refreshFavourite()}
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
                                onOpenViewer={entries,_,index ->viewer=entries to index},modifier=Modifier.padding(padding),
                                onLoadRecentlyDeleted={done ->request({repository.recentlyDeleted()}) {it.onSuccess(done)}},
                                onMoveItemsToRecentlyDeleted={ids,done ->change({repository.trash(ids)},done)},
                                onChangeRecentlyDeleted={item,delete,done ->change({if(delete)repository.deletePermanently(item.id) else repository.restore(item.id)},done)},
                                vaultTitle=if(route==Route.FAVOURITE)favouriteName ?: "Favourite" else "Hidden Vault",
                                statusLabel="Hidden · TEST ONLY",
                                allowGeneration=false,allowBiometrics=false,allowRestore=false,onRequestImport={chooseSource=true},
                            )}
                        }
                        viewer?.let {view ->FullscreenMediaViewer(view.first,MediaViewerSource.VAULT,view.second,onClose={viewer=null},
                            onLoadProtectedBytes={id,done ->request({repository.read(id)},done)},
                            onLoadEditorBytes={id,cancelled,done ->request({check(!cancelled());repository.read(id).also {if(cancelled()){it.fill(0);throw kotlinx.coroutines.CancellationException("Cancelled")}}},done)},
                            onLoadVideoBytes={id,cancelled,progress,done ->request({check(!cancelled());repository.read(id).also {if(cancelled()){it.fill(0);throw kotlinx.coroutines.CancellationException("Cancelled")}}}) {result ->progress(100);done(result)}},
                            onDeleteFromVault={entry ->change({repository.trash(listOf(entry.id))}) {viewer=null}},
                            onAddToCollection={entry ->viewerCollectionItem=entry.id;request({repository.collections()}) {it.onSuccess {list ->collections=list}}},
                        )}
                    }
                }
            }
            if (chooseSource) AlertDialog(onDismissRequest={chooseSource=false}, title={Text("Add media")},
                text={Column {
                    TextButton(onClick={chooseSource=false;(onBrowseDeviceGallery ?: onPick).invoke()}) {Text("From device Gallery")}
                    TextButton(onClick={
                        chooseSource=false
                        primaryCopy=runCatching {createPrimaryCopy?.invoke(session)}.getOrNull()
                        if(primaryCopy==null) error="Unlock Primary and Hidden before copying."
                    }) {Text("From Primary Vault")}
                }}, confirmButton={TextButton(onClick={chooseSource=false}) {Text("Cancel")}})
            primaryCopy?.let {access -> PrimaryCopyDialog(access,
                onClose={primaryCopy=null;revision++;refreshFavourite();error="Copy closed. Completed items remain in Hidden; Primary originals unchanged."}, onComplete={message ->primaryCopy=null;error=message;revision++;refreshFavourite()}) }
            viewerCollectionItem?.let {id ->AlertDialog(onDismissRequest={viewerCollectionItem=null},title={Text("Add to collection")},
                text={Column {if(collections.isEmpty())Text("Create a collection in Hidden first.")
                    collections.forEach {collection ->TextButton(onClick={change({repository.addItemsToCollection(collection.id,listOf(id))}) {viewerCollectionItem=null}}) {Text(collection.name)}}}},
                confirmButton={TextButton(onClick={viewerCollectionItem=null}) {Text("Close")}})}
        }
}
