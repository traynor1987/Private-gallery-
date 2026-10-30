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
  val active={runCatching{current() && System.nanoTime()-start<TimeUnit.SECONDS.toNanos(45)}.getOrDefault(false)}
  val watch=Executors.newSingleThreadScheduledExecutor()
  watch.scheduleWithFixedDelay({if(!active())client.dispatcher.cancelAll()},0,100,TimeUnit.MILLISECONDS)
  try {
   return PrivateAppDiscovery.discover(page,tor,active,{url ->
    check(active());require(PrivateAppDiscovery.resolve(page,url,tor)!=null)
    val call=client.newCall(Request.Builder().url(url).header("Accept","text/html,application/manifest+json,application/json,image/png,image/jpeg,image/webp,image/x-icon").header("Cache-Control","no-store").build())
    call.execute().use {response ->
     check(active());require(response.code==200 && response.header("Content-Range")==null)
     val body=checkNotNull(response.body);require(body.contentLength()<=PrivateAppDiscovery.MAX_DOCUMENT)
     val buffer=ByteArray(PrivateAppDiscovery.MAX_DOCUMENT+1)
     try {
      var count=0
      body.byteStream().use {input ->while(true){check(active());val n=input.read(buffer,count,buffer.size-count);if(n<0)break;count+=n;require(count<=PrivateAppDiscovery.MAX_DOCUMENT)}}
      check(active());PrivateAppDiscovery.Resource(buffer.copyOf(count),body.contentType()?.let{"${it.type}/${it.subtype}"}.orEmpty())
     }finally{buffer.fill(0)}
    }
   },PrivateAppIcons::normalize)
  }finally{watch.shutdownNow();client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
 }
}
