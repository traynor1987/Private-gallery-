package uk.co.traynor.privategallery.core.privateapps

import org.json.JSONObject
import java.net.URI
import java.util.UUID
import uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy

/** Public metadata only. No credentials, installation, script execution or transport selection. */
internal object PrivateAppDiscovery {
 const val MAX_DOCUMENT=128*1024
 data class Resource(val bytes:ByteArray,val mime:String)
 data class Metadata(val name:String,val start:String,val scope:String="",val display:String="browser",val iconUrl:String="")
 fun safe(url:String,tor:Boolean)=PrivateAppPolicy.safeUrl(url,tor) && HiddenConnectionPolicy.safeUrl(url,if(tor)HiddenConnectionPolicy.Mode.TOR_ONLY else HiddenConnectionPolicy.Mode.VPN_ONLY)
 fun resolve(base:String,value:String,tor:Boolean):String?=runCatching {
  require(value.length<=PrivateAppPolicy.MAX_URL && value.none {it.code<=32 || it=='\\'})
  URI(base).resolve(value).normalize().toString().takeIf{safe(it,tor) && PrivateAppPolicy.sameOrigin(base,it)}
 }.getOrNull()
 private fun links(html:String):List<Map<String,String>> {
  // This deliberately narrow extractor is not an HTML renderer. Malformed/long tags fall back safely.
  val visible=html.replace(Regex("<!--.*?-->|<script\\b[^>]*>.*?</script\\s*>",setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)),"")
  return Regex("<link\\b[^>]{0,4096}>",RegexOption.IGNORE_CASE).findAll(visible).take(32).map {tag ->
   Regex("([a-zA-Z][a-zA-Z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))").findAll(tag.value).associate {m ->
    m.groupValues[1].lowercase() to m.groupValues.drop(2).firstOrNull{it.isNotEmpty()}.orEmpty()
   }
  }.toList()
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
  val display=obj.optString("display","browser")
  require(display in setOf("browser","standalone","minimal-ui","fullscreen"))
  val icons=obj.optJSONArray("icons")
  val icon=(0 until minOf(icons?.length() ?: 0,16)).firstNotNullOfOrNull {i ->
   val item=icons?.optJSONObject(i)
   val type=item?.optString("type").orEmpty()
   if(type.isNotEmpty() && type !in setOf("image/png","image/jpeg","image/webp","image/x-icon","image/vnd.microsoft.icon"))null
   else item?.optString("src")?.takeIf{it.isNotEmpty()}?.let{resolve(url,it,tor)}
  }.orEmpty()
  return Metadata(name,start,scope,display,icon)
 }
 fun discover(page:String,tor:Boolean,current:()->Boolean,fetch:(String)->Resource,normalizeIcon:(ByteArray)->String,diagnostic:(String,Throwable)->Unit={_,_->}):PrivateAppCatalog.App {
  check(current());require(safe(page,tor))
  var metadata=Metadata(URI(page).host,page)
  var favicon=resolve(page,"/favicon.ico",tor).orEmpty()
  fun get(url:String):Resource {check(current());require(resolve(page,url,tor)!=null);return fetch(url).also{try{check(current());require(it.bytes.size<=MAX_DOCUMENT)}catch(e:Exception){it.bytes.fill(0);throw e}}}
  var phase="fetch_page"
  fun report(stage:String,failure:Throwable){runCatching{diagnostic(stage,failure)}}
  runCatching {
   val html=get(page)
   val tags=try {phase="parse_page";require(html.mime in setOf("text/html","application/xhtml+xml"));links(html.bytes.toString(Charsets.UTF_8))}finally{html.bytes.fill(0)}
   tags.firstOrNull{it["rel"].orEmpty().lowercase().split(Regex("\\s+")).contains("icon")}?.get("href")?.let {resolve(page,it,tor)}?.let{favicon=it}
   val url=tags.firstOrNull{it["rel"].orEmpty().lowercase().split(Regex("\\s+")).contains("manifest")}?.get("href")?.let{resolve(page,it,tor)} ?: return@runCatching
   phase="fetch_manifest"
   val resource=get(url)
   try {phase="parse_manifest";require(resource.mime in setOf("application/manifest+json","application/json"));metadata=manifest(page,url,resource.bytes.toString(Charsets.UTF_8),tor)}finally{resource.bytes.fill(0)}
  }.onFailure {failure ->
   report(phase,failure)
   // Network unavailability is not an incompatible manifest. Existing UI offers manual save.
   if(failure is java.io.IOException)throw failure
  }
  check(current())
  var icon=""
  for(url in listOf(metadata.iconUrl,favicon).filter{it.isNotEmpty()}.distinct().take(2)) {
   icon=runCatching {val resource=get(url);try {require(resource.mime.startsWith("image/") && resource.mime!="image/svg+xml");normalizeIcon(resource.bytes)}finally{resource.bytes.fill(0)}}.onFailure {report("icon",it)}.getOrDefault("")
   check(current());if(icon.isNotEmpty())break
  }
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),metadata.name,metadata.start,metadata.scope,metadata.display,icon,false,true,false)
  return PrivateAppCatalog(tor,listOf(app)).apps.single().also{check(current())}
 }
}
