package uk.co.traynor.privategallery.core.privateapps

import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PrivateAppMetadataTransportTest {
 @Test fun refuses_redirects_and_never_sends_credentials() {
  val seen=mutableListOf<String>()
  val builder=OkHttpClient.Builder().addInterceptor {chain ->
   val request=chain.request();seen+=request.url.toString()
   assertNull(request.header("Cookie"));assertNull(request.header("Authorization"));assertNull(request.header("Referer"))
   Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(302).message("Redirect")
    .header("Location","https://127.0.0.1/private").body("".toResponseBody()).build()
  }
  val app=PrivateAppMetadataTransport.discover("https://example.test/",false,builder,{true})
  assertEquals("",app.scope());assertEquals(listOf("https://example.test/","https://example.test/favicon.ico"),seen)
 }
 @Test fun rejects_before_request_when_authority_is_revoked() {
  var calls=0
  val builder=OkHttpClient.Builder().addInterceptor{calls++;error("No requests")}
  assertThrows(IllegalStateException::class.java){PrivateAppMetadataTransport.discover("https://example.test/",false,builder,{false})}
  assertEquals(0,calls)
 }
}
