package uk.co.traynor.novaos.preview

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

data class BrowserLink(val title:String,val url:String,val time:Long)
class BrowserLibrary(private val context:Context){
 private val prefs=context.getSharedPreferences("preview_browser_library_v1",0)
 var revision by mutableIntStateOf(0);private set
 var error by mutableStateOf<String?>(null)
 fun links(kind:String,profile:String):List<BrowserLink>{revision;return runCatching{val a=JSONArray(prefs.getString("$kind:$profile","[]"));(0 until minOf(a.length(),200)).map{val o=a.getJSONObject(it);BrowserLink(o.getString("title"),o.getString("url"),o.getLong("time"))}}.getOrDefault(emptyList())}
 fun add(kind:String,profile:String,title:String,url:String){
  val safe=runCatching{PreviewRules.https(url)}.getOrNull()?:return
  val list=(listOf(BrowserLink(title.take(160),safe,System.currentTimeMillis()))+links(kind,profile).filterNot{it.url==safe}).take(200)
  val a=JSONArray();list.forEach{a.put(JSONObject().put("title",it.title).put("url",it.url).put("time",it.time))};if(prefs.edit().putString("$kind:$profile",a.toString()).commit())revision++ else error="Unable to save browser list."
 }
 fun clear(kind:String,profile:String){if(prefs.edit().remove("$kind:$profile").commit())revision++}
 fun flag(key:String,default:Boolean=false):Boolean{revision;return prefs.getBoolean(key,default)}
 fun setFlag(key:String,value:Boolean){if(prefs.edit().putBoolean(key,value).commit())revision++}
 fun download(profile:String,url:String,title:String){
  runCatching{
   val safe=PreviewRules.https(url);val uri=Uri.parse(safe)
   require(uri.userInfo==null)
   val name=android.webkit.URLUtil.guessFileName(safe,null,null).replace(Regex("[^a-zA-Z0-9._-]"),"_").take(100).ifBlank{"nova-download"}
   val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
   val request=DownloadManager.Request(uri).setTitle(name).setDescription("NovaOS Preview download").setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
   val id=dm.enqueue(request)
   prefs.edit().putString("download:$id",profile).apply();add("downloads",profile,title,safe)
  }.onFailure{error="Unable to download. This preview supports public HTTPS files; authenticated or blob downloads may require the website’s own controls."}
 }
 fun refreshDownloads(shell:PreviewShellData){
  val dm=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
  prefs.all.filterKeys{it.startsWith("download:")}.forEach{(key,value)->runCatching{
   val id=key.removePrefix("download:").toLong();dm.query(DownloadManager.Query().setFilterById(id)).use{c->if(c.moveToFirst()){
    when(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))){DownloadManager.STATUS_SUCCESSFUL->{shell.record(value as String,"Download completed","browser-v1");prefs.edit().remove(key).apply()};DownloadManager.STATUS_FAILED->{error="A download failed. Check Downloads in Android’s Files app.";prefs.edit().remove(key).apply()}}
   }else prefs.edit().remove(key).apply()}
  }}
 }
}
val standaloneBrowser=StoreEntry("browser-v1","Browser V1","https://www.google.com/","NovaOS","Browser","Ordinary preview browser",classification="Website Shortcut")
