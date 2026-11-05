package uk.co.traynor.novaos.preview

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject

data class MindEntry(val id:String,val time:Long,val text:String,val mood:String,val journal:Boolean)
/** Ordinary preview-local storage. Never encrypted, synced, logged or sent to a website. */
class MindData(context:Context){
 private val prefs=context.getSharedPreferences("nova_mind_v1",Context.MODE_PRIVATE)
 var revision by mutableIntStateOf(0);private set
 var error by mutableStateOf<String?>(null);private set
 var preferred by mutableStateOf(prefs.getString("preferred","breathing")?:"breathing");private set
 fun prefer(value:String){require(value in listOf("breathing","grounding"));if(prefs.edit().putString("preferred",value).commit())preferred=value else error="Unable to save preference."}
 fun entries(profile:String):List<MindEntry>{revision;return runCatching{val a=JSONArray(prefs.getString(profile,"[]"));require(a.length()<=1000);(0 until a.length()).map{val o=a.getJSONObject(it);MindEntry(o.getString("id"),o.getLong("time"),o.getString("text"),o.getString("mood"),o.optBoolean("journal",true))}}.getOrElse{error="Unable to read entries. Existing data has been preserved.";emptyList()}}
 private fun save(profile:String,list:List<MindEntry>):Boolean{
  require(profile in listOf("primary","hidden"));if(list.size>1000){error="Preview limit reached. Delete an entry before adding another.";return false}
  val a=JSONArray();list.forEach{a.put(JSONObject().put("id",it.id).put("time",it.time).put("text",it.text).put("mood",it.mood).put("journal",it.journal))}
  return prefs.edit().putString(profile,a.toString()).commit().also{if(it){revision++;error=null}else error="Unable to save. Keep your text here and try again."}
 }
 fun put(profile:String,text:String,mood:String="",journal:Boolean=true,id:String?=null):Boolean{
  if(text.isBlank()&&mood.isBlank())return false
  if(error?.startsWith("Unable to read")==true)return false
  val old=entries(profile);if(error?.startsWith("Unable to read")==true)return false
  val entry=MindEntry(id?:java.util.UUID.randomUUID().toString(),old.firstOrNull{it.id==id}?.time?:System.currentTimeMillis(),text.take(20000),mood.take(40),journal)
  return save(profile,(listOf(entry)+old.filterNot{it.id==entry.id}).sortedByDescending{it.time})
 }
 fun delete(profile:String,id:String):Boolean {val old=entries(profile);if(error?.startsWith("Unable to read")==true)return false;return save(profile,old.filterNot{it.id==id})}
}
