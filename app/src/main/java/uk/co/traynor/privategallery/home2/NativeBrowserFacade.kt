package uk.co.traynor.privategallery.home2

import androidx.compose.runtime.*
import uk.co.traynor.privategallery.core.home2.NativeAppsBridge

/** Launcher metadata only. Production MainActivity remains the sole browser owner. */
data class PreviewTab(val id:String,val profile:String,val app:StoreEntry,val touched:Long=System.currentTimeMillis(),val mediaTitle:String="",val mediaState:String="none"){val title get()=app.name}
val standaloneBrowser=StoreEntry("browser-v1","Browser V1","https://www.google.com/","NovaOS","Browser","Existing Primary Browser V1")
class PreviewBrowserModel(private val apps:NativeAppsBridge?,private val onNative:(String)->Unit,private val current:()->Boolean) {
 val canBrowse get()=apps!=null&&current()
 var error by mutableStateOf<String?>(null)
 val tabs=mutableStateListOf<PreviewTab>()
 var currentProfile="primary"
 var activeId by mutableStateOf<String?>(null);private set
 val active get()=tabs.firstOrNull{it.id==activeId}
 fun recents(profile:String):List<PreviewTab> = if(current()&&profile==currentProfile)tabs.filter{it.profile==profile}.sortedByDescending{it.touched}else emptyList()
 fun open(entry:StoreEntry,profile:String):PreviewTab {
  check(current()&&profile==currentProfile)
  if(apps==null){error="Web browsing is unavailable in this Space while secure networking is deferred.";return PreviewTab(entry.id,profile,entry)}
  if(entry.id=="browser-v1"){onNative("browser-v1");return PreviewTab("browser-v1",profile,entry)}
  val bridge=apps
  val app=bridge.list().firstOrNull{it.id()==entry.id.removePrefix("web:")}?:run{error="This app is no longer installed. Open Nova Store to refresh your apps.";return PreviewTab(entry.id,profile,entry)}
  runCatching{bridge.launch(app.id())}.getOrElse{error="This app could not open. Check Browser connection and try again.";return PreviewTab(entry.id,profile,entry)}
  val tab=PreviewTab(app.id(),profile,entry);tabs.removeAll{it.id==tab.id};tabs.add(tab);activeId=tab.id;return tab
 }
 fun select(tab:PreviewTab){if(current()&&tab.profile==currentProfile)open(tab.app,tab.profile)}
 fun closeApp(profile:String,id:String){if(!current()||profile!=currentProfile)return;val matching=tabs.filter{it.profile==profile&&(it.app.id==id||it.id==id)};matching.forEach{apps?.close(it.id)};tabs.removeAll(matching.toSet());if(activeId in matching.map{it.id})activeId=null}
 fun close(tab:PreviewTab)=closeApp(tab.profile,tab.id)
 fun clear(){tabs.clear();activeId=null}
 fun navigate(tab:PreviewTab,url:String){if(!canBrowse){error="Web browsing is unavailable in this Space while secure networking is deferred.";return};check(tab.profile==currentProfile);apps?.browse(url)}
 fun setBrowserMode(tab:PreviewTab,enabled:Boolean) {check(current()) /* presentation owned by production V1 */}
 fun closeFullscreen() {} // No website view or fullscreen surface belongs to the launcher.
 fun pollMedia() {} // No inferred provider metadata: Return to Player is the fallback.
 fun player(profile:String):PreviewTab? = null
}
