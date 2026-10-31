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
 @Test fun network_failure_is_unavailable_not_incompatible_metadata() {
  assertThrows(java.net.SocketTimeoutException::class.java) {
   PrivateAppDiscovery.discover(page,false,{true},{throw java.net.SocketTimeoutException("Synthetic timeout")},{""})
  }
  val seen=mutableListOf<String>()
  assertThrows(java.net.SocketTimeoutException::class.java) {
   PrivateAppDiscovery.discover(page,false,{true},{url ->
    seen+=url
    if(url==page)PrivateAppDiscovery.Resource("<link rel='manifest' href='/manifest.json'>".toByteArray(),"text/html")
    else throw java.net.SocketTimeoutException("Synthetic manifest timeout")
   },{""})
  }
  assertEquals(listOf(page,manifest),seen)
 }
 @Test fun oversize_and_malformed_documents_fail_closed_to_website() {
  val app=PrivateAppDiscovery.discover(page,false,{true},{PrivateAppDiscovery.Resource(ByteArray(PrivateAppDiscovery.MAX_DOCUMENT+1),"text/html")},{error("Do not decode")})
  assertEquals("",app.scope());assertEquals("",app.icon())
 }
 @Test fun private_web_app_defaults_to_compact_presentation_and_reports_capabilities() {
  val app=PrivateAppDiscovery.discover(page,false,{true},{url ->
   PrivateAppDiscovery.Resource((if(url==page)"<link rel='manifest' href='/manifest.json?x=1&amp;y=2'>" else """{"name":"App","start_url":"/app/home","scope":"/app/","display":"minimal-ui"}""").toByteArray(),if(url==page)"text/html" else "application/manifest+json")
  },{""})
  assertEquals("https://example.test/app/home",app.startUrl());assertEquals("minimal-ui",app.display())
  assertTrue(app.appStyle());assertFalse(app.addressBar());assertTrue(app.discoveryReport().contains("inside Private Gallery"))
 }
 @Test fun website_fallback_explains_manifest_failure_without_claiming_installation() {
  val app=PrivateAppDiscovery.discover(page,false,{true},{PrivateAppDiscovery.Resource("<html>No manifest</html>".toByteArray(),"text/html")},{""})
  assertTrue(app.scope().isEmpty());assertTrue(app.appStyle());assertFalse(app.addressBar())
  assertTrue(app.discoveryReport().contains("manifest"));assertFalse(app.discoveryReport().contains("Compatible private web app"))
 }

 @Test fun document_base_and_later_valid_manifest_icon_use_bounded_same_origin_transport() {
  val seen=mutableListOf<String>()
  val result=PrivateAppDiscovery.discover(page,false,{true},{url ->
   seen+=url
   when(url) {
    page->PrivateAppDiscovery.Resource("<base href='/assets/'><link rel='manifest' href='app.json'>".toByteArray(),"text/html")
    "https://example.test/assets/app.json"->PrivateAppDiscovery.Resource("""{"name":"Base app","start_url":"/app/home","scope":"/app/","display":"browser","display_override":["window-controls-overlay","standalone"],"icons":[{"src":"bad.png"},{"src":"good.png"}]}""".toByteArray(),"application/manifest+json")
    "https://example.test/assets/good.png"->PrivateAppDiscovery.Resource(byteArrayOf(1,2,3),"image/png")
    else->PrivateAppDiscovery.Resource(byteArrayOf(),"text/plain")
   }
  },{"AQID"})
  assertEquals("standalone",result.display());assertTrue(seen.contains("https://example.test/assets/good.png"))
  assertTrue(result.scope().isNotEmpty());assertEquals("AQID",result.icon())
 }

 @Test fun optional_icon_budget_cannot_downgrade_valid_private_web_app_metadata() {
  val seen=mutableListOf<String>()
  val app=PrivateAppDiscovery.discover(page,false,{true},{url ->seen+=url
   PrivateAppDiscovery.Resource((if(url==page)"<link rel='manifest' href='/manifest.json'>" else """{"name":"App","start_url":"/app/home","scope":"/app/","icons":[{"src":"/icon.png"}]}""").toByteArray(),if(url==page)"text/html" else "application/manifest+json")
  },{error("No optional icon decode after budget")},iconBudget={false})
  assertEquals(listOf(page,manifest),seen);assertTrue(app.scope().isNotEmpty());assertEquals("",app.icon())
 }

}
