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
) : Binder(),Closeable {
    private val closed=AtomicBoolean()
    private val authorityRevoked=AtomicBoolean()
    private val cleanupQueued=AtomicBoolean()
    private val hardAt=java.util.concurrent.atomic.AtomicLong(-1)
    private val hardReason=java.util.concurrent.atomic.AtomicInteger(DEATH)
    private val authority=session.authority()
    private val library=HiddenBrowserLibrary(context.noBackupFilesDir,session,authority,mode==HiddenConnectionPolicy.Mode.VPN_ONLY)
    private val gate=Any()
    private val worker=Executors.newSingleThreadExecutor()
    private val calls=ConcurrentHashMap.newKeySet<Call>()
    private val downloading=AtomicBoolean()
    @Volatile private var downloadStatus=""
    private val pipes=ConcurrentHashMap.newKeySet<ParcelFileDescriptor>()
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
    private val death=IBinder.DeathRecipient {
        end(DEATH,SystemClock.elapsedRealtime(),false)
        revocationRegistration?.close();revocationRegistration=null
        synchronized(registry) {if(processOwner===this)processOwner=null}
        HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
    }
    val active get() = !closed.get() && !authorityRevoked.get()
    init {
        revocationRegistration=session.onRevoked(authority,revocationSignal)
        if(!authorityRevoked.get())synchronized(registry) {registry[token]=this}
    }
    fun valid():Boolean = !closed.get() && !authorityRevoked.get() && authorized() && runCatching {session.beginOperation(authority).use {session.isCurrent(it)}}.getOrDefault(false)
    fun launch() {
        check(valid())
        synchronized(registry) {
            val old=processOwner
            check(old==null || (old.closed.get() && old.child?.isBinderAlive!=true)) {"Previous browser process is still closing"}
            check(dedicatedProcesses().isEmpty()) {"Previous browser process is still alive"}
            processOwner=this
        }
        try {
            val extras=Bundle().apply {putBinder(EXTRA,this@HiddenVpnBrowserBroker)}
            context.startActivity(Intent(context,HiddenVpnBrowserActivity::class.java).putExtras(extras))
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
                    result.writeNoException();result.writeTypedObject(network,0);result.writeInt(proxy.port);result.writeString(proxy.password);result.writeString(token);result.writeString(mode.name)
                }
            } else if(code==END || code==CLEANED) {
                check(Binder.getCallingUid()==Process.myUid() && childPid==Binder.getCallingPid())
                if(code==END) {
                    val reason=data.readInt();require(reason in setOf(RETURN,BACKGROUND,SCREEN_OFF))
                    val at=data.readLong();require(at in 0..SystemClock.elapsedRealtime())
                    val graceful=reason==RETURN && valid()
                    end(reason,at,false,graceful)
                } else profileClearAcknowledged=data.readInt()==1
                result.writeNoException()
            } else {
                caller()
                when(code) {
                    LIBRARY -> {
                        val action=data.readInt()
                        val url=data.readString().orEmpty()
                        require(url.length<=4096)
                        synchronized(gate) {
                            check(valid())
                            when(action) {
                                0 -> Unit
                                1 -> {require(HiddenConnectionPolicy.safeUrl(url,mode));library.visit(url)}
                                2 -> {require(HiddenConnectionPolicy.safeUrl(url,mode));library.bookmark(url)}
                                3 -> library.removeBookmark(url)
                                4 -> library.saveHistory(data.readInt()==1)
                                5 -> library.clearHistory()
                                6 -> library.clearBrowsingData()
                                else -> error("Unsupported Hidden library request")
                            }
                            val state=librarySnapshot(library)
                            check(valid());result.writeNoException();result.writeString(state)
                        }
                    }
                    PANIC -> {result.writeNoException();main.post {onPanic()};end(RETURN,SystemClock.elapsedRealtime(),true)}
                    CHECK -> {result.writeNoException();result.writeInt(1);result.writeString(downloadStatus)}
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
                    DOWNLOAD -> {
                        val url=checkNotNull(data.readString());val cookie=data.readString().orEmpty();val agent=data.readString().orEmpty()
                        require(HiddenConnectionPolicy.safeUrl(url,mode));require(cookie.length<=16384 && agent.length<=4096 && listOf(cookie,agent).none {it.contains('\r') || it.contains('\n')})
                        // Queue only after authenticated explicit child confirmation. The repository promotion
                        // is fenced again, so a late download cannot inherit a subsequent unlock.
                        check(downloading.compareAndSet(false,true))
                        downloadStatus="Downloading GET media into Hidden Vault…"
                        worker.execute {
                            val outcome=runCatching {download(url,cookie,agent)}
                            if(valid())downloadStatus=if(outcome.isSuccess)"Download verified and saved encrypted in Hidden Vault." else "Download failed or unsupported. No new media was saved. Use a reusable complete image/video GET URL."
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
    private fun repository():HiddenRepository {
        check(valid())
        return HiddenRepository(context,session,publishIfAllowed={commit ->synchronized(gate) {check(valid());commit()}})
    }
    fun uploadInfo(grant:String):uk.co.traynor.privategallery.core.vault.VaultItem {
        check(valid());val id=checkNotNull(uploads[grant]);return repository().items().single {it.id==id && !it.vaultOnly}
    }
    fun openUpload(grant:String):ParcelFileDescriptor {
        val item=uploadInfo(grant)
        val pipe=ParcelFileDescriptor.createReliablePipe()
        synchronized(gate) {check(valid());pipes.add(pipe[1])}
        // Upload decrypts bounded chunks directly into a private pipe, never a public/cache file.
        worker.execute {
            val buffer=ByteArray(32*1024)
            try {val repo=repository();repo.verify(item.id);check(valid());repo.withTransferSource(item.id,{!valid()}) {current,input ->
                check(!current.vaultOnly)
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use {out ->while(true) {check(valid());val n=input.read(buffer);if(n<0)break;check(valid());out.write(buffer,0,n)}}
            }} catch(_:Exception) {runCatching {pipe[1].closeWithError("Hidden upload revoked")}} finally {buffer.fill(0);pipes.remove(pipe[1]);runCatching {pipe[1].close()}}
        }
        return pipe[0]
    }
    /** Readiness uses the same authenticated HTTP→private Unix SOCKS path as native WebView. */
    fun verifyTorReady() {
        check(mode==HiddenConnectionPolicy.Mode.TOR_ONLY && valid())
        val client=OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.HTTP,java.net.InetSocketAddress(java.net.InetAddress.getByAddress(byteArrayOf(127,0,0,1)),proxy.port)))
            .proxyAuthenticator(object:Authenticator {override fun authenticate(route:Route?,response:Response):Request? = if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("hidden",proxy.password)).build()})
            .dns(object:Dns {override fun lookup(hostname:String):List<java.net.InetAddress> = throw java.net.UnknownHostException("Tor origin DNS prohibited")})
            .followRedirects(false).retryOnConnectionFailure(false).callTimeout(30,TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url("https://example.com/").build())
        try {
            synchronized(gate){check(valid());calls.add(call)}
            call.execute().use {response->check(response.code==200);check(checkNotNull(response.body).byteStream().read()!=-1);check(valid())}
        } finally {calls.remove(call);client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    private fun download(url:String,cookie:String,agent:String) {
        check(valid())
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
            synchronized(gate) {check(valid());calls.add(call)}
            try {call.execute().use {response ->
                require(response.code==200 && response.header("Content-Range")==null)
                val body=checkNotNull(response.body);val mime=body.contentType()?.let {"${it.type}/${it.subtype}"}.orEmpty()
                require(mime.startsWith("image/") || mime in setOf("video/mp4","video/webm","video/quicktime","video/x-matroska"))
                val expected=body.contentLength();var count=0L
                val input=object:FilterInputStream(body.byteStream()) {
                    override fun read():Int {val one=ByteArray(1);return if(read(one,0,1)<0)-1 else one[0].toInt() and 255}
                    override fun read(b:ByteArray,off:Int,len:Int):Int {check(valid());val n=`in`.read(b,off,len);if(n>0){count+=n;require(count<=2L*1024*1024*1024)};check(valid());if(n<0 && expected>=0)check(count==expected);return n}
                }
                val repo=repository();val item=repo.importMedia("Hidden download",mime,input,validateDownloadedMedia=hiddenDownloadedMediaValidator(mime,{!valid()}));repo.verify(item.id)
            }} finally {calls.remove(call)}
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
            proxy.close();calls.forEach {it.cancel()};pipes.forEach {runCatching {it.closeWithError("Session ended")}};pipes.clear();uploads.clear();pickerQuery=null;pickerQueryId=""
            endpoint=child
        }
        synchronized(registry) {registry.remove(token)}
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
        if(endpoint==null || !endpoint.isBinderAlive) {
            synchronized(registry) {if(processOwner===this)processOwner=null}
            HiddenVpnStorageCleanup.cleanAsync(context.applicationContext)
        }
    }
    companion object {
        internal fun pickerSnapshot(items:List<uk.co.traynor.privategallery.core.vault.VaultItem>,accept:Array<String>):HiddenUploadPickerQuery =
            HiddenUploadPickerQuery(items.map {item ->HiddenUploadPickerQuery.Entry(item.id,item.displayName,item.mimeType,item.vaultOnly,item.state==uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE)}) {mime ->uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.accepts(mime,accept)}
        internal fun pickerPage(snapshot:HiddenUploadPickerQuery,generation:Long,queryId:String,offset:Int):String {
            val page=snapshot.page(offset)
            val json=JSONArray();page.forEach {json.put(JSONObject().put("id",it.id).put("name",it.name).put("mime",it.mime))}
            return JSONObject().put("query",queryId).put("generation",generation).put("total",snapshot.size())
                .put("restricted",snapshot.restrictedCount).put("types",snapshot.typeCount).put("next",offset+page.size).put("items",json).toString()
        }
        internal fun librarySnapshot(library:HiddenBrowserLibrary):String = JSONObject().put("bookmarks",JSONArray(library.bookmarks())).put("history",JSONArray(library.history())).put("saveHistory",library.savesHistory()).toString().replace("\\/","/")
        const val EXTRA="hidden_vpn_capability"
        const val DESCRIPTOR="uk.co.traynor.privategallery.HiddenVpnSession"
        const val CLAIM=IBinder.FIRST_CALL_TRANSACTION
        const val CHECK=IBinder.FIRST_CALL_TRANSACTION+1
        const val END=IBinder.FIRST_CALL_TRANSACTION+2
        const val LIST=IBinder.FIRST_CALL_TRANSACTION+3
        const val UPLOAD=IBinder.FIRST_CALL_TRANSACTION+4
        const val DOWNLOAD=IBinder.FIRST_CALL_TRANSACTION+5
        const val CLEANED=IBinder.FIRST_CALL_TRANSACTION+6
        const val PREVIEW=IBinder.FIRST_CALL_TRANSACTION+7
        const val LIBRARY=IBinder.FIRST_CALL_TRANSACTION+8
        const val PANIC=IBinder.FIRST_CALL_TRANSACTION+9
        const val CHILD_DESCRIPTOR="uk.co.traynor.privategallery.HiddenVpnChild"
        const val CHILD_SHUTDOWN=IBinder.FIRST_CALL_TRANSACTION
        const val CHILD_SECURITY=IBinder.FIRST_CALL_TRANSACTION+1
        const val RETURN=1
        const val BACKGROUND=2
        const val SCREEN_OFF=3
        const val DEATH=4
        private val registry=mutableMapOf<String,HiddenVpnBrowserBroker>()
        private var processOwner:HiddenVpnBrowserBroker?=null
        fun hasDedicatedProcess(context:Context):Boolean = runCatching {
            checkNotNull(context.getSystemService(android.app.ActivityManager::class.java).runningAppProcesses)
                .any {it.uid==Process.myUid() && it.processName==context.packageName+":hidden_vpn_browser"}
        }.getOrDefault(true)
        fun find(token:String):HiddenVpnBrowserBroker = synchronized(registry) {checkNotNull(registry[token]).also {check(it.valid())}}
    }
}
