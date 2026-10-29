package uk.co.traynor.privategallery.core.privateapps

import org.junit.Assert.*
import org.junit.Test

class PrivateAppDiscoveryTest {
 private val page="https://example.test/app/index.html"
 private val manifest="https://example.test/manifest.json"
 @Test fun manifest_resolves_names_scope_display_and_icon() {
  val m=PrivateAppDiscovery.manifest(page,manifest,"""{"name":"Fixture App","short_name":"Fixture","start_url":"/app/home","scope":"/app/","display":"standalone","icons":[{"src":"https://elsewhere.test/icon.png"},{"src":"/icon.png","type":"image/png"}]}""",false)
  assertEquals("Fixture App",m.name);assertEquals("https://example.test/app/home",m.start);assertEquals("https://example.test/app/",m.scope);assertEquals("standalone",m.display);assertEquals("https://example.test/icon.png",m.iconUrl)
  assertEquals("Short",PrivateAppDiscovery.manifest(page,manifest,"""{"short_name":"Short"}""",false).name)
 }
 @Test fun rejects_unsafe_urls_cross_origin_and_out_of_scope_metadata() {
  for(url in listOf("https://127.0.0.1/x","https://localhost/x","https://foo.local/x","https://user:pass@example.test/","https://example.test:8443/","http://example.test/","file:///secret","data:text/plain,hi"))assertFalse(url,PrivateAppDiscovery.safe(url,false))
  for(ref in listOf("https://other.test/x","//other.test/x","javascript:alert(1)","https://example.test\\@other.test/x"))assertNull(PrivateAppDiscovery.resolve(page,ref,false))
  for(json in listOf("""{"name":"X","start_url":"https://other.test/"}""","""{"name":"X","start_url":"/outside","scope":"/app/"}""","""{"name":"X","display":"window-controls-overlay"}""","""{"name":"X","scope":"/app/?secret=1"}""")) {
   assertThrows(Exception::class.java){PrivateAppDiscovery.manifest(page,manifest,json,false)}
  }
  val onion="http://"+"a".repeat(56)+".onion/"
  assertFalse(PrivateAppDiscovery.safe(onion,false));assertTrue(PrivateAppDiscovery.safe(onion,true))
 }
 @Test fun synthetic_discovery_uses_bounded_same_origin_resources_and_wipes_them() {
  val bytes=mutableListOf<ByteArray>();val urls=mutableListOf<String>()
  val app=PrivateAppDiscovery.discover(page,false,{true},{url ->
   urls+=url
   val (text,mime)=when(url){page->"<link rel='manifest' href='/manifest.json'>" to "text/html";manifest->"""{"name":"Fixture","start_url":"/app/home","scope":"/app/","icons":[{"src":"/icon.png"}]}""" to "application/manifest+json";else->"icon" to "image/png"}
   PrivateAppDiscovery.Resource(text.toByteArray().also{bytes+=it},mime)
  },{""})
  assertEquals("Fixture",app.name());assertEquals("https://example.test/app/",app.scope());assertTrue(urls.all{it.startsWith("https://example.test/")});assertTrue(bytes.all{b->b.all{it==0.toByte()}})
 }
 @Test fun invalid_manifest_falls_back_and_revocation_never_publishes() {
  val app=PrivateAppDiscovery.discover(page,false,{true},{url ->PrivateAppDiscovery.Resource((if(url==page)"<link rel='manifest' href='https://other.test/x'>" else "").toByteArray(),"text/html")},{""})
  assertEquals("",app.scope());assertEquals(page,app.startUrl())
  var live=true;val bytes="secret".toByteArray()
  assertThrows(IllegalStateException::class.java){PrivateAppDiscovery.discover(page,false,{live},{live=false;PrivateAppDiscovery.Resource(bytes,"text/html")},{""})}
  assertTrue(bytes.all{it==0.toByte()})
 }
 @Test fun oversize_and_malformed_documents_fail_closed_to_website() {
  val app=PrivateAppDiscovery.discover(page,false,{true},{PrivateAppDiscovery.Resource(ByteArray(PrivateAppDiscovery.MAX_DOCUMENT+1),"text/html")},{error("Do not decode")})
  assertEquals("",app.scope());assertEquals("",app.icon())
 }
}
