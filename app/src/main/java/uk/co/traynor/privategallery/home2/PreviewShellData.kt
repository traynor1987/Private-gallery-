package uk.co.traynor.privategallery.home2

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

data class NovaEvent(val id:String,val text:String,val time:Long,val target:String="",val read:Boolean=false)
/** Preview-only preferences, never browser credentials or production stores. */
class PreviewShellData(context:Context){
 val internalRecents=mutableStateListOf<Pair<String,String>>()
 fun touchInternal(profile:String,id:String){internalRecents.removeAll{it.first==profile&&it.second==id};internalRecents.add(0,profile to id)}
 fun closeInternal(profile:String,id:String){internalRecents.removeAll{it.first==profile&&it.second==id}}
 private val prefs=context.getSharedPreferences("nova_shell_v1",Context.MODE_PRIVATE)
 var dim by mutableFloatStateOf(prefs.getFloat("displayDim",0f).coerceIn(0f,.6f));private set
 fun dim(value:Float){val next=value.coerceIn(0f,.6f);if(prefs.edit().putFloat("displayDim",next).commit())dim=next else error="Unable to save Nova dimming."}
 var reverse by mutableStateOf(prefs.getBoolean("reverse",false));private set
 var gestures by mutableStateOf(prefs.getBoolean("gestures",false));private set
 var edge by mutableStateOf(prefs.getBoolean("edge",false));private set
 var left by mutableStateOf(prefs.getBoolean("left",false));private set
 var edgePosition by mutableFloatStateOf(prefs.getFloat("edgePosition",.5f).coerceIn(.2f,.8f));private set
 var revision by mutableIntStateOf(0);private set
 var error by mutableStateOf<String?>(null);private set
 fun configure(reverse:Boolean=this.reverse,gestures:Boolean=this.gestures,edge:Boolean=this.edge,left:Boolean=this.left,position:Float=edgePosition){
  if(prefs.edit().putBoolean("reverse",reverse).putBoolean("gestures",gestures).putBoolean("edge",edge).putBoolean("left",left).putFloat("edgePosition",position.coerceIn(.2f,.8f)).commit()){
   this.reverse=reverse;this.gestures=gestures;this.edge=edge;this.left=left;edgePosition=position.coerceIn(.2f,.8f);error=null
  }else error="Unable to save preview navigation settings."
 }
 fun events(profile:String):List<NovaEvent>{revision;return runCatching{val raw=prefs.getString("events:$profile","[]")!!;require(raw.length<20000);val a=JSONArray(raw);(0 until minOf(a.length(),40)).map{val e=a.getJSONObject(it);NovaEvent(e.getString("id"),e.getString("text").take(120),e.getLong("time"),e.optString("target"),e.optBoolean("read",false))}}.getOrDefault(emptyList())}
 private fun saveEvents(profile:String,events:List<NovaEvent>){
  val a=JSONArray();events.take(40).forEach{a.put(JSONObject().put("id",it.id).put("text",it.text.take(120)).put("time",it.time).put("target",it.target).put("read",it.read))}
  if(prefs.edit().putString("events:$profile",a.toString()).commit()){revision++;error=null}else error="Unable to save preview events."
 }
 fun record(profile:String,text:String,target:String=""){require(profile in listOf("primary","hidden"));saveEvents(profile,listOf(NovaEvent(java.util.UUID.randomUUID().toString(),text.take(120),System.currentTimeMillis(),target))+events(profile))}
 fun read(profile:String,id:String){saveEvents(profile,events(profile).map{if(it.id==id)it.copy(read=true)else it})}
 fun clear(profile:String){saveEvents(profile,emptyList())}
 fun delete(profile:String,id:String){saveEvents(profile,events(profile).filterNot{it.id==id})}
 fun favourites(profile:String):Set<String>{revision;return prefs.getStringSet("favourites:$profile",emptySet())?.toSet()?:emptySet()}
 fun favourite(profile:String,id:String,enabled:Boolean){val next=favourites(profile).toMutableSet();if(enabled){if(next.size>=6)return;next.add(id)}else next.remove(id);if(prefs.edit().putStringSet("favourites:$profile",next).commit())revision++ else error="Unable to save Edge favourites."}
}
