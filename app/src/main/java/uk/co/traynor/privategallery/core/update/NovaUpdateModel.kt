package uk.co.traynor.privategallery.core.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File
import uk.co.traynor.privategallery.home2.NOVA_PACKAGE
import uk.co.traynor.privategallery.home2.downloadVerifiedNovaApk

/** Existing Updates & About flow; no Vault access or additional update channel. */
class NovaUpdateModel internal constructor(app: Application, private val releases: GithubReleaseUpdateService) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, GithubReleaseUpdateService())
    private val context = app.applicationContext
    private val preferences = context.getSharedPreferences("private-gallery-settings", 0)
    var state by mutableStateOf(NovaUpdateState()); private set
    var lastChecked by mutableStateOf(preferences.getString("update-last-checked", "Never") ?: "Never"); private set
    private var job: Job? = null
    private var apk: File? = null
    private var permissionPending = false
    @Volatile private var generation = 0L
    init {
        if (preferences.getLong("update-install-target",0)>0) {
            state=NovaUpdateState(UpdatePhase.INSTALLATION_REQUESTED,"Checking Android installation result")
            refreshInstalled()
            if(state.phase==UpdatePhase.INSTALLATION_REQUESTED)
                state=NovaUpdateState(UpdatePhase.CANCELLED,"Installation cancelled or not completed. Check for updates to retry.")
        }
    }
    private fun installed() = context.packageManager.getPackageInfo(context.packageName,0)
    fun refreshInstalled() {
        val target=preferences.getLong("update-install-target",0)
        val actual=installed()
        if(UpdateInstallPolicy.confirmedInstalled(target,actual.longVersionCode)) {
            preferences.edit().remove("update-install-target").commit()
            apk?.delete();apk=null
            state=NovaUpdateState(UpdatePhase.UP_TO_DATE,"${actual.versionName} · Up to date")
        }
    }
    fun check() {
        if(state.busy)return
        apk?.delete();apk=null
        state=NovaUpdateState(UpdatePhase.CHECKING,"Checking…")
        job=viewModelScope.launch {
            val actual=installed()
            val result=withContext(Dispatchers.IO) { releases.check(actual.versionName ?: "0",actual.longVersionCode) }
            state=when(result) {
                is UpdateCheck.Available -> NovaUpdateState(UpdatePhase.AVAILABLE,"${result.release.version.raw} · Build ${result.release.versionCode} available",result.release)
                is UpdateCheck.UpToDate -> NovaUpdateState(UpdatePhase.UP_TO_DATE,"${actual.versionName} · Up to date",result.release)
                is UpdateCheck.Failed -> NovaUpdateState(UpdatePhase.FAILED,result.reason.userMessage)
            }
            if(result !is UpdateCheck.Failed) {
                lastChecked=java.text.DateFormat.getDateTimeInstance().format(java.util.Date())
                preferences.edit().putString("update-last-checked",lastChecked).commit()
            }
        }
    }
    fun download(onReady: () -> Unit) {
        if(state.busy)return
        if(context.packageName!=NOVA_PACKAGE) {state=state.copy(phase=UpdatePhase.FAILED,message="Production updates require installed NovaOS.");return}
        if(state.phase==UpdatePhase.READY && apk?.isFile==true) {onReady();return}
        val release=state.release ?: return
        val ticket=++generation
        state=NovaUpdateState(UpdatePhase.DOWNLOADING,"Downloading…",release,total=release.apkSizeBytes ?: -1)
        job=viewModelScope.launch {
            try {
                val active=currentCoroutineContext()[Job]!!
                val file=withContext(Dispatchers.IO) {
                    val identity=checkNotNull(releases.verifiedIdentity(release))
                    check(identity.versionCode>installed().longVersionCode)
                    val meter=UpdateTransferMeter()
                    downloadVerifiedNovaApk(context,identity,expectedSize=release.apkSizeBytes,onProgress={ bytes,total ->
                        val sample=meter.sample(bytes,total)
                        if(sample!=null)viewModelScope.launch { if(generation==ticket && state.phase==UpdatePhase.DOWNLOADING)state=state.copy(bytes=bytes,total=total,bytesPerSecond=sample.bytesPerSecond) }
                    },cancelled={ !active.isActive || generation!=ticket },onVerifying={
                        viewModelScope.launch { if(generation==ticket && state.phase==UpdatePhase.DOWNLOADING)state=state.copy(phase=UpdatePhase.VERIFYING,message="Verifying package, signature and integrity…") }
                    }).also { if(!active.isActive || generation!=ticket) { it.delete();throw CancellationException() } }
                }
                if(ticket!=generation){file.delete();return@launch}
                apk=file
                state=state.copy(phase=UpdatePhase.READY,message="Ready to install")
                onReady()
            } catch(failure:CancellationException) { throw failure }
            catch(failure:Exception) {
                if(generation==ticket)state=state.copy(phase=UpdatePhase.FAILED,message=if(failure.message?.contains("storage",true)==true)"Insufficient storage. Free space and retry." else "Download or verification failed. Retry safely.")
            }
        }
    }
    override fun onCleared() {
        ++generation;job?.cancel()
        if(state.phase!=UpdatePhase.INSTALLATION_REQUESTED)apk?.delete()
        super.onCleared()
    }
    fun cancel() {
        if(state.phase==UpdatePhase.INSTALLATION_REQUESTED)return
        ++generation;job?.cancel();apk?.delete();apk=null
        state=state.copy(phase=UpdatePhase.CANCELLED,message="Download cancelled")
    }
    fun permissionResult(granted: Boolean) {
        permissionPending = false
        if(!granted && state.phase==UpdatePhase.READY)
            state=state.copy(message="Allow installs from NovaOS to continue. Your installed version is unchanged.")
    }
    fun requestInstall(launch: (Intent) -> Unit, permission: (Intent) -> Unit) {
        if(state.phase!=UpdatePhase.READY || permissionPending)return
        if(!context.packageManager.canRequestPackageInstalls()) {
            permissionPending = true
            try { permission(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
            catch(_:Exception) { permissionPending=false;state=state.copy(message="Android install permission settings are unavailable. Retry safely.") }
            return
        }
        val file=apk?.takeIf { it.isFile } ?: run {state=state.copy(phase=UpdatePhase.FAILED,message="Update file unavailable. Download again.");return}
        try {
            val release=checkNotNull(state.release)
            check(checkNotNull(release.versionCode)>installed().longVersionCode)
            val intent=Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file),"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).putExtra(Intent.EXTRA_RETURN_RESULT,true)
            // Start the supported intent directly; package-visibility query results are not authority.
            preferences.edit().putLong("update-install-target",release.versionCode!!).commit()
            state=state.copy(phase=UpdatePhase.INSTALLATION_REQUESTED,message="Installation requested · confirm in Android")
            launch(intent)
        } catch(_:Exception) {state=state.copy(phase=UpdatePhase.FAILED,message="Android installer could not be opened. Retry installation.")}
    }
    fun installResult(resultCode:Int) {
        refreshInstalled()
        if(state.phase==UpdatePhase.UP_TO_DATE)return
        state=state.copy(phase=if(resultCode==android.app.Activity.RESULT_CANCELED)UpdatePhase.CANCELLED else UpdatePhase.FAILED,
            message=if(resultCode==android.app.Activity.RESULT_CANCELED)"Installation cancelled · your existing NovaOS is unchanged" else "Android did not install the update. Retry safely.")
    }
}
