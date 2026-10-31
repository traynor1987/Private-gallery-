package uk.co.traynor.privategallery.core.privateapps

import org.json.JSONObject
import java.net.URI
import java.util.UUID
import uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy

/** Public metadata only. No credentials, installation, script execution or transport selection. */
internal object PrivateAppDiscovery {
 const val MAX_DOCUMENT=128*1024
 data class Resource(val bytes:ByteArray,val mime:String)
 data class Metadata(val name:String,val start:String,val scope:String="",val display:String="browser",val iconUrl:String="",val iconCandidates:List<String> = emptyList())
 fun safe(url:String,tor:Boolean)=PrivateAppPolicy.safeUrl(url,tor) && HiddenConnectionPolicy.safeUrl(url,if(tor)HiddenConnectionPolicy.Mode.TOR_ONLY else HiddenConnectionPolicy.Mode.VPN_ONLY)
 fun resolve(base:String,value:String,tor:Boolean):String?=runCatching {
  require(value.length<=PrivateAppPolicy.MAX_URL && value.none {it.code<=32 || it=='\\'})
  URI(base).resolve(value).normalize().toString().takeIf{safe(it,tor) && PrivateAppPolicy.sameOrigin(base,it)}
 }.getOrNull()
 private fun htmlValue(value:String)=value.replace("&amp;","&").replace("&#38;","&").replace("&#x26;","&").replace("&quot;","\"").replace("&#39;","'")
 private fun attributes(tag:String)=Regex("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").findAll(tag).associate {m ->
  m.groupValues[1].lowercase() to htmlValue(m.groupValues.drop(2).firstOrNull{it.isNotEmpty()}.orEmpty())
 }
 private fun visibleHtml(html:String)=html.replace(Regex("<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)),"")
 private fun links(html:String):List<Map<String,String>> = Regex("<link\\b[^>]{0,4096}>",RegexOption.IGNORE_CASE).findAll(html).take(32).map {attributes(it.value)}.toList()
 private fun documentBase(page:String,html:String,tor:Boolean):String {
  val href=Regex("<base\\b[^>]{0,4096}>",RegexOption.IGNORE_CASE).find(html)?.let {attributes(it.value)["href"]} ?: return page
  return resolve(page,href,tor) ?: error("Unsupported document base")
 }

 fun manifest(page:String,url:String,json:String,tor:Boolean):Metadata {
  require(safe(page,tor) && resolve(page,url,tor)!=null && json.length<=MAX_DOCUMENT)
  val obj=JSONObject(json)
  val name=listOf(obj.optString("name"),obj.optString("short_name")).firstOrNull{PrivateAppPolicy.validName(it)} ?: error("Missing manifest name")
  val start=resolve(url,obj.optString("start_url",page),tor) ?: error("Unsafe start URL")
  require(PrivateAppPolicy.sameOrigin(page,start))
  val defaultScope=URI(start).resolve(".").toString()
  val scope=resolve(url,obj.optString("scope",defaultScope),tor) ?: error("Unsafe scope")
  require(URI(scope).rawQuery==null && URI(scope).rawFragment==null && PrivateAppPolicy.inScope(start,scope))
  val supported=setOf("browser","standalone","minimal-ui","fullscreen")
  val overrides=obj.optJSONArray("display_override")
  val display=(0 until minOf(overrides?.length() ?: 0,16)).map {overrides?.optString(it).orEmpty()}.firstOrNull {it in supported} ?: obj.optString("display","browser")
  require(display in supported)
  val icons=obj.optJSONArray("icons")
  val candidates=(0 until minOf(icons?.length() ?: 0,16)).mapNotNull {i ->
   val item=icons?.optJSONObject(i)
   val type=item?.optString("type").orEmpty()
   if(type.isNotEmpty() && type !in setOf("image/png","image/jpeg","image/webp","image/x-icon","image/vnd.microsoft.icon"))null
   else item?.optString("src")?.takeIf{it.isNotEmpty()}?.let{resolve(url,it,tor)}
  }.distinct().take(3)
  return Metadata(name,start,scope,display,candidates.firstOrNull().orEmpty(),candidates)
 }
 fun discover(page:String,tor:Boolean,current:()->Boolean,fetch:(String)->Resource,normalizeIcon:(ByteArray)->String,diagnostic:(String,Throwable)->Unit={_,_->},iconBudget:()->Boolean={true}):PrivateAppCatalog.App {
  check(current());require(safe(page,tor))
  var metadata=Metadata(URI(page).host,page)
  var favicon=resolve(page,"/favicon.ico",tor).orEmpty()
  fun get(url:String):Resource {check(current());require(resolve(page,url,tor)!=null);return fetch(url).also{try{check(current());require(it.bytes.size<=MAX_DOCUMENT)}catch(e:Exception){it.bytes.fill(0);throw e}}}
  var phase="fetch_page"
  var compatibility="No manifest link was found in the public page. Login-only or script-generated manifests cannot be discovered privately."
  fun report(stage:String,failure:Throwable){runCatching{diagnostic(stage,failure)}}
  runCatching {
   val html=get(page)
   var base=page
   val tags=try {phase="parse_page";require(html.mime in setOf("text/html","application/xhtml+xml"));val source=visibleHtml(html.bytes.toString(Charsets.UTF_8));base=documentBase(page,source,tor);links(source)}finally{html.bytes.fill(0)}
   tags.firstOrNull{it["rel"].orEmpty().lowercase().split(Regex("\\s+")).contains("icon")}?.get("href")?.let {resolve(base,it,tor)}?.let{favicon=it}
   val url=tags.firstOrNull{it["rel"].orEmpty().lowercase().split(Regex("\\s+")).contains("manifest")}?.get("href")?.let{resolve(base,it,tor)} ?: run {compatibility="No supported same-origin manifest link was found. Cross-origin metadata is not fetched.";return@runCatching}
   phase="fetch_manifest"
   val resource=get(url)
   try {phase="parse_manifest";require(resource.mime in setOf("application/manifest+json","application/json"));metadata=manifest(page,url,resource.bytes.toString(Charsets.UTF_8),tor);compatibility="Compatible private web app: ${metadata.display}. Runs inside Private Gallery; push, background execution and guaranteed offline operation are unsupported."}finally{resource.bytes.fill(0)}
  }.onFailure {failure ->
   report(phase,failure)
   // Network unavailability is not an incompatible manifest. Existing UI offers manual save.
   if(failure is java.io.IOException)throw failure
   compatibility=when(phase) {
    "fetch_page"->"The public page could not be read without authentication or a redirect. Open its final HTTPS address and check again."
    "parse_page"->"The page has an unsupported document base or response type, or exceeds the bounded HTML limit."
    "fetch_manifest"->"The manifest could not be read without authentication or a redirect."
    else->"The manifest has unsupported or invalid name, start URL, scope or display metadata."
   }
  }
  check(current())
  var icon=""
  for(url in (metadata.iconCandidates+listOf(metadata.iconUrl,favicon)).filter{it.isNotEmpty()}.distinct().take(4)) {
   check(current());if(!iconBudget())break
   icon=runCatching {val resource=get(url);try {require(resource.mime.startsWith("image/") && resource.mime!="image/svg+xml");normalizeIcon(resource.bytes)}finally{resource.bytes.fill(0)}}.onFailure {report("icon",it)}.getOrDefault("")
   check(current());if(icon.isNotEmpty())break
  }
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),metadata.name,metadata.start,metadata.scope,metadata.display,icon,false,false,false,true,compatibility)
  return PrivateAppCatalog(tor,listOf(app)).apps.single().also{check(current())}
 }
}
