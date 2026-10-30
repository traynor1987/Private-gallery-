package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Context
import android.content.Intent
import android.net.Network
import android.net.Uri
import android.os.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.FilterInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** One parent-held capability; keys, HiddenSession and repository never leave this process. */
internal class HiddenVpnBrowserBroker(
    private val context:Context,
    private val session:HiddenSession,
    val network:Network,
    private val authorized:()->Boolean,
    private val onEnded:()->Unit,
    private val onPresentationEnded:(Int,Long)->Unit={_,_->},
    val mode:HiddenConnectionPolicy.Mode=HiddenConnectionPolicy.Mode.VPN_ONLY,
    private val torUnixPath:String?=null,
    private val onPanic:()->Unit={},
    private val hiddenCaptureAllowed:()->Boolean={false},
    private val hiddenTheme:()->uk.co.traynor.privategallery.core.ui.AppTheme={uk.co.traynor.privategallery.core.ui.AppTheme.SYSTEM},
    private val onManageDownloads:()->Unit={},
) : Binder(),Closeable {
    private val closed=AtomicBoolean()
    private val authorityRevoked=AtomicBoolean()
    private val cleanupQueued=AtomicBoolean()
    private val hardAt=java.util.concurrent.atomic.AtomicLong(-1)
    private val hardReason=java.util.concurrent.atomic.AtomicInteger(DEATH)
    private val authority=session.authority()
    private val gate=Any()
    private val privateApps=uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore(context.noBackupFilesDir,session,authority,mode==HiddenConnectionPolicy.Mode.TOR_ONLY,{valid()},{commit ->synchronized(gate){check(valid());commit.run()}})
    private val library=HiddenBrowserLibrary(context.noBackupFilesDir,session,authority,mode==HiddenConnectionPolicy.Mode.VPN_ONLY)
    private val worker=Executors.newSingleThreadExecutor()
    private val calls=ConcurrentHashMap.newKeySet<Call>()
    private val downloading=AtomicBoolean()
    private val discovering=AtomicBoolean()
    private val screenshotting=AtomicBoolean()
    private val screenshotReaders=ConcurrentHashMap.newKeySet<ParcelFileDescriptor>()
    @Volatile private var downloadStatus=""
    @Volatile private var downloadedBytes=0L
    @Volatile private var downloadTotal=-1L
    @Volatile private var downloadCall:okhttp3.Call?=null
    private val downloadCancelled=AtomicBoolean(false)
    private var lastPinAttempt=0L
    private val uploadReaders=ConcurrentHashMap.newKeySet<UploadReader>()
    private val uploads=ConcurrentHashMap<String,String>()
    private var pickerGeneration=-1L
    private var pickerQueryId=""
    private var pickerQuery:HiddenUploadPickerQuery?=null
    val token=UUID.randomUUID().toString()
    private val storage=HiddenVpnStorageCleanup.prepare(context.applicationContext,token)
    private val proxy=try {if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY)HiddenVpnProxy.tor(checkNotNull(torUnixPath),::valid) else HiddenVpnProxy(network,::valid)} catch(failure:Throwable) {storage.close();HiddenVpnStorageCleanup.cleanAsync(context.applicationContext);throw failure}
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var child:IBinder?=null
    private var childPid=0
    private data class ClaimedProcess(val endpoint:IBinder,val pid:Int,val uid:Int)
    private val claimed=java.util.concurrent.atomic.AtomicReference<ClaimedProcess?>(null)
    private var revocationRegistration:HiddenSession.RevocationRegistration?=null
    private val revokedCleanup=Runnable {end(hardReason.get(),hardAt.get().coerceAtLeast(0),true)}
    /** This is the only synchronous session-listener body: no broker/session lock or callbacks. */
    private val revocationSignal=Runnable {markAndKill(DEATH,SystemClock.elapsedRealtime());if(cleanupQueued.compareAndSet(false,true))main.post(revokedCleanup)}
    private fun markAndKill(reason:Int,at:Long) {
        if(authorityRevoked.compareAndSet(false,true)){hardReason.set(reason);hardAt.set(at)}
        val process=claimed.get() ?: return
        if(process.pid>0 && process.pid!=Process.myPid() && process.endpoint.isBinderAlive && process.uid==Process.myUid()) {
            if(process.endpoint.isBinderAlive)runCatching {Process.killProcess(process.pid)}
        }
    }
    fun childIsAlive():Boolean=claimed.get()?.endpoint?.isBinderAlive==true
    @Volatile var profileClearAcknowledged=false; private set
    private var presentationLoss:Pair<Int,Long>?=null
    /** Main consumes recorded Binder loss before uncovering/foreground expiry, not just a queued callback. */
    fun deliverPendingPresentationLoss() {
        val loss=if(authorityRevoked.get() && hardAt.get()>=0)hardReason.get() to hardAt.get() else synchronized(gate) {presentationLoss}
        loss?.let {onPresentationEnded(it.first,it.second)}
    }
    private val retirementQueued=AtomicBoolean()
    private fun ownsRetirement()=synchronized(registry) {processOwner===this}
    private fun retireDedicatedProcess() {
        if(!ownsRetirement() || !retirementQueued.compareAndSet(false,true))return
        // This bounded OS-record/process phase starts after graceful child death (or
        // immediately after hard revocation); it grants no authority or transport lifetime.
        val observation=HiddenVpnRetirement(SystemClock.elapsedRealtime()+5_000)
        val expected=context.packageName+":hidden_vpn_browser"
        val childComponent=android.content.ComponentName(context,HiddenVpnBrowserActivity::class.java).flattenToString()
        val sweep=object:Runnable {
            override fun run() {
                if(!ownsRetirement())return
                var known=true;var pending=false;var ownProcesses=0
                val taskFacts=mutableListOf<String>()
                val manager=context.getSystemService(android.app.ActivityManager::class.java)
                try {
                    manager.appTasks.forEach {task ->
                        val info=task.taskInfo
                        val base=info?.baseActivity?.flattenToString();val top=info?.topActivity?.flattenToString()
                        val count=info?.numActivities ?: -1;val running=info?.isRunning ?: true
                        val original=info?.baseIntent?.component?.flattenToString()
                        val empty=HiddenVpnRetirement.knownEmptyTask(info!=null,count,running,base,top)
                        if(taskFacts.size<16)taskFacts+="task=${info?.taskId ?: -1},count=$count,running=$running,baseMissing=${base==null},topMissing=${top==null},baseChild=${base==childComponent},topChild=${top==childComponent},originalChild=${original==childComponent},empty=$empty"
                        if(!HiddenVpnRetirement.knownTask(info!=null,count,running,base,top,original))known=false
                        if(HiddenVpnRetirement.pendingTask(info!=null,count,running,base,top,original,childComponent))pending=true
                        // An empty historical child root retains its base intent. Remove that exact
                        // shell too, canceling pending relaunches; never remove a Main-root task.
                        if(HiddenVpnRetirement.removableTask(info!=null,count,running,base,top,original,childComponent) && ownsRetirement())task.finishAndRemoveTask()
                    }
                    // Enumerate immediately before killing: never act on a remembered replacement PID.
                    checkNotNull(manager.runningAppProcesses).forEach {process ->
                        if(HiddenVpnRetirement.ownsProcess(process.uid,Process.myUid(),process.pid,Process.myPid(),process.processName,expected)) {
                            pending=true;ownProcesses++
                            if(ownsRetirement())Process.killProcess(process.pid)
                        }
                    }
                } catch(_:Exception) {known=false}
                when(observation.observe(SystemClock.elapsedRealtime(),ownsRetirement(),known,pending)) {
                    HiddenVpnRetirement.Decision.RELEASE -> {
                        synchronized(registry) {if(processOwner===this@HiddenVpnBrowserBroker)processOwner=null}
                        HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
                    }
                    HiddenVpnRetirement.Decision.SWEEP -> main.postDelayed(this,100)
                    HiddenVpnRetirement.Decision.EXHAUSTED -> android.util.Log.e("HiddenVpnRetirement","Dedicated process retirement unavailable; reuse remains closed; known=$known pending=$pending ownProcesses=$ownProcesses tasks=$taskFacts")
                    HiddenVpnRetirement.Decision.STALE -> Unit
                }
            }
        }
        main.post(sweep)
    }
    private val death=IBinder.DeathRecipient {
        end(DEATH,SystemClock.elapsedRealtime(),false)
        revocationRegistration?.close();revocationRegistration=null
        retireDedicatedProcess()
        HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
    }
    val active get() = !closed.get() && !authorityRevoked.get()
    init {
        revocationRegistration=session.onRevoked(authority,revocationSignal)
        if(!authorityRevoked.get())synchronized(registry) {registry[token]=this}
    }
    fun valid():Boolean = !closed.get() && !authorityRevoked.get() && authorized() && session.isAuthorityCurrent(authority)
    fun launch() {
        check(valid())
        synchronized(registry) {
            val old=processOwner
            check(old==null) {"Previous browser process is still closing"}
            check(dedicatedProcesses().isEmpty()) {"Previous browser process is still alive"}
            processOwner=this
        }
        try {
            val extras=Bundle().apply {putBinder(EXTRA,this@HiddenVpnBrowserBroker)}
            context.startActivity(Intent(context,HiddenVpnBrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtras(extras))
            main.postDelayed({
                if(child==null && !closed.get()) {
                    // No endpoint means no browser was authorized. A stalled cold launch cannot
                    // reserve foreground handoff authority indefinitely.
                    runCatching {dedicatedProcesses()}.getOrDefault(emptyList()).forEach {if(it.pid!=Process.myPid())Process.killProcess(it.pid)}
                    end(DEATH,SystemClock.elapsedRealtime(),false)
                }
            },10_000)
        } catch(failure:Throwable) {end(DEATH,SystemClock.elapsedRealtime(),false);throw failure}
    }
    private fun dedicatedProcesses():List<android.app.ActivityManager.RunningAppProcessInfo> =
        checkNotNull(context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses)
            .filter {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_vpn_browser"}
    /** Same-UID diagnostic returns two routing/settings facts, never URLs, cookies or credentials. */
    fun childSecuritySnapshot():Pair<Boolean,Boolean> {
        check(valid());val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken(CHILD_DESCRIPTOR);check(checkNotNull(child).transact(CHILD_SECURITY,request,reply,0));reply.readException();return (reply.readInt()==1) to (reply.readInt()==1)}finally{request.recycle();reply.recycle()}
    }
    internal data class PresentationState(val secure:Boolean,val covered:Boolean,val theme:uk.co.traynor.privategallery.core.ui.AppTheme)
    /** Same capability diagnostics: no browsing content, credentials or window titles. */
    internal fun childPresentationSnapshot():PresentationState {
        check(valid());val request=Parcel.obtain();val reply=Parcel.obtain()
        try {request.writeInterfaceToken(CHILD_DESCRIPTOR);check(checkNotNull(child).transact(CHILD_PRESENTATION,request,reply,0));reply.readException()
            return PresentationState(reply.readInt()==1,reply.readInt()==1,uk.co.traynor.privategallery.core.ui.ThemePreference.decode(reply.readString()))
        }finally{request.recycle();reply.recycle()}
    }
    private fun caller() {check(Binder.getCallingUid()==Process.myUid() && valid());check(childPid==Binder.getCallingPid())}
    override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
        if(code==INTERFACE_TRANSACTION) {reply?.writeString(DESCRIPTOR);return true}
        data.enforceInterface(DESCRIPTOR)
        val result=checkNotNull(reply)
        try {
            if(code==CLAIM) {
                synchronized(gate) {
                    check(Binder.getCallingUid()==Process.myUid() && Binder.getCallingPid()!=Process.myPid() && valid() && child==null)
                    val endpoint=checkNotNull(data.readStrongBinder());endpoint.linkToDeath(death,0)
                    child=endpoint;childPid=Binder.getCallingPid();claimed.set(ClaimedProcess(endpoint,childPid,Binder.getCallingUid()))
                    if(authorityRevoked.get() || !valid()){markAndKill(DEATH,SystemClock.elapsedRealtime());throw SecurityException("Revoked claim")}
                    result.writeNoException();result.writeTypedObject(network,0);result.writeInt(proxy.port);result.writeString(proxy.password);result.writeString(token);result.writeString(mode.name);result.writeInt(if(valid() && hiddenCaptureAllowed())1 else 0);result.writeString(uk.co.traynor.privategallery.core.ui.ThemePreference.encode(hiddenTheme()))
                }
            } else if(code==END || code==CLEANED) {
                check(Binder.getCallingUid()==Process.myUid() && childPid==Binder.getCallingPid())
                if(code==END) {
                    val reason=data.readInt();require(reason in setOf(RETURN,BACKGROUND,SCREEN_OFF))
                    val at=data.readLong();require(at in 0..SystemClock.elapsedRealtime())
                    val manage=if(data.dataAvail()>0)data.readInt().also {require(it in 0..1)}==1 else false
                    val switchName=if(data.dataAvail()>0)data.readString() else null
                    require(data.dataAvail()==0)
                    if(switchName!=null) {
                        caller() // Same live, claimed child; stale capabilities cannot select a mode.
                        require(reason==RETURN && !manage)
                        val selected=HiddenConnectionPolicy.Mode.valueOf(switchName) // Never parse with a fallback.
                        require(selected!=HiddenConnectionPolicy.Mode.VPN_TOR && selected!=mode)
                        // This is only a selection, never network/authentication readiness. Persist
                        // before hard retirement so returning Main can observe it on its first start.
                        check(context.getSharedPreferences("hidden-browser-v16",Context.MODE_PRIVATE).edit().putString("mode",selected.name).commit())
                    }
                    val graceful=reason==RETURN && switchName==null && valid()
                    // Keep the RETURN retirement publication ahead of its resulting death
                    // callback, which must not misclassify a deliberate mode switch as a crash.
                    if(switchName!=null)synchronized(gate) {end(reason,at,false,false)}
                    else end(reason,at,false,graceful)
                    if(manage && graceful)main.post {onManageDownloads()}
                } else profileClearAcknowledged=data.readInt()==1
                result.writeNoException()
            } else {
                caller()
                when(code) {
                    PRIVATE_APPS -> {
                        require(data.dataAvail()<=uk.co.traynor.privategallery.core.privateapps.PrivateAppCommands.MAX_COMMAND_BYTES+128)
                        val action=data.readInt();val size=data.readInt()
                        require(size in -1..uk.co.traynor.privategallery.core.privateapps.PrivateAppCommands.MAX_COMMAND_BYTES)
                        val bytes=if(size<0)null else ByteArray(size).also{data.readByteArray(it)}
                        try {
                            val id=data.readString().orEmpty();require(data.dataAvail()==0)
                            val state=uk.co.traynor.privategallery.core.privateapps.PrivateAppCommands.execute(privateApps,action,bytes,id)
                            val encoded=state.encode()
                            try {synchronized(gate){check(valid());result.writeNoException();result.writeInt(encoded.size);result.writeByteArray(encoded)}}
                            finally{encoded.fill(0)}
                        }finally{bytes?.fill(0)}
                    }
                    LIBRARY -> {
                        val action=data.readInt()
                        val url=data.readString().orEmpty()
                        require(url.length<=2048)
                        val supplied=action in 1..2 && data.dataAvail()>0
                        val title=if(supplied)checkNotNull(data.readString()) else ""
                        val timestamp=if(supplied)data.readLong() else 0L
                        val enabled=if(action==4)data.readInt().also {require(it in 0..1)}==1 else false
                        require(data.dataAvail()==0)
                        require(HiddenBrowserLibrary.validTitle(title) && timestamp in 0..System.currentTimeMillis())
                        synchronized(gate) {
                            check(valid())
                            when(action) {
                                0 -> Unit
                                1 -> {require(HiddenConnectionPolicy.safeUrl(url,mode));library.visit(url,title,timestamp)}
                                2 -> {require(HiddenConnectionPolicy.safeUrl(url,mode));library.bookmark(url,title,timestamp)}
                                3 -> library.removeBookmark(url)
                                4 -> library.saveHistory(enabled)
                                5 -> library.clearHistory()
                                6 -> library.clearBrowsingData()
                                else -> error("Unsupported Hidden library request")
                            }
                            val state=librarySnapshot(library)
                            check(valid());result.writeNoException();result.writeString(state)
                        }
                    }
                    PANIC -> {result.writeNoException();main.post {onPanic()};end(RETURN,SystemClock.elapsedRealtime(),true)}
                    CHECK -> {result.writeNoException();result.writeInt(1);result.writeString(downloadStatus);result.writeInt(if(valid() && hiddenCaptureAllowed())1 else 0);result.writeString(uk.co.traynor.privategallery.core.ui.ThemePreference.encode(hiddenTheme()));result.writeInt(if(downloading.get())1 else 0);result.writeLong(downloadedBytes);result.writeLong(downloadTotal)}
                    LIST -> {
                        val generation=data.readLong();val queryId=data.readString().orEmpty();val offset=data.readInt()
                        val accept:Array<String> = data.createStringArray()?.map {requireNotNull(it)}?.toTypedArray() ?: emptyArray()
                        require(generation>=0 && accept.size<=64 && accept.all {it.length<=256})
                        synchronized(gate) {
                            check(valid())
                            if(queryId.isEmpty()) {
                                require(offset==0 && generation>pickerGeneration)
                                val source=repository().items()
                                check(valid())
                                pickerQuery=pickerSnapshot(source,accept)
                                pickerGeneration=generation;pickerQueryId=UUID.randomUUID().toString()
                            }
                            require(generation==pickerGeneration && (queryId.isEmpty() || queryId==pickerQueryId))
                            val snapshot=checkNotNull(pickerQuery)
                            val response=pickerPage(snapshot,generation,pickerQueryId,offset)
                            check(valid());require(response.length<=200_000)
                            result.writeNoException();result.writeString(response)
                        }
                    }
                    PREVIEW -> {
                        val generation=data.readLong();val queryId=data.readString().orEmpty();val id=checkNotNull(data.readString())
                        synchronized(gate) {check(valid());require(generation==pickerGeneration && queryId==pickerQueryId && pickerQuery?.contains(id)==true)}
                        val repo=repository()
                        require(repo.items().any {it.id==id && !it.vaultOnly && it.state==uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE})
                        val bitmap=repo.preview(id)
                        var scaled:android.graphics.Bitmap?=null
                        val encoded=try {
                            val ratio=minOf(1f,256f/maxOf(bitmap.width,bitmap.height))
                            scaled=if(ratio<1f)android.graphics.Bitmap.createScaledBitmap(bitmap,(bitmap.width*ratio).toInt().coerceAtLeast(1),(bitmap.height*ratio).toInt().coerceAtLeast(1),true) else bitmap
                            java.io.ByteArrayOutputStream().use {out ->check(checkNotNull(scaled).compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
                        } finally {if(scaled!==bitmap)scaled?.recycle();bitmap.recycle()}
                        try {synchronized(gate) {
                            check(valid());require(generation==pickerGeneration && queryId==pickerQueryId && pickerQuery?.contains(id)==true)
                            require(encoded.size<=300_000);result.writeNoException();result.writeByteArray(encoded)
                        }} finally {encoded.fill(0)}
                    }
                    UPLOAD -> {
                        val id=checkNotNull(data.readString());require(repository().items().any {it.id==id && !it.vaultOnly})
                        val grant=UUID.randomUUID().toString();uploads[grant]=id
                        result.writeNoException();result.writeString("content://${context.packageName}.hiddenbrowser/$token/$grant")
                    }
                    DISCOVER_PRIVATE_APP -> {
                        require(data.dataAvail()<=8192)
                        val url=checkNotNull(data.readString());require(data.dataAvail()==0)
                        require(uk.co.traynor.privategallery.core.privateapps.PrivateAppDiscovery.safe(url,mode==HiddenConnectionPolicy.Mode.TOR_ONLY))
                        check(discovering.compareAndSet(false,true))
                        try {
                            val app=uk.co.traynor.privategallery.core.privateapps.PrivateAppMetadataTransport.discover(url,mode==HiddenConnectionPolicy.Mode.TOR_ONLY,
                                uk.co.traynor.privategallery.core.privateapps.PrivateAppMetadataTransport.hiddenProxy(proxy.port,proxy.password),::valid)
                            val bytes=uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog(mode==HiddenConnectionPolicy.Mode.TOR_ONLY,listOf(app)).encode()
                            try {synchronized(gate){check(valid());result.writeNoException();result.writeInt(bytes.size);result.writeByteArray(bytes)}}finally{bytes.fill(0)}
                        }finally{discovering.set(false)}
                    }
                    DOWNLOAD_CANCEL -> {
                        require(data.dataAvail()==0)
                        synchronized(gate) {check(valid());downloadCancelled.set(true);downloadCall?.cancel();if(downloading.get())downloadStatus="Cancelling download…"}
                        result.writeNoException()
                    }
                    VERIFY_PRIVATE_APP_PIN -> {
                        require(data.dataAvail()<=256)
                        val pin=checkNotNull(data.readString()).toCharArray()
                        var candidate:ByteArray?=null
                        try {
                            require(data.dataAvail()==0 && pin.size in 6..12 && pin.all {it in '0'..'9'})
                            synchronized(gate) {check(valid());val now=SystemClock.elapsedRealtime();check(now-lastPinAttempt>=1000);lastPinAttempt=now}
                            candidate=runCatching {uk.co.traynor.privategallery.core.hidden.HiddenCredentialStore(context).unlock(pin)}.getOrNull()
                            val matched=session.beginOperation().use {op ->synchronized(gate) {check(valid() && session.isCurrent(op));candidate?.let {java.security.MessageDigest.isEqual(it,op.key())}==true}}
                            result.writeNoException();result.writeInt(if(matched)1 else 0)
                        }finally {pin.fill('\u0000');candidate?.fill(0)}
                    }
                    SCREENSHOT -> {
                        val confirmed=data.readInt()==1
                        val input=checkNotNull(data.readTypedObject(ParcelFileDescriptor.CREATOR))
                        var admitted=false
                        try {
                            require(data.dataAvail()==0 && confirmed)
                            check(valid() && hiddenCaptureAllowed())
                            admitted=screenshotting.compareAndSet(false,true);check(admitted)
                            synchronized(gate){check(valid());screenshotReaders.add(input)}
                            val expiry=Runnable {runCatching {input.close()}}
                            main.postDelayed(expiry,15_000)
                            worker.execute {
                                try {
                                    val allowed={valid() && hiddenCaptureAllowed() && screenshotReaders.contains(input)}
                                    val outcome=runCatching {
                                        ParcelFileDescriptor.AutoCloseInputStream(input).use {raw ->
                                            var count=0L
                                            val stream=object:FilterInputStream(raw) {
                                                override fun read():Int {val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt() and 255}
                                                override fun read(b:ByteArray,off:Int,len:Int):Int {
                                                    check(allowed());val n=`in`.read(b,off,len)
                                                    if(n>0){count+=n;require(count<=uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy.MAX_PNG_BYTES)}
                                                    check(allowed());return n
                                                }
                                            }
                                            val repo=repository(allowed)
                                            val item=repo.importMedia("browser-screenshot.png","image/png",stream,vaultOnly=true,
                                                validateDownloadedMedia={media,size ->
                                                    val header=ByteArray(24);val tail=ByteArray(12)
                                                    try {
                                                        check(allowed());check(media.readAt(0,header,0,24)==24)
                                                        require(uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy.pngHeader(header,size))
                                                        check(media.readAt(size-12,tail,0,12)==12)
                                                        require(uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy.pngTrailer(tail));check(allowed())
                                                    } finally {header.fill(0);tail.fill(0)}
                                                },capturedAtEpochMillis=System.currentTimeMillis())
                                            repo.verify(item.id)
                                        }
                                    }
                                    if(uk.co.traynor.privategallery.BuildConfig.DEBUG)outcome.exceptionOrNull()?.let {
                                        android.util.Log.w("PrivateAppsScreenshot","Encrypted screenshot save failed: ${it.javaClass.simpleName}")
                                    }
                                    if(valid())downloadStatus=if(outcome.isSuccess)"Screenshot verified and saved encrypted in Hidden Vault." else "Screenshot could not be saved. No capture was exported."
                                } finally {main.removeCallbacks(expiry);screenshotReaders.remove(input);runCatching {input.close()};screenshotting.set(false)}
                            }
                            result.writeNoException()
                        } catch(failure:Throwable){screenshotReaders.remove(input);runCatching {input.close()};if(admitted)screenshotting.set(false);throw failure}
                    }
                    DOWNLOAD -> {
                        val url=checkNotNull(data.readString());val cookie=data.readString().orEmpty();val agent=data.readString().orEmpty()
                        require(HiddenConnectionPolicy.safeUrl(url,mode));require(cookie.length<=16384 && agent.length<=4096 && listOf(cookie,agent).none {it.contains('\r') || it.contains('\n')})
                        // Queue only after authenticated explicit child confirmation. The repository promotion
                        // is fenced again, so a late download cannot inherit a subsequent unlock.
                        check(downloading.compareAndSet(false,true))
                        downloadCancelled.set(false);downloadedBytes=0;downloadTotal=-1
                        downloadStatus="Downloading into Hidden Vault…"
                        worker.execute {
                            val outcome=runCatching {download(url,cookie,agent)}
                            if(valid())downloadStatus=if(outcome.isSuccess)"Download verified and saved encrypted in Hidden Vault." else if(downloadCancelled.get())"Download cancelled. No new item was saved." else "Download failed or unsupported. No new item was saved. Try a complete image, video, PDF, text or CSV GET URL again."
                            downloading.set(false)
                        }
                        result.writeNoException()
                    }
                    else -> return super.onTransact(code,data,reply,flags)
                }
            }
        } catch(_:Exception) {result.writeException(SecurityException("Hidden browser session unavailable"))}
        return true
    }
    private fun repository(extraAllowed:()->Boolean={true}):HiddenRepository {
        check(valid())
        return HiddenRepository(context,session,publishIfAllowed={commit ->synchronized(gate) {check(valid() && extraAllowed());commit()}})
    }
    fun uploadInfo(grant:String):uk.co.traynor.privategallery.core.vault.VaultItem {
        check(valid());val id=checkNotNull(uploads[grant]);return repository().items().single {it.id==id && !it.vaultOnly}
    }
    /** Chromium multipart needs stat size and seekable reads; never materialize plaintext files. */
    private inner class UploadReader(item:uk.co.traynor.privategallery.core.vault.VaultItem,private val grant:String):ProxyFileDescriptorCallback(),Closeable {
        private val id=item.id
        private val size=item.plaintextSize
        private val hash=item.plaintextSha256.copyOf()
        private val nonce=item.payloadNonce.copyOf()
        private val mime=item.mimeType
        private val released=AtomicBoolean()
        private val disposed=AtomicBoolean()
        private val buffer=java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
        @Volatile var descriptor:ParcelFileDescriptor?=null
        private fun current() {
            check(!released.get() && valid())
            val item=uploadInfo(grant)
            check(item.id==id && item.state==uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE && !item.vaultOnly &&
                item.plaintextSize==size && item.mimeType==mime && java.security.MessageDigest.isEqual(item.plaintextSha256,hash) &&
                java.security.MessageDigest.isEqual(item.payloadNonce,nonce))
        }
        override fun onGetSize():Long = try {current();size} catch(_:Exception) {throw android.system.ErrnoException("Hidden upload unavailable",android.system.OsConstants.EBADF)}
        override fun onRead(offset:Long,requested:Int,data:ByteArray):Int {
            var succeeded=false
            try {
                // The framework owns this bounded reply buffer; retain only its reference so
                // revocation/release can wipe it. A new read wipes the previous reply first.
                require(data.size<=1024*1024 && requested in 0..data.size && offset>=0)
                buffer.getAndSet(data)?.takeIf {it!==data}?.fill(0)
                data.fill(0)
                current()
                val count=minOf(requested.toLong(),(size-offset).coerceAtLeast(0)).toInt()
                var done=0
                while(done<count) {
                    current()
                    val read=repository().readRange(id,offset+done,data,done,count-done)
                    check(read in 1..(count-done));done+=read
                }
                current()
                succeeded=true;return count
            } catch(_:Exception) {throw android.system.ErrnoException("Hidden upload unavailable",android.system.OsConstants.EIO)}
            finally {if(!succeeded)data.fill(0)}
        }
        override fun onWrite(offset:Long,size:Int,data:ByteArray):Int {data.fill(0);throw android.system.ErrnoException("Read-only Hidden upload",android.system.OsConstants.EBADF)}
        override fun onRelease() {dispose()}
        fun dispose() {
            close()
            if(disposed.compareAndSet(false,true))uploadSlots.release()
        }
        override fun close() {
            if(!released.compareAndSet(false,true))return
            buffer.getAndSet(null)?.fill(0)
            hash.fill(0);nonce.fill(0)
            runCatching {descriptor?.close()}
            descriptor=null;uploadReaders.remove(this)
        }
    }
    fun openUpload(grant:String):ParcelFileDescriptor {
        val item=uploadInfo(grant)
        require(item.state==uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE && !item.vaultOnly && item.plaintextSize>=0)
        check(uploadSlots.tryAcquire()) {"Too many Hidden upload readers"}
        val reader=UploadReader(item,grant)
        try {
            repository().verify(item.id) // Full original digest before any native upload byte.
            synchronized(gate) {
                check(valid());reader.onGetSize() // Recheck captured identity after whole-file verification.
                uploadReaders.add(reader)
                val descriptor=checkNotNull(checkNotNull(context.getSystemService(android.os.storage.StorageManager::class.java))
                    .openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,reader,uploadCallbackHandler)).also {opened ->
                    try {
                        // AppFuse's default open reply permits page caching. A cached reread can
                        // bypass our revoked callback, so require direct I/O before publishing FD.
                        val file=opened.fileDescriptor
                        val flags=android.system.Os.fcntlInt(file,android.system.OsConstants.F_GETFL,0)
                        android.system.Os.fcntlInt(file,android.system.OsConstants.F_SETFL,flags or uploadDirectIoFlag())
                        check(android.system.Os.fcntlInt(file,android.system.OsConstants.F_GETFL,0) and uploadDirectIoFlag() != 0)
                    } catch(failure:Throwable) {opened.close();throw failure}
                }
                reader.descriptor=descriptor
                check(valid());reader.onGetSize()
                return descriptor
            }
        } catch(failure:Throwable) {reader.dispose();throw failure}
    }
    /** Readiness uses the same authenticated HTTP→private Unix SOCKS path as native WebView. */
    fun verifyTorReady() {
        check(mode==HiddenConnectionPolicy.Mode.TOR_ONLY && valid())
        val client=OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,java.net.InetSocketAddress(java.net.InetAddress.getByAddress(byteArrayOf(127,0,0,1)),proxy.port)))
            .proxyAuthenticator(object:Authenticator {override fun authenticate(route:Route?,response:Response):Request? = if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("hidden",proxy.password)).build()})
            .dns(object:Dns {override fun lookup(hostname:String):List<java.net.InetAddress> = throw java.net.UnknownHostException("Tor origin DNS prohibited")})
            .followRedirects(false).retryOnConnectionFailure(false).connectTimeout(30,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url("https://example.com/").build())
        try {
            synchronized(gate){check(valid());calls.add(call)}
            call.execute().use {response->check(response.code==200);check(checkNotNull(response.body).byteStream().read()!=-1);check(valid())}
        } finally {calls.remove(call);client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    private fun download(url:String,cookie:String,agent:String) {
        fun active()=valid() && !downloadCancelled.get()
        check(active())
        val builder=OkHttpClient.Builder()
        if(mode==HiddenConnectionPolicy.Mode.TOR_ONLY) {
            builder.proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,java.net.InetSocketAddress(java.net.InetAddress.getByAddress(byteArrayOf(127,0,0,1)),proxy.port)))
                .proxyAuthenticator(object:Authenticator {override fun authenticate(route:Route?,response:Response):Request? = if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("hidden",proxy.password)).build()})
                .dns(object:Dns {override fun lookup(hostname:String):List<java.net.InetAddress> = throw java.net.UnknownHostException("Tor origin DNS prohibited")})
        } else builder.proxy(java.net.Proxy.NO_PROXY).socketFactory(network.socketFactory)
            .dns(object:Dns {
                override fun lookup(hostname:String):List<java.net.InetAddress> {
                    check(valid());require(!hostname.endsWith(".onion",true))
                    return network.getAllByName(hostname).toList().also {require(it.isNotEmpty() && it.all(HiddenVpnProxy::publicAddress))}
                }
            })
        val client=builder
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).cache(null)
            .connectTimeout(30,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(60,TimeUnit.MINUTES).build()
        try {
            val request=Request.Builder().url(url).header("User-Agent",agent).apply {if(cookie.isNotEmpty())header("Cookie",cookie)}.build()
            val call=client.newCall(request)
            synchronized(gate) {check(active());calls.add(call);downloadCall=call}
            try {call.execute().use {response ->
                require(response.code==200 && response.header("Content-Range")==null)
                val body=checkNotNull(response.body);val mime=body.contentType()?.let {"${it.type}/${it.subtype}"}.orEmpty()
                require(uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.acceptsDownload(mime))
                val expected=body.contentLength();var count=0L
                val limit=if(uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.isDocument(mime))uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.maxBytes(mime) else 2L*1024*1024*1024
                require(expected<0 || expected in 1..limit);downloadTotal=expected
                val input=object:FilterInputStream(body.byteStream()) {
                    override fun read():Int {val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255}
                    override fun read(b:ByteArray,off:Int,len:Int):Int {check(active());val n=`in`.read(b,off,len);if(n>0){count+=n;require(count<=limit);downloadedBytes=count};check(active());if(n<0 && expected>=0)check(count==expected);return n}
                }
                val repo=repository(::active);val name=if(uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.isDocument(mime))"Hidden download."+uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.extension(mime) else "Hidden download"
                val item=repo.importMedia(name,mime,input,validateDownloadedMedia=hiddenDownloadedMediaValidator(mime,{!active()}));repo.verify(item.id)
            }} finally {calls.remove(call);downloadCall=null}
        } finally {client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    override fun close() = end(RETURN,SystemClock.elapsedRealtime(),true)
    private fun end(reason:Int,stoppedAt:Long,requestChild:Boolean,gracefulReturn:Boolean=false) {
        // Owner-close is hard even before HiddenSession.lock. Only an explicit still-current
        // child RETURN can retain a pinned lifetime while asynchronous data clearing finishes.
        val graceful=gracefulReturn && !authorityRevoked.get()
        if(!graceful)markAndKill(reason,stoppedAt)
        val endpoint:IBinder?
        synchronized(gate) {
            if(!closed.compareAndSet(false,true)) {
                if(!graceful)main.post {revocationRegistration?.close();revocationRegistration=null;HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)}
                return
            }
            presentationLoss=reason to stoppedAt
            proxy.close();calls.forEach {it.cancel()};uploadReaders.toList().forEach {it.close()};uploadReaders.clear();uploads.clear();pickerQuery=null;pickerQueryId=""
            endpoint=child
        }
        synchronized(registry) {registry.remove(token)}
        screenshotReaders.toList().forEach {runCatching {it.close()}};screenshotReaders.clear()
        worker.shutdownNow();storage.close()
        // A graceful RETURN keeps its signal registered until actual child death: locking
        // during data clearing must still terminate non-proxied UDP/RTC immediately.
        if(!graceful || endpoint?.isBinderAlive!=true){revocationRegistration?.close();revocationRegistration=null}
        main.post {onPresentationEnded(reason,stoppedAt);onEnded()}
        if(graceful && requestChild && endpoint?.isBinderAlive==true) {
            val request=Parcel.obtain()
            try {request.writeInterfaceToken(CHILD_DESCRIPTOR);endpoint.transact(CHILD_SHUTDOWN,request,null,IBinder.FLAG_ONEWAY)} catch(_:Exception) {} finally {request.recycle()}
        }
        // Data-clearing runs with the dead immutable Network pin and no admitted proxy/calls.
        // A hung provider cannot keep a browser process around indefinitely.
        if(graceful)main.postDelayed({
            if(endpoint?.isBinderAlive==true && childPid>0 && childPid!=Process.myPid())Process.killProcess(childPid)
            HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
        },5_000)
        if(!graceful || endpoint==null || !endpoint.isBinderAlive) {
            retireDedicatedProcess()
            HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
        }
    }
    companion object {
        // A remote duplicated FD can outlive its browser. Keep one bounded process-wide
        // callback actor alive to reject revoked reads until release; no per-session threads.
        private val uploadSlots=java.util.concurrent.Semaphore(32)
        private val uploadCallbackHandler by lazy {Handler(HandlerThread("hidden-upload-reader").apply {start()}.looper)}
        internal fun pickerSnapshot(items:List<uk.co.traynor.privategallery.core.vault.VaultItem>,accept:Array<String>):HiddenUploadPickerQuery =
            HiddenUploadPickerQuery(items.map {item ->HiddenUploadPickerQuery.Entry(item.id,item.displayName,item.mimeType,item.vaultOnly,item.state==uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE)}) {mime ->uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.accepts(mime,accept)}
        internal fun pickerPage(snapshot:HiddenUploadPickerQuery,generation:Long,queryId:String,offset:Int):String {
            val page=snapshot.page(offset)
            val json=JSONArray();page.forEach {json.put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime))}
            return JSONObject().put("query",queryId).put("generation",generation).put("total",snapshot.size())
                .put("restricted",snapshot.restrictedCount).put("types",snapshot.typeCount).put("next",offset+page.size).put("items",json).toString()
        }
        internal fun librarySnapshot(library:HiddenBrowserLibrary):String {
            val state=library.snapshot()
            return JSONObject().put("bookmarks",JSONArray(state.bookmarks.map {it.url})).put("history",JSONArray(state.history.map {it.url}))
                .put("bookmarkTitles",JSONArray(state.bookmarks.map {it.title})).put("bookmarkTimes",JSONArray(state.bookmarks.map {it.timestamp}))
                .put("historyTitles",JSONArray(state.history.map {it.title})).put("historyTimes",JSONArray(state.history.map {it.timestamp}))
                .put("saveHistory",state.saveHistory).toString().replace("\\/","/")
        }
        const val EXTRA="hidden_vpn_capability"
        const val DESCRIPTOR="uk.co.traynor.privategallery.HiddenVpnSession"
        const val CLAIM=IBinder.FIRST_CALL_TRANSACTION
        const val CHECK=IBinder.FIRST_CALL_TRANSACTION+1
        const val END=IBinder.FIRST_CALL_TRANSACTION+2
        const val LIST=IBinder.FIRST_CALL_TRANSACTION+3
        const val UPLOAD=IBinder.FIRST_CALL_TRANSACTION+4
        const val DISCOVER_PRIVATE_APP=IBinder.FIRST_CALL_TRANSACTION+11
        const val SCREENSHOT=IBinder.FIRST_CALL_TRANSACTION+14
        const val DOWNLOAD=IBinder.FIRST_CALL_TRANSACTION+5
        const val DOWNLOAD_CANCEL=IBinder.FIRST_CALL_TRANSACTION+12
        const val VERIFY_PRIVATE_APP_PIN=IBinder.FIRST_CALL_TRANSACTION+13
        const val CLEANED=IBinder.FIRST_CALL_TRANSACTION+6
        const val PREVIEW=IBinder.FIRST_CALL_TRANSACTION+7
        const val LIBRARY=IBinder.FIRST_CALL_TRANSACTION+8
        const val PRIVATE_APPS=IBinder.FIRST_CALL_TRANSACTION+10
        const val PANIC=IBinder.FIRST_CALL_TRANSACTION+9
        const val CHILD_DESCRIPTOR="uk.co.traynor.privategallery.HiddenVpnChild"
        const val CHILD_SHUTDOWN=IBinder.FIRST_CALL_TRANSACTION
        const val CHILD_SECURITY=IBinder.FIRST_CALL_TRANSACTION+1
        const val CHILD_PRESENTATION=IBinder.FIRST_CALL_TRANSACTION+2
        const val RETURN=1
        const val BACKGROUND=2
        const val SCREEN_OFF=3
        const val DEATH=4
        private val registry=mutableMapOf<String,HiddenVpnBrowserBroker>()
        private var processOwner:HiddenVpnBrowserBroker?=null
        /** Linux UAPI O_DIRECT is absent from public OsConstants. Build adjusts CPU_ABI
         * to this process's bitness; unknown ABIs refuse rather than guess a kernel flag. */
        @Suppress("DEPRECATION")
        internal fun uploadDirectIoFlag():Int = when(android.os.Build.CPU_ABI) {
            "arm64-v8a","x86_64","x86" -> 0x4000 // NDK asm-generic/fcntl.h: 00040000
            "armeabi-v7a" -> 0x10000 // NDK ARM asm/fcntl.h: 0200000
            else -> error("Unsupported private upload ABI")
        }
        /** Observes the stable-absence lease; only the retirement owner can release it. */
        internal fun admissionRetired():Boolean = synchronized(registry) {processOwner==null}
        fun hasDedicatedProcess(context:Context):Boolean = runCatching {
            checkNotNull(context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses)
                .any {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_vpn_browser"}
        }.getOrDefault(true)
        fun find(token:String):HiddenVpnBrowserBroker = synchronized(registry) {checkNotNull(registry[token]).also {check(it.valid())}}
    }
}
