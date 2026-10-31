package uk.co.traynor.privategallery.core.evidence

import java.io.BufferedInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*
import kotlinx.coroutines.*
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiRequestBody

/** Test-only certificate trust and reflection: production exposes no TLS/endpoint override. */
class EvidenceNetwork179Test {
 private class Https:AutoCloseable {
  val requests=CopyOnWriteArrayList<String>()
  val entered=CountDownLatch(1)
  val headersEntered=CountDownLatch(1)
  val responseBodyEntered=CountDownLatch(1)
  val releaseResponse=CountDownLatch(1)
  val peerClosed=CountDownLatch(1)
  val replies=LinkedBlockingQueue<String>()
  private val certificate=CertificateFactory.getInstance("X.509").generateCertificate(Base64.getDecoder().decode(CERT).inputStream())
  private val privateKey=KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(KEY)))
  private val trust=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setCertificateEntry("only-fixture",certificate)}
  val manager=(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(trust)}.trustManagers.single{it is X509TrustManager} as X509TrustManager)
  val clientTls=SSLContext.getInstance("TLS").apply{init(null,arrayOf(manager),null)}
  private val keys=KeyStore.getInstance(KeyStore.getDefaultType()).apply{load(null);setKeyEntry("fixture",privateKey,CharArray(0),arrayOf(certificate))}
  private val serverTls=SSLContext.getInstance("TLS").apply{init(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(keys,CharArray(0))}.keyManagers,null,null)}
  private val server=(serverTls.serverSocketFactory.createServerSocket() as SSLServerSocket).apply{bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),0));soTimeout=10000}
  val url="https://localhost:${server.localPort}/fixture"
  private val open=AtomicBoolean(true)
  private val sockets=CopyOnWriteArrayList<SSLSocket>()
  private val worker=Thread({while(open.get()){
   val socket=try{server.accept() as SSLSocket}catch(_:Exception){break};sockets+=socket
   try{socket.use{
    it.soTimeout=10000;val input=BufferedInputStream(it.inputStream);val headers=StringBuilder()
    while(true){val line=line(input);if(line.isEmpty())break;headers.append(line).append('\n')}
    headersEntered.countDown()
    if(headers.toString().contains("Transfer-Encoding: chunked",true)){
     while(true){val size=line(input).substringBefore(';').toInt(16);if(size==0){while(line(input).isNotEmpty()){};break};repeat(size){check(input.read()>=0)};check(line(input).isEmpty())}
    }else{val length=Regex("Content-Length: ([0-9]+)",RegexOption.IGNORE_CASE).find(headers)?.groupValues?.get(1)?.toInt()?:0;repeat(length){check(input.read()>=0)}}
    requests+=headers.toString();entered.countDown()
    val reply=replies.poll(10,TimeUnit.SECONDS)?:"HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
    if(reply=="RESET"){it.setSoLinger(true,0);return@use}
    if(reply=="HOLD_RESPONSE"){
     it.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\nConnection: close\r\n\r\nx".toByteArray());it.outputStream.flush();responseBodyEntered.countDown()
     check(releaseResponse.await(10,TimeUnit.SECONDS));return@use
    }
    it.outputStream.write(reply.toByteArray(Charsets.US_ASCII));it.outputStream.flush()
   }}catch(_:Exception){}finally{sockets.remove(socket);peerClosed.countDown()}
  }},"evidence-test-https").apply{isDaemon=true;start()}
  fun response(code:Int=200,headers:String="",body:String="OK") {replies.put("HTTP/1.1 $code Fixture\r\nContent-Length: ${body.length}\r\nConnection: close\r\n$headers\r\n$body")}
  fun route(active:()->Boolean={true},configure:(OkHttpClient.Builder)->Unit={}):EvidenceRoute {
   // Keep the narrowly scoped fixture trust injection entirely in androidTest.
   val type=Class.forName("uk.co.traynor.privategallery.core.evidence.EvidenceNetwork\$RouteClient")
   val constructor=type.declaredConstructors.single{it.parameterTypes.size==6}.apply{isAccessible=true}
   val configuration:(OkHttpClient.Builder,()->Boolean)->Unit={builder,_->
    builder.sslSocketFactory(clientTls.socketFactory,manager).proxy(Proxy.NO_PROXY).dns(object:Dns{override fun lookup(hostname:String)=listOf(InetAddress.getByAddress(byteArrayOf(127,0,0,1)))})
    configure(builder)
   }
   return constructor.newInstance(EvidenceCatalog.Owner.PRIMARY,"HTTPS fixture",active,{},null,configuration) as EvidenceRoute
  }
  override fun close(){open.set(false);releaseResponse.countDown();server.close();sockets.forEach{runCatching{it.close()}};worker.interrupt();worker.join(1000)}
  companion object {
   private fun line(input:InputStream):String {val b=StringBuilder();while(true){val c=input.read();check(c>=0&&b.length<32768);if(c==10)return b.toString().removeSuffix("\r");b.append(c.toChar())}}
   // Public, disposable fixture certificate and private key; never an account credential.
   private const val CERT="MIIDJTCCAg2gAwIBAgIUM84pNQq81IOLtRCDjaxpesqQhv0wDQYJKoZIhvcNAQELBQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MTAzMTE1NDk0MFoXDTM2MTAyODE1NDk0MFowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoADdm+7MAu3LG+8pA+bEfbVTdBCbfp/EbC3IzwIPF75Sz0DW435wDepkLqr7feNYaL8NKQQiPpnFoMW7pW7SFdPG8Qb+WAcFzTaw0lQ9+K+P0sya4uGwxVCU9ZULu9ysKEU+VZ6JFUDkugMkC6OR/JtBguMAWIZCYddME4JBFEHuY0MOzkSiQ17B8TyTxr8XjohqxK7CLwlROtp0uGEbT8DBaF+KIfMPzZCI6iXH/TVzpe4MX/eez/Nrbnn165BlscXOMbXtK/WXnGFfagLt4wDFfESHR/MykcaCY+ucgUoczcK0WmdGgkSda6r6+FLY4Cy7Y7+fu0LLuyBbZ9NInwIDAQABo28wbTAdBgNVHQ4EFgQUiZKRgfpvT4E/Zkpax/eo74YAuhowHwYDVR0jBBgwFoAUiZKRgfpvT4E/Zkpax/eo74YAuhowDwYDVR0TAQH/BAUwAwEB/zAaBgNVHREEEzARgglsb2NhbGhvc3SHBH8AAAEwDQYJKoZIhvcNAQELBQADggEBABniduvhTM9R41OH8Rw3+VCw3pfHF39qYdC5bf1LLoo91q6ZBI1JCNkaSoPpr3dux2E/UTBMXbhq17lCWqFQLEIQqpeGVKNrck9g+yFSQq3TmfBEssepZmEI8kU/mq2Nj1rwNKisdrt4HbX0gmXEbRE3Y7i3hSGPodjd9vbFJMLi7seJE86zJDq2uguHu5iBLZPFRfdb5mK2G7dBAv2/i63dGY8EtZPZWF7LzErMU5GnLdHB82h1/5RrqOUbsEXu9UYQ77jcXTp9NfrGzM8HQwCuxYwlsKQ05VTY606hJwT6ZeItVb7r8qplx7dcFejbj6Yd1yS5Zybsi81rsGxmuZM="
   private const val KEY="MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCgAN2b7swC7csb7ykD5sR9tVN0EJt+n8RsLcjPAg8XvlLPQNbjfnAN6mQuqvt941hovw0pBCI+mcWgxbulbtIV08bxBv5YBwXNNrDSVD34r4/SzJri4bDFUJT1lQu73KwoRT5VnokVQOS6AyQLo5H8m0GC4wBYhkJh10wTgkEUQe5jQw7ORKJDXsHxPJPGvxeOiGrErsIvCVE62nS4YRtPwMFoX4oh8w/NkIjqJcf9NXOl7gxf957P82tuefXrkGWxxc4xte0r9ZecYV9qAu3jAMV8RIdH8zKRxoJj65yBShzNwrRaZ0aCRJ1rqvr4UtjgLLtjv5+7Qsu7IFtn00ifAgMBAAECggEAPVI3nJR3kcPK7OjK4ljmA3ukAT6AKZZXtFITvOqAGExz9kRWVxfVIDAttwkGm6eu8W4PQCH6cd2DNWuyz+NaFs10WFmizt/aJF0KhYGNwqqeR17tGXj/w49vl3NI7PXU8RYgnrXEueWGA5hCj0dgItzpzZzsPAEwncjiujNYd8d1kFH5OdNMVg/r7rrshdJzl8D3R/GV6RgDdqrDIE/dEYtlavzT0z4KgyejdMHMJAc3SNYEzqJLyaY1KZ/XiNeEWx8oU3PHOaaQbPPInQJknKffifqvh5eOz2/O0vOAglQX5U6ALhotm8hQa5mnTfu3o/VPCzaz1v5FzTqg9ReEsQKBgQDWPa5aiHc6P5LpDJ2PVsf2jqZT2l4SNavwF05sirxayv+7YnSWo4rTgq+/0Ll3c0i6e18Fnk2k3b5irIstW5640fp3OwHDXzq7WyPMUFmd2hiZOpzr9ParuOZwfhWGu0RguwjMlWIvUwRcePkxVVzBNh+9Gb8YuPcNf31StbZXxwKBgQC/MM6sJCmVdWg/hdrIS5Gj02iQ/UjPa6wUJkQbY9ioU/1CqdPtvALDc1mjvZFIenrPm/Hn0POM3mIYpiifhWeZIqkmcS7+dSuGeJ+T9/xndMmYD9pUIMx0rKJnYhEf1cYHztygJGVRqxvN0RG/xizrsBMuFewHtdJn5ijrCfx4aQKBgQCkNkkC2aDRg9MeUG4gZTsGEBcLJNCAEqWEdMjGx5xR11EnFvMYV6hcMMp3MwxFs5amYVPXIZyP5us8BNb04EoLjd72LsNONhr1aolI3LmkFXxPTq7i6TN9Mk9nIC1txPJHVnULIuFMNsyb+JnAp5Zd/hdbwXVJBNVQgkzT7bYjFwKBgAi8v0UopenZorj8cgIjSfWVrLqriI9KGWQWV5h02Krgo7tPLYs4w1uRQvBzslcAmeow8z4n02mFpDACL+sSWqip59cE5yPj0OhlTM2jUJqdwLS9TLn5gb53QTi+R5AP4evrbTO4VhUEX9HpEwmTkev3cPjzMYUsrv/micor+6i5AoGBAMzOfDqdS2jkzgFIfSJJitixKRVq5PqMtG+B7w9ZEaWwWR975yXgTqujn2HbwkAXegh2bt11qqTl/J7Dzhk5TxgWekMaJPvckBgF2jwTKxQM9NQDprzChW/Omcmeokvi+nPtJZWJjMt4TE5L3Vkmi60OrGV8f239VjnwJ8gDvN/a"
  }
 }
 private fun request(url:String,limit:Int=1024)=AiHttpRequest("POST",url,mapOf("Content-Type" to "application/json"),AiRequestBody{it.write("{}".toByteArray())},limit)
 @Test fun controlledHttpsRedirectsAreNotFollowedAndCookiesCacheAreNotReused()=runBlocking {
  Https().use{https->https.route().use{route->
   https.response(302,"Location: ${https.url}\r\nSet-Cookie: secret=fixture\r\nCache-Control: public, max-age=3600\r\n")
   val redirected=route.execute(request(https.url));assertEquals(302,redirected.status);assertTrue(redirected.bytes.isEmpty());assertEquals(1,https.requests.size)
   https.response(200,"Set-Cookie: secret=fixture\r\nCache-Control: public, max-age=3600\r\n", "FIRST")
   assertEquals("FIRST",route.execute(request(https.url)).bytes.toString(Charsets.UTF_8))
   https.response(200,body="SECOND");assertEquals("SECOND",route.execute(request(https.url)).bytes.toString(Charsets.UTF_8))
   assertEquals(3,https.requests.size);assertTrue(https.requests.none{it.contains("Cookie:",true)})
  }}
 }
 @Test fun responseBoundsAndRevocationCancelAdmittedHttpsCalls()=runBlocking {
  Https().use{https->https.route().use{route->https.response(body="TOO LARGE");try{route.execute(request(https.url,2));fail("oversize response")}catch(_:EvidenceAiFailure){}}}
  Https().use{https->val active=AtomicBoolean(true);https.route(active::get).use{route->
   coroutineScope{
    val call=async(Dispatchers.IO){try{route.execute(request(https.url));false}catch(_:EvidenceAiFailure){true}}
    assertTrue(withContext(Dispatchers.IO){https.entered.await(5,TimeUnit.SECONDS)});active.set(false)
    assertTrue(withTimeout(5000){call.await()});assertFalse(route.current())
   }
  }}
 }
 @Test fun unavailableExplicitProxyNeverFallsBackAndCloseRejectsNewCalls()=runBlocking {
  Https().use{https->https.route(configure={builder->builder.proxy(Proxy(Proxy.Type.HTTP,InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127,0,0,1)),1))).dns(object:Dns{override fun lookup(hostname:String):List<InetAddress> = error("Origin DNS prohibited")})}).use{route->
   try{route.execute(request(https.url));fail("proxy fallback")}catch(_:EvidenceAiFailure){};assertTrue(https.requests.isEmpty())
   route.close();try{route.execute(request(https.url));fail("closed call admitted")}catch(_:EvidenceAiFailure){};assertTrue(https.requests.isEmpty())
  }}
 }
 @Test fun resetAfterReceiptAnd408Or503NeverRetryBilledPost()=runBlocking {
  for(code in listOf(0,408,503))Https().use{https->https.route().use{route->
   if(code==0)https.replies.put("RESET")else https.response(code,"Retry-After: 0\r\n")
   https.response(200,body="WOULD BE RETRY")
   if(code==0){try{withTimeout(5000){route.execute(request(https.url))};fail("reset returned success")}catch(_:EvidenceAiFailure){}}
   else assertEquals(code,withTimeout(5000){route.execute(request(https.url))}.status)
   assertEquals(1,https.requests.size)
  }}
 }
 @Test fun unknownLengthChunkedResponseOverflowIsRejectedWhileStreaming()=runBlocking {
  Https().use{https->https.route().use{route->
   https.replies.put("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n4\r\nABCD\r\n4\r\nEFGH\r\n0\r\n\r\n")
   try{route.execute(request(https.url,5));fail("chunked overflow")}catch(_:EvidenceAiFailure){};assertEquals(1,https.requests.size)
  }}
 }
 @Test fun coroutineCancellationDuringResponseReadDisconnectsWithoutPublishing()=runBlocking {
  Https().use{https->https.route().use{route->
   https.replies.put("HOLD_RESPONSE")
   coroutineScope{
    val call=async(Dispatchers.IO){route.execute(request(https.url))}
    try{
     assertTrue(withContext(Dispatchers.IO){https.responseBodyEntered.await(5,TimeUnit.SECONDS)})
     withTimeout(5000){call.cancelAndJoin()};try{call.await();fail("cancelled response published")}catch(_:CancellationException){}
     assertEquals(1,https.requests.size)
    }finally{https.releaseResponse.countDown();call.cancel()}
   }
  }}
 }
 @Test fun coroutineCancellationDuringUploadClosesSocketBeforeSourceWriterReturns()=runBlocking {
  Https().use{https->https.route().use{route->
   val entered=CountDownLatch(1);val release=CountDownLatch(1);val writerExited=CountDownLatch(1)
   val upload=AiHttpRequest("POST",https.url,emptyMap(),AiRequestBody{output->try{output.write(ByteArray(65536));output.flush();entered.countDown();check(release.await(10,TimeUnit.SECONDS))}finally{writerExited.countDown()}},1024)
   coroutineScope{
    val call=async(Dispatchers.IO){route.execute(upload)}
    try{
     assertTrue(withContext(Dispatchers.IO){entered.await(5,TimeUnit.SECONDS)});call.cancel()
     assertTrue("admitted socket remained open",withContext(Dispatchers.IO){https.peerClosed.await(5,TimeUnit.SECONDS)})
     assertEquals("writer returned before socket cancellation was observed",1L,writerExited.count)
     release.countDown();withTimeout(5000){call.join()};try{call.await();fail("cancelled upload returned")}catch(_:CancellationException){}
     assertTrue(https.requests.isEmpty())
    }finally{release.countDown();call.cancel()}
   }
  }}
 }
 @Test fun admissionPausedDuringCallCreationCannotEscapeConcurrentClose()=runBlocking {
  Https().use{https->
   val creating=CountDownLatch(1);val release=CountDownLatch(1);val closeAttempt=CountDownLatch(1);val closed=CountDownLatch(1)
   https.route(configure={builder->builder.eventListenerFactory{
    creating.countDown();check(release.await(5,TimeUnit.SECONDS))
    object:EventListener(){override fun callStart(call:Call){check(closed.await(5,TimeUnit.SECONDS))}}
   }}).use{route->
    supervisorScope{
     val call=async(Dispatchers.IO){try{route.execute(request(https.url));false}catch(_:EvidenceAiFailure){true}}
     var closing:Deferred<Unit>?=null
     try{
      assertTrue(withContext(Dispatchers.IO){creating.await(5,TimeUnit.SECONDS)})
      val closingCall=async(Dispatchers.IO){closeAttempt.countDown();route.close();closed.countDown()};closing=closingCall
      assertTrue(withContext(Dispatchers.IO){closeAttempt.await(5,TimeUnit.SECONDS)});release.countDown()
      withTimeout(5000){closingCall.await();assertTrue(call.await())};assertTrue(https.requests.isEmpty());assertFalse(route.current())
     }finally{release.countDown();closed.countDown();closing?.cancel();call.cancel()}
    }
   }
  }
 }
}
