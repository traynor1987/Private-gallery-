package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSink
import uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProxy
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiHttpResponse
import uk.co.traynor.privategallery.core.editor.PrivateAiHttpTransport
import uk.co.traynor.privategallery.core.vpn.OwnedVpnDns

/** Immutable clients over resources already connected and verified by the owner coordinator.
 * No VpnService startup, system binding, browser/profile changes, or permission handoffs here.
 */
internal object EvidenceNetwork {
 fun primarySystem(current:()->Boolean):EvidenceRoute = make(EvidenceCatalog.Owner.PRIMARY,"Primary authorised system transport",current,{},null){_,_->}

 fun ownedVpn(owner:EvidenceCatalog.Owner,context:Context,network:Network,current:()->Boolean,onClose:()->Unit={}):EvidenceRoute {
  val application=context.applicationContext
  val manager=application.getSystemService(ConnectivityManager::class.java)
  val valid={current()&&runCatching {
   val caps=checkNotNull(manager.getNetworkCapabilities(network))
   caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)&&caps.ownerUid==Process.myUid()&&
    !manager.getLinkProperties(network)?.interfaceName.isNullOrBlank()
  }.getOrDefault(false)}
  check(valid()){"Owned VPN is unavailable"}
  return make(owner,"Owned VPN (pinned network)",valid,onClose,null){builder,active->
   builder.proxy(Proxy.NO_PROXY).socketFactory(network.socketFactory).dns(object:Dns {
    override fun lookup(hostname:String):List<InetAddress> {
     check(active());require(!hostname.endsWith(".onion",true))
     return OwnedVpnDns.lookup(application,network,hostname,active).also{check(active());require(it.isNotEmpty()&&it.all(HiddenVpnProxy::publicAddress))}
    }
   })
  }
 }

 fun tor(context:Context,unixPath:String,current:()->Boolean,onClose:()->Unit={}):EvidenceRoute {
  uk.co.traynor.privategallery.core.home.NovaReleaseFeatures.requireTorEnabled()
  val root=File(context.applicationInfo.dataDir).canonicalFile
  val socket=File(unixPath)
  fun privateSocket():Boolean=runCatching {
   require(socket.isAbsolute&&socket.canonicalPath==unixPath&&unixPath.startsWith(root.path+"/"))
   val parent=Os.stat(checkNotNull(socket.parent));val endpoint=Os.stat(unixPath)
   parent.st_uid==Process.myUid()&&(parent.st_mode and 63)==0&&
    endpoint.st_uid==Process.myUid()&&(endpoint.st_mode and OsConstants.S_IFMT)==OsConstants.S_IFSOCK
  }.getOrDefault(false)
  val valid={current()&&privateSocket()}
  check(valid()){"Private Tor endpoint is unavailable"}
  var bridge:HiddenVpnProxy?=null
  try {
   return make(EvidenceCatalog.Owner.HIDDEN,"Tor (private Unix SOCKS)",valid,onClose,{active->HiddenVpnProxy.tor(unixPath,active).also{bridge=it}}){builder,_->
    val proxy=checkNotNull(bridge)
    builder.proxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),proxy.port)))
     .proxyAuthenticator(object:Authenticator {
      override fun authenticate(route:Route?,response:Response):Request? =
       if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("hidden",proxy.password)).build()
     }).dns(object:Dns {override fun lookup(hostname:String):List<InetAddress> = throw UnknownHostException("Tor origin DNS prohibited")})
   }
  }catch(failure:Throwable){bridge?.close();throw failure}
 }

 private fun make(owner:EvidenceCatalog.Owner,label:String,current:()->Boolean,onClose:()->Unit,
  resourceFactory:((()->Boolean)->AutoCloseable)?,configure:(OkHttpClient.Builder,()->Boolean)->Unit):EvidenceRoute =
  RouteClient(owner,label,current,onClose,resourceFactory,configure)

 private class RouteClient(override val owner:EvidenceCatalog.Owner,override val label:String,
  private val guard:()->Boolean,private val onClose:()->Unit,
  resourceFactory:((()->Boolean)->AutoCloseable)?,configure:(OkHttpClient.Builder,()->Boolean)->Unit):EvidenceRoute {
  override val identity=UUID.randomUUID().toString()
  private val closed=AtomicBoolean()
  private val admission=Any()
  private val calls=mutableSetOf<Call>()
  private val resource:AutoCloseable?
  private val client:OkHttpClient
  private val watcher=Executors.newSingleThreadScheduledExecutor{r->Thread(r,"evidence-route-retirement").apply{isDaemon=true}}
  private fun valid()=!closed.get()&&runCatching(guard).getOrDefault(false)
  init {
   check(valid()){"Evidence route retired"}
   resource=resourceFactory?.invoke(::valid)
   try {
    val builder=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
     .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).cookieJar(CookieJar.NO_COOKIES).cache(null)
     .connectTimeout(20,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS).callTimeout(90,TimeUnit.SECONDS)
     .protocols(listOf(Protocol.HTTP_1_1)) // No multiplex/coalescing to another route or origin.
    configure(builder,::valid);client=builder.build()
    check(valid()){"Evidence route retired"}
    watcher.scheduleWithFixedDelay({if(!valid())close()},50,50,TimeUnit.MILLISECONDS)
   }catch(failure:Throwable){closed.set(true);resource?.close();watcher.shutdownNow();throw failure}
  }
  override fun current():Boolean {if(valid())return true;close();return false}
  override suspend fun execute(request:AiHttpRequest):AiHttpResponse {
   require(request.url.startsWith("https://")&&request.maxResponseBytes in 1..2*1024*1024)
   // The primitive can serve controlled HTTPS fixtures. Only EvidenceAi may choose the
   // production endpoint, which is an exact constant with no runtime override.
   val url=request.url.toHttpUrlOrNullSafe()
   require(url.isHttps&&url.username.isEmpty()&&url.password.isEmpty()&&url.fragment==null)
   require(request.method=="POST"&&request.body!=null)
   var pending:ByteArray?=null
   try {
    val result=withContext(Dispatchers.IO) {
     coroutineScope {
      val context=currentCoroutineContext()
      val streamBody=checkNotNull(request.body)
      val body=object:RequestBody() {
       override fun contentType()="application/json; charset=utf-8".toMediaType()
       override fun isOneShot()=true
       override fun writeTo(sink:BufferedSink) {
        check(valid());context.ensureActive()
        streamBody.writeTo(object:java.io.FilterOutputStream(sink.outputStream()) {
         private var total=0L
         override fun write(b:ByteArray,off:Int,len:Int){check(valid());context.ensureActive();total+=len;require(total<=20L*1024*1024);out.write(b,off,len);check(valid())}
         override fun write(b:Int){check(valid());context.ensureActive();total++;require(total<=20L*1024*1024);out.write(b);check(valid())}
        });check(valid());context.ensureActive()
       }
      }
      val builder=Request.Builder().url(url).post(body).header("Accept-Encoding","identity").header("Cache-Control","no-store")
      request.headers.forEach{(name,value)->require(!name.equals("Cookie",true)&&!name.equals("Proxy-Authorization",true));builder.header(name,value)}
      // Check, create, and register under the same monitor used by close. No admitted
      // request can escape a concurrent retirement between creation and registration.
      val call=synchronized(admission){check(valid()){"Evidence route retired"};client.newCall(builder.build()).also{calls.add(it)}}
      val canceller=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
      try {
       check(valid());ensureActive()
       call.execute().use{response->
        check(valid());ensureActive()
        if(response.code !in 200..299)AiHttpResponse(response.code,null,byteArrayOf())
        else {
         val responseBody=checkNotNull(response.body)
         require(responseBody.contentLength()<=request.maxResponseBytes)
         pending=responseBody.byteStream().use{input->PrivateAiHttpTransport.readBounded(input,request.maxResponseBytes){ensureActive();check(valid())}}
         check(valid());ensureActive();AiHttpResponse(response.code,response.header("Content-Type"),checkNotNull(pending))
        }
       }
      }finally{withContext(NonCancellable){canceller.cancelAndJoin()};call.cancel();synchronized(admission){calls.remove(call)}}
     }
    }
    currentCoroutineContext().ensureActive();check(current());pending=null;return result
   }catch(cancelled:CancellationException){throw cancelled}
   catch(_:Exception){
    // Cancelling a socket read may surface as IOException before the dispatcher resumes.
    currentCoroutineContext().ensureActive()
    throw EvidenceAiFailure("The selected network route is unavailable or returned an invalid response.")
   }
   finally{pending?.fill(0)}
  }
  override fun close() {
   synchronized(admission){if(!closed.compareAndSet(false,true))return;calls.forEach{it.cancel()};calls.clear()}
   // Calls must be revoked before destroying the authenticated proxy or owned route.
   watcher.shutdownNow();client.connectionPool.evictAll();client.dispatcher.cancelAll();client.dispatcher.executorService.shutdown()
   runCatching{resource?.close()};runCatching(onClose)
  }
 }
 private fun String.toHttpUrlOrNullSafe():HttpUrl = try {toHttpUrl()}catch(_:Exception){throw EvidenceAiFailure("HTTPS route endpoint is invalid.")}
}
