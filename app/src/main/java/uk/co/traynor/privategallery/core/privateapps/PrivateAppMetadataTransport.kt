package uk.co.traynor.privategallery.core.privateapps

import android.net.Network
import okhttp3.*
import java.net.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnProxy

/** Short-lived metadata reader on an already-authorised Network or Hidden proxy. Never ambient DNS/sockets. */
internal object PrivateAppMetadataTransport {
 fun vpn(context:android.content.Context,network:Network,current:()->Boolean)=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).socketFactory(network.socketFactory)
  .dns(object:Dns {override fun lookup(hostname:String):List<InetAddress> {
   check(current());require(!hostname.endsWith(".onion",true))
   return uk.co.traynor.privategallery.core.vpn.OwnedVpnDns.lookup(context,network,hostname,current).also{check(current());require(it.isNotEmpty() && it.all(HiddenVpnProxy::publicAddress))}
  }})
 fun hiddenProxy(port:Int,password:String)=OkHttpClient.Builder()
  .proxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),port)))
  .proxyAuthenticator(object:okhttp3.Authenticator {override fun authenticate(route:Route?,response:Response):Request? =
   if(response.request.header("Proxy-Authorization")!=null)null else response.request.newBuilder().header("Proxy-Authorization",Credentials.basic("hidden",password)).build()})
  .dns(object:Dns {override fun lookup(hostname:String):List<InetAddress> = throw UnknownHostException("Origin DNS prohibited")})
 fun discover(page:String,tor:Boolean,builder:OkHttpClient.Builder,current:()->Boolean):PrivateAppCatalog.App {
  check(current());require(PrivateAppDiscovery.safe(page,tor))
  val client=builder.followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).cache(null).cookieJar(CookieJar.NO_COOKIES)
   .connectTimeout(8,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(12,TimeUnit.SECONDS)
   .connectionPool(ConnectionPool(0,1,TimeUnit.SECONDS)).build()
  val start=System.nanoTime()
  fun diagnostic(stage:String,failure:Throwable?=null,status:Int=-1,bytes:Long=-1,mime:String="") {
   // Fixed phase/cause/counters only: never URL, host, body, credentials or exception message.
   runCatching {if(uk.co.traynor.privategallery.BuildConfig.DEBUG)android.util.Log.i("PrivateAppDiscovery",
    "tor=$tor stage=$stage cause=${failure?.javaClass?.simpleName.orEmpty()} status=$status bytes=$bytes mime=$mime elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)}")}
  }
  val active={runCatching{current() && System.nanoTime()-start<TimeUnit.SECONDS.toNanos(45)}.getOrDefault(false)}
  val watch=Executors.newSingleThreadScheduledExecutor()
  watch.scheduleWithFixedDelay({if(!active())client.dispatcher.cancelAll()},0,100,TimeUnit.MILLISECONDS)
  try {
   return PrivateAppDiscovery.discover(page,tor,active,{url ->
    check(active());require(PrivateAppDiscovery.resolve(page,url,tor)!=null)
    val call=client.newCall(Request.Builder().url(url).header("Accept","text/html,application/manifest+json,application/json,image/png,image/jpeg,image/webp,image/x-icon").header("Cache-Control","no-store").build())
    call.execute().use {response ->
     val kind=response.body?.contentType()?.let {type ->when {
      type.type=="text" && type.subtype=="html" ->"html"
      type.type=="application" && type.subtype in setOf("json","manifest+json") ->"json"
      type.type=="image" ->"image"
      else ->"other"
     }} ?: "none"
     diagnostic("resource_headers",status=response.code,bytes=response.body?.contentLength() ?: -1,mime=kind)
     check(active())
     if(response.code in setOf(408,429) || response.code>=500)throw java.io.IOException("Public metadata temporarily unavailable")
     require(response.code==200 && response.header("Content-Range")==null)
     val body=checkNotNull(response.body);require(body.contentLength()<=PrivateAppDiscovery.MAX_DOCUMENT)
     val buffer=ByteArray(PrivateAppDiscovery.MAX_DOCUMENT+1)
     try {
      var count=0
      body.byteStream().use {input ->while(true){check(active());val n=input.read(buffer,count,buffer.size-count);if(n<0)break;count+=n;require(count<=PrivateAppDiscovery.MAX_DOCUMENT)}}
      check(active());diagnostic("resource_body",status=response.code,bytes=count.toLong(),mime=kind);PrivateAppDiscovery.Resource(buffer.copyOf(count),body.contentType()?.let{"${it.type}/${it.subtype}"}.orEmpty())
     }finally{buffer.fill(0)}
    }
   },PrivateAppIcons::normalize,{stage,failure ->diagnostic(stage,failure)})
  }finally{watch.shutdownNow();client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
 }
}
