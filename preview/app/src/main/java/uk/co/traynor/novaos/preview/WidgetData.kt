package uk.co.traynor.novaos.preview
import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

data class WidgetSpec(val type:String,val name:String,val description:String,val sizes:List<Pair<Int,Int>>)
val widgetSpecs=listOf(
 WidgetSpec("clock","Clock & date","A quiet local clock and date.",listOf(2 to 1,2 to 2,4 to 1)),
 WidgetSpec("media","Now Playing","Real native media controls, supported website information and Return to App.",listOf(2 to 2,4 to 1,4 to 2,4 to 4)),
 WidgetSpec("recents","Recent apps","Resume retained Nova apps.",listOf(2 to 2,4 to 1,4 to 2)),
 WidgetSpec("mind","Nova Mind","A gentle shortcut to breathing and grounding.",listOf(2 to 1,2 to 2,4 to 1)),
 WidgetSpec("note","Quick Note","A local preview note. Text is excluded from Home backups.",listOf(2 to 2,4 to 2,4 to 4)),
 WidgetSpec("search","Nova Search","Find apps, settings, games and Mind tools locally.",listOf(2 to 1,4 to 1)))
data class WidgetItem(val id:String,val type:String,val config:String="")
data class WidgetSlot(val id:String,val page:Int,val x:Int,val y:Int,val w:Int,val h:Int,val items:List<WidgetItem>,val active:Int=0){fun rect()=intArrayOf(x,y,w,h)}
class WidgetData(context:Context){
 internal val prefs=context.getSharedPreferences("nova_widgets_v1",0)
 var revision by mutableIntStateOf(0);private set
 var error by mutableStateOf<String?>(null);private set
 private var readBlocked=false
 var undo by mutableStateOf<Pair<String,WidgetSlot>?>(null);private set
 var reduced by mutableStateOf(prefs.getBoolean("reduced",false));private set
 var demoLock by mutableStateOf(prefs.getBoolean("demoLock",false));private set
 fun reduced(value:Boolean):Boolean{return prefs.edit().putBoolean("reduced",value).commit().also{if(it)reduced=value else error="Could not save motion preference."}}
 fun demoLock(value:Boolean):Boolean{return prefs.edit().putBoolean("demoLock",value).commit().also{if(it)demoLock=value else error="Could not save lock preference."}}
 fun slots(profile:String):List<WidgetSlot>{revision;readBlocked=false;return runCatching{decode(JSONArray(prefs.getString("layout:$profile","[]")))}.getOrElse{readBlocked=true;error="Widget layout could not be read. Existing data has been preserved.";emptyList()}}
 fun save(profile:String,list:List<WidgetSlot>):Boolean{
  require(profile in listOf("primary","hidden"));require(list.size<=64)
  validate(list)
  return prefs.edit().putString("layout:$profile",encode(list).toString()).commit().also{if(it){revision++;error=null}else error="Could not save widgets. Try again."}
 }
 fun add(profile:String,type:String,w:Int,h:Int,page:Int):Boolean{
  val spec=widgetSpecs.firstOrNull{it.type==type}?:return false;require(w to h in spec.sizes)
  val all=slots(profile);if(readBlocked)return false
  val r=WidgetRules.firstFree(w,h,all.filter{it.page==page}.map{it.rect()})?:run{error="This page is full. Choose another Home page.";return false}
  return save(profile,all+WidgetSlot(java.util.UUID.randomUUID().toString(),page,r[0],r[1],w,h,listOf(WidgetItem(java.util.UUID.randomUUID().toString(),type))))
 }
 fun place(profile:String,id:String,x:Int,y:Int,w:Int,h:Int,page:Int):Boolean{
  val all=slots(profile);val slot=all.firstOrNull{it.id==id}?:return false
  if(slot.items.any{item->widgetSpecs.firstOrNull{it.type==item.type}?.sizes?.contains(w to h)==false}){error="That size is unavailable for a widget in this stack.";return false}
  if(!WidgetRules.fits(intArrayOf(x,y,w,h),all.filter{it.id!=id&&it.page==page}.map{it.rect()},-1)){error="That position is occupied or outside the page.";return false}
  return save(profile,all.map{if(it.id==id)it.copy(x=x,y=y,w=w,h=h,page=page)else it})
 }
 fun remove(profile:String,id:String){val all=slots(profile);val slot=all.firstOrNull{it.id==id}?:return;if(save(profile,all.filterNot{it.id==id}))undo=profile to slot}
 fun undoRemove(){val old=undo?:return;val all=slots(old.first);val slot=old.second;val r=if(WidgetRules.fits(slot.rect(),all.filter{it.page==slot.page}.map{it.rect()},-1))slot.rect()else WidgetRules.firstFree(slot.w,slot.h,all.filter{it.page==slot.page}.map{it.rect()});if(r!=null&&save(old.first,all+slot.copy(x=r[0],y=r[1])))undo=null}
 fun active(profile:String,id:String,index:Int){save(profile,slots(profile).map{if(it.id==id)it.copy(active=WidgetRules.active(index,it.items.size))else it})}
 fun note(profile:String,id:String,itemId:String,text:String){save(profile,slots(profile).map{if(it.id==id)it.copy(items=it.items.map{item->if(item.id==itemId)item.copy(config=text.take(2000))else item})else it})}
 fun stack(profile:String,id:String,otherId:String):Boolean{
  val all=slots(profile);val a=all.firstOrNull{it.id==id}?:return false;val b=all.firstOrNull{it.id==otherId}?:return false
  if(id==otherId||!WidgetRules.compatible(a.w,a.h,b.w,b.h)||a.items.size+b.items.size>8)return false
  return save(profile,all.filterNot{it.id==otherId}.map{if(it.id==id)it.copy(items=it.items+b.items)else it})
 }
 fun removeItem(profile:String,id:String,index:Int){val all=slots(profile);save(profile,all.mapNotNull{if(it.id!=id)it else {val items=it.items.filterIndexed{i,_->i!=index};if(items.isEmpty())null else it.copy(items=items,active=WidgetRules.active(it.active,items.size))}})}
 fun reorder(profile:String,id:String,index:Int,delta:Int){save(profile,slots(profile).map{if(it.id!=id)it else{val a=it.items.toMutableList();val target=(index+delta).coerceIn(0,a.lastIndex);java.util.Collections.swap(a,index,target);it.copy(items=a,active=target)}})}
 fun snapshot(profile:String)=prefs.getString("layout:$profile","[]")!!
 fun restoreRaw(profile:String,raw:String):Boolean=save(profile,decode(JSONArray(raw)))
 companion object {
  fun encode(slots:List<WidgetSlot>,backup:Boolean=false)=JSONArray().apply{slots.forEach{s->put(JSONObject().put("id",s.id).put("page",s.page).put("x",s.x).put("y",s.y).put("w",s.w).put("h",s.h).put("active",s.active).put("items",JSONArray().apply{s.items.forEach{put(JSONObject().put("id",it.id).put("type",it.type).put("config",if(backup)""else it.config))}}))}}
  fun decode(a:JSONArray):List<WidgetSlot>{require(a.length()<=64);return (0 until a.length()).map{i->val s=a.getJSONObject(i);val items=s.getJSONArray("items");require(items.length() in 1..8);WidgetSlot(s.getString("id"),s.getInt("page"),s.getInt("x"),s.getInt("y"),s.getInt("w"),s.getInt("h"),(0 until items.length()).map{j->val item=items.getJSONObject(j);WidgetItem(item.getString("id"),item.getString("type"),item.optString("config").take(2000))},WidgetRules.active(s.optInt("active"),items.length()))}.also{validate(it)}}
  fun validate(list:List<WidgetSlot>){require(list.map{it.id}.distinct().size==list.size);require(list.flatMap{it.items}.map{it.id}.distinct().size==list.sumOf{it.items.size});list.forEach{s->require(s.id.length in 1..80&&s.page in 0..7&&s.items.all{it.id.length in 1..80&&it.type.length in 1..40});require(WidgetRules.fits(s.rect(),list.filter{it.page==s.page&&it.id!=s.id}.map{it.rect()},-1));require(s.items.all{item->widgetSpecs.firstOrNull{it.type==item.type}?.sizes?.contains(s.w to s.h)!=false})}}
 }
}
