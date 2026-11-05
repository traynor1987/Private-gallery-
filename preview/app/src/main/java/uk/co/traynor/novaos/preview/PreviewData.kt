package uk.co.traynor.novaos.preview

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class StoreEntry(val id:String,val name:String,val url:String,val publisher:String,val category:String,val description:String,val classification:String="Website Shortcut",val source:String="",val date:String="",val limitations:String="",val icon:String="")
data class AppFolder(val id:String,val name:String,val apps:List<String>)
data class Wallpaper(val builtin:String="Aurora",val image:String?=null,val zoom:Float=1f,val x:Float=0f,val y:Float=0f,val blur:Float=0f,val dim:Float=.12f,val imageRevision:Long=0)
data class Profile(val installed:List<StoreEntry> = emptyList(),val pages:List<List<String>> = listOf(listOf("store","folder:studio","guide"),listOf("settings")),val dock:List<String> = listOf("photos","store","wallpaper","settings"),val folders:List<AppFolder> = listOf(AppFolder("folder:studio","Studio",listOf("photos","wallpaper"))),val drawerOrder:List<String> = emptyList(),val wallpaper:Wallpaper=Wallpaper(),val columns:Int=4,val iconSize:Int=60)
data class PreviewState(val current:String="primary",val theme:String="dark",val primary:Profile=Profile(),val hidden:Profile=Profile(wallpaper=Wallpaper(builtin="Midnight")))
data class LaunchApp(val id:String,val name:String,val symbol:String,val website:StoreEntry?=null)
val builtinApps = listOf(LaunchApp("widgets","Widget Centre","widgets"),LaunchApp("search","Nova Search","search"),LaunchApp("video","Nova Video","video"),LaunchApp("backup","Home backup","backup"),LaunchApp("lock-settings","Preview lock","lock"),LaunchApp("mind","Nova Mind","mind"),LaunchApp("calm","Calm Now","mind"),LaunchApp("sanctuary","Nova Sanctuary","mind"),LaunchApp("browser-v1","Browser V1","browser"),LaunchApp("store","Nova Store","store"),LaunchApp("photos","Photos demo","photos"),LaunchApp("wallpaper","Wallpapers","wallpaper"),LaunchApp("settings","Home settings","settings"),LaunchApp("guide","Preview guide","guide"))

class PreviewData(private val context:Context) {
 val shell=PreviewShellData(context)
 private val prefs=context.getSharedPreferences("nova_preview_v1",Context.MODE_PRIVATE)
 var state by mutableStateOf(load());private set
 var error by mutableStateOf<String?>(null);private set
 private var pending:PreviewState?=null
 fun retrySave(){pending?.let{save(it)}}
 val profile get()=if(state.current=="hidden")state.hidden else state.primary
 val profileLabel get()=if(state.current=="hidden")"Hidden Preview" else "Primary Preview"
 val catalogue:List<StoreEntry> = parseCatalogue(context.assets.open("catalogue-v1.json").bufferedReader().use{it.readText()})
 fun layoutExport():JSONObject {
  val o=profileJson(profile);o.remove("installed");o.put("installedIds",JSONArray(profile.installed.map{it.id}))
  val w=o.getJSONObject("wallpaper");w.remove("imageRevision");w.put("image",profile.wallpaper.image?:"")
  return o
 }
 fun snapshotLayout():String=encode(state).toString()
 fun rollbackLayout(raw:String):Boolean {return runCatching{save(decode(JSONObject(raw)));error==null}.getOrDefault(false)}
 fun restoreLayout(layout:JSONObject):Boolean {
  val old=profile.installed
  val ids=layout.getJSONArray("installedIds");val restored=(0 until ids.length()).mapNotNull{i->val id=ids.getString(i);old.firstOrNull{it.id==id}?:catalogue.firstOrNull{entryKey(it)==id}?.copy(id=id)}
  val o=JSONObject(layout.toString());o.remove("installedIds");o.put("installed",JSONArray().apply{(old+restored).distinctBy{it.id}.forEach{put(entryJson(it))}})
  val wall=o.getJSONObject("wallpaper");val image=wall.optString("image");if(image.isBlank()||!java.io.File(context.filesDir,image).isFile)wall.remove("image")
  val next=readProfile(o);change{next};return error==null
 }
 fun profile(id:String)=if(id=="hidden")state.hidden else state.primary
 fun allApps(p:Profile=profile)=builtinApps+p.installed.map{entry->val artwork=catalogue.firstOrNull{PreviewRules.key(it.url)==PreviewRules.key(entry.url)};LaunchApp(entry.id,entry.name,"website",if(artwork!=null)entry.copy(icon=artwork.icon)else entry)}
 fun app(id:String)=allApps().firstOrNull{it.id==id}
 fun drawerIds(p:Profile=profile):List<String> {
  val grouped=p.folders.flatMap{it.apps}.toSet()
  val available=p.folders.map{it.id}+allApps(p).map{it.id}.filter{it !in grouped}
  return (p.drawerOrder.filter{it in available}+available).distinct()
 }
 fun entryKey(e:StoreEntry)="web:"+PreviewRules.key(e.url)
 fun installed(e:StoreEntry)=profile.installed.firstOrNull{it.id==entryKey(e)}
 fun switchProfile(){save(state.copy(current=if(state.current=="primary")"hidden" else "primary"))}
 fun setTheme(theme:String){if(theme in listOf("dark","light","system"))save(state.copy(theme=theme))}
 private fun save(next:PreviewState){
  val text=encode(next).toString()
  if(prefs.edit().putString("state",text).commit()){state=next;pending=null;error=null}else{pending=next;error="Preview changes could not be saved. Try again."}
 }
 fun change(id:String=state.current,transform:(Profile)->Profile){
  require(id in listOf("primary","hidden"))
  val before=profile(id)
  val next=transform(before);require(next.pages.size in 1..8 && next.dock.size<=5)
  save(if(id=="hidden")state.copy(hidden=next)else state.copy(primary=next))
  if(error==null&&before.wallpaper!=next.wallpaper)shell.record(id,"Wallpaper changed","wallpaper")
 }
 fun install(entry:StoreEntry){
  val url=PreviewRules.https(entry.url);val id="web:"+PreviewRules.key(url)
  if(profile.installed.any{it.id==id})return
  require(profile.installed.size<200){"Preview catalogue limit reached"}
  change{it.copy(installed=it.installed+entry.copy(id=id,url=url))}
  if(error==null)shell.record(state.current,"Installed ${entry.name}",id)
 }
 fun uninstall(id:String){change{p->
  val folders=p.folders.map{it.copy(apps=it.apps.filterNot{a->a==id})}.filter{it.apps.isNotEmpty()}
  val valid=allApps(p).map{it.id}.filterNot{it==id}.toSet()+folders.map{it.id}
  p.copy(installed=p.installed.filterNot{it.id==id},pages=p.pages.map{page->page.filter{it in valid}},dock=p.dock.filterNot{it==id},folders=folders,drawerOrder=p.drawerOrder.filter{it in valid})
 };if(error==null)shell.favourite(state.current,id,false)
 }
 fun addHome(id:String,page:Int=0){change{p->val target=page.coerceIn(0,p.pages.lastIndex);p.copy(pages=p.pages.mapIndexed{i,items->items.filterNot{it==id}+(if(i==target)listOf(id)else emptyList())})}}
 fun removeHome(id:String){change{it.copy(pages=it.pages.map{p->p.filterNot{a->a==id}})}}
 fun pin(id:String){change{p->if(id in p.dock)p else p.copy(dock=(p.dock+id).takeLast(5))}}
 fun unpin(id:String){change{it.copy(dock=it.dock.filterNot{a->a==id})}}
 fun moveDock(id:String,delta:Int){change{it.copy(dock=PreviewRules.move(it.dock,id,delta))}}
 fun moveDrawer(id:String,delta:Int){change{it.copy(drawerOrder=PreviewRules.move(drawerIds(it),id,delta))}}
 fun moveHome(id:String,page:Int,delta:Int){change{p->p.copy(pages=p.pages.mapIndexed{i,list->if(i==page)PreviewRules.move(list,id,delta)else list})}}
 fun addPage(){if(profile.pages.size<8)change{it.copy(pages=it.pages+listOf(emptyList()))}}
 fun removePage(page:Int){
  val w=WidgetData(context);val old=w.slots(state.current)
  if(profile.pages.size<=1||profile.pages[page].isNotEmpty()||old.any{it.page==page})return
  if(!w.save(state.current,old.map{if(it.page>page)it.copy(page=it.page-1)else it}))return
  change{it.copy(pages=it.pages.filterIndexed{i,_->i!=page})};if(error!=null)w.save(state.current,old)
 }
 fun movePage(page:Int,delta:Int){
  val target=(page+delta).coerceIn(0,profile.pages.lastIndex);val w=WidgetData(context);val old=w.slots(state.current)
  if(!w.save(state.current,old.map{when(it.page){page->it.copy(page=target);target->it.copy(page=page);else->it}}))return
  change{p->val next=p.pages.toMutableList();java.util.Collections.swap(next,page,target);p.copy(pages=next)};if(error!=null)w.save(state.current,old)
 }
 fun createFolder(name:String,apps:List<String>,page:Int){
  require(name.trim().isNotEmpty()&&apps.distinct().size>=2)
  val id="folder:"+java.util.UUID.randomUUID().toString()
  change{p->val pages=p.pages.map{it.filterNot{a->a in apps}}.toMutableList();val target=page.coerceIn(0,pages.lastIndex);pages[target]=pages[target]+id
   val groups=p.folders.map{it.copy(apps=it.apps.filterNot{a->a in apps})}.filter{it.apps.isNotEmpty()}+AppFolder(id,name.trim().take(32),apps.distinct())
   p.copy(pages=pages.map{row->row.filter{!it.startsWith("folder:")||groups.any{f->f.id==it}}},folders=groups,drawerOrder=p.drawerOrder.filter{!it.startsWith("folder:")||groups.any{f->f.id==it}})}
 }
 fun rename(id:String,name:String){if(name.isBlank())return;change{p->if(id.startsWith("folder:"))p.copy(folders=p.folders.map{if(it.id==id)it.copy(name=name.trim().take(32))else it}) else p.copy(installed=p.installed.map{if(it.id==id)it.copy(name=name.trim().take(40))else it})}}
 fun folderAdd(folder:String,app:String){change{p->val groups=p.folders.map{f->f.copy(apps=if(f.id==folder)(f.apps+app).distinct()else f.apps.filterNot{it==app})}.filter{it.apps.isNotEmpty()};p.copy(folders=groups,pages=p.pages.map{row->row.filter{!it.startsWith("folder:")||groups.any{f->f.id==it}}},drawerOrder=p.drawerOrder.filter{!it.startsWith("folder:")||groups.any{f->f.id==it}})}}
 fun moveFolderMember(folder:String,app:String,delta:Int){change{p->p.copy(folders=p.folders.map{if(it.id==folder)it.copy(apps=PreviewRules.move(it.apps,app,delta))else it})}}
 fun dissolveFolder(id:String){change{p->p.copy(folders=p.folders.filterNot{it.id==id},pages=p.pages.map{it.filterNot{a->a==id}},drawerOrder=p.drawerOrder.filterNot{it==id})}}
 fun removeFolderMember(folder:String,app:String){change{p->val folders=p.folders.map{if(it.id==folder)it.copy(apps=it.apps.filterNot{a->a==app})else it}.filter{it.apps.isNotEmpty()};p.copy(folders=folders,pages=p.pages.map{it.filter{a->!a.startsWith("folder:")||folders.any{f->f.id==a}}})}}
 fun reset(){change{p->Profile(installed=p.installed,wallpaper=Wallpaper(builtin=if(state.current=="hidden")"Midnight" else "Aurora"))}}
 private fun load():PreviewState=runCatching{
  val raw=prefs.getString("state",null)?:return PreviewState();require(raw.length<500000);decode(JSONObject(raw))
 }.getOrDefault(PreviewState())
 companion object {
  fun parseCatalogue(text:String):List<StoreEntry>{
   val root=JSONObject(text);require(root.getInt("schemaVersion")==1);val entries=root.getJSONArray("entries");require(entries.length() in 1..150)
   val result=(0 until entries.length()).map{i->entry(entries.getJSONObject(i),false)}
   require(result.map{it.id}.distinct().size==result.size);require(result.map{PreviewRules.key(it.url)}.distinct().size==result.size)
   return result
  }
  private fun entry(o:JSONObject,local:Boolean):StoreEntry {
   val url=PreviewRules.https(o.getString("url"));val name=o.getString("name").take(80);require(name.isNotBlank())
   val classification=o.optString("classification","Website Shortcut");require(classification in listOf("Website Shortcut","Web App","Verified PWA","Unsupported"))
   return StoreEntry(if(local)"web:"+PreviewRules.key(url)else o.getString("id"),name,url,o.optString("publisher","Custom website"),o.optString("category","Custom"),o.optString("description"),classification,o.optString("verificationSource"),o.optString("verifiedDate"),o.optString("limitations"),o.optString("iconAsset"))
  }
  private fun entryJson(e:StoreEntry)=JSONObject().put("id",e.id).put("name",e.name).put("url",e.url).put("publisher",e.publisher).put("category",e.category).put("description",e.description).put("classification",e.classification).put("verificationSource",e.source).put("verifiedDate",e.date).put("limitations",e.limitations).put("iconAsset",e.icon)
  private fun strings(a:JSONArray)= (0 until a.length()).map{a.getString(it)}
  private fun array(v:List<*>)=JSONArray(v)
  private fun profileJson(p:Profile)=JSONObject().put("installed",JSONArray().apply{p.installed.forEach{put(entryJson(it))}}).put("pages",JSONArray().apply{p.pages.forEach{put(array(it))}}).put("dock",array(p.dock)).put("drawer",array(p.drawerOrder)).put("folders",JSONArray().apply{p.folders.forEach{put(JSONObject().put("id",it.id).put("name",it.name).put("apps",array(it.apps)))}}).put("columns",p.columns).put("iconSize",p.iconSize).put("wallpaper",JSONObject().put("builtin",p.wallpaper.builtin).put("image",p.wallpaper.image).put("imageRevision",p.wallpaper.imageRevision).put("zoom",p.wallpaper.zoom).put("x",p.wallpaper.x).put("y",p.wallpaper.y).put("blur",p.wallpaper.blur).put("dim",p.wallpaper.dim))
  private fun encode(s:PreviewState)=JSONObject().put("schema",1).put("current",s.current).put("theme",s.theme).put("primary",profileJson(s.primary)).put("hidden",profileJson(s.hidden))
  private fun readProfile(o:JSONObject):Profile {
   val installed=o.getJSONArray("installed");require(installed.length()<=200)
   val apps=(0 until installed.length()).map{entry(installed.getJSONObject(it),true)}.distinctBy{it.id}
   val available=(builtinApps.map{it.id}+apps.map{it.id}).toSet()
   val folders=o.getJSONArray("folders");require(folders.length()<=64)
   val groups=(0 until folders.length()).map{val f=folders.getJSONObject(it);AppFolder(f.getString("id"),f.getString("name").take(32),strings(f.getJSONArray("apps")).filter{it in available}.distinct())}.filter{it.id.startsWith("folder:")&&it.apps.isNotEmpty()}.distinctBy{it.id}
   val valid=available+groups.map{it.id};val pages=o.getJSONArray("pages");require(pages.length() in 1..8)
   val home=(0 until pages.length()).map{strings(pages.getJSONArray(it)).filter{it in valid}.distinct().take(100)}
   val w=o.getJSONObject("wallpaper");val file=w.optString("image").takeIf{it in listOf("wallpaper-primary.jpg","wallpaper-hidden.jpg")}
   fun number(name:String,default:Double,low:Float,high:Float)=w.optDouble(name,default).toFloat().let{if(it.isFinite())it.coerceIn(low,high)else default.toFloat()}
   return Profile(apps,home,strings(o.getJSONArray("dock")).filter{it in available}.distinct().take(5),groups,strings(o.optJSONArray("drawer")?:JSONArray()).filter{it in valid}.distinct(),Wallpaper(w.optString("builtin","Aurora").takeIf{it in listOf("Aurora","Midnight","Dusk")}?:"Aurora",file,number("zoom",1.0,1f,3f),number("x",0.0,-1f,1f),number("y",0.0,-1f,1f),number("blur",0.0,0f,24f),number("dim",.12,0f,.8f),w.optLong("imageRevision",0L).coerceAtLeast(0)),o.optInt("columns",4).coerceIn(3,6),o.optInt("iconSize",60).coerceIn(44,76))
  }
  private fun decode(o:JSONObject):PreviewState {require(o.getInt("schema")==1);return PreviewState(o.optString("current").takeIf{it in listOf("primary","hidden")}?:"primary",o.optString("theme").takeIf{it in listOf("dark","light","system")}?:"dark",readProfile(o.getJSONObject("primary")),readProfile(o.getJSONObject("hidden")))}
 }
}
