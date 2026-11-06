package uk.co.traynor.privategallery.core.backup

import org.json.JSONArray
import org.json.JSONObject

/** Archive authentication does not validate application schemas. No coercion or clamping on import. */
internal object PortablePreferenceSchema {
 private val uuid=Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
 private val reference=Regex("[a-z][a-z0-9:_-]{0,100}")
 fun validate(name:String,values:Map<String,Any>,owner:String) {
  require(owner in setOf("primary","hidden"))
  fun keys(vararg allowed:String){require(values.keys.all{it in allowed}){"Unsupported preference key"}}
  fun boolean(key:String){values[key]?.let{require(it is Boolean){"Invalid Boolean preference"}}}
  fun text(key:String,action:(String)->Unit){values[key]?.let{require(it is String);action(it)}}
  fun floating(key:String,min:Float,max:Float){values[key]?.let{require(it is Float&&it.isFinite()&&it in min..max)}}
  when(name){
   "nova_preview_v1"->{
    keys("state","wallpaper-applied-v1","native-theme-bridged");boolean("native-theme-bridged")
    text("wallpaper-applied-v1"){wallpaper(JSONObject(it))}
    text("state"){raw->val state=JSONObject(raw);keys(state,setOf("schema","current","theme","profile"));require(integer(state,"schema",1,1)==1L&&string(state,"current",1,10)==owner);require(string(state,"theme",1,10) in setOf("light","dark","system"));profile(state.getJSONObject("profile"))}
   }
   "nova_widgets_v1"->{keys("reduced","clock:$owner","layout:$owner");boolean("reduced");boolean("clock:$owner");text("layout:$owner"){PortableWidgets.sanitize(it)}}
   "nova_mind_v1"->{
    keys(owner,"preferred");text("preferred"){require(it in setOf("breathing","grounding"))}
    text(owner){raw->val entries=JSONArray(raw);require(entries.length()<=1000);val ids=hashSetOf<String>();repeat(entries.length()){i->
     val entry=entries.getJSONObject(i);keys(entry,setOf("id","time","text","mood","journal"));val id=string(entry,"id",36,36);require(uuid.matches(id)&&ids.add(id));integer(entry,"time",1,253402300799000L);string(entry,"text",0,20000);string(entry,"mood",0,40);if(entry.has("journal"))require(entry.get("journal") is Boolean)
    }}
   }
   "nova_shell_v1"->{
    keys("displayDim","reverse","gestures","edge","left","edgePosition","favourites:$owner")
    listOf("reverse","gestures","edge","left").forEach(::boolean);floating("displayDim",0f,.6f);floating("edgePosition",.2f,.8f)
    values["favourites:$owner"]?.let{value->require(value is Set<*>&&value.size<=6&&value.all{it is String&&reference.matches(it)})}
   }
   // Arcade uses the same complete codec as local loads. Calculator remains reserved until its schema is installed.
   "nova_arcade_v1"->uk.co.traynor.privategallery.core.arcade.ArcadePreferences.validate(values)
   "nova_calculator_v1"->require(values.isEmpty()){"Application backup schema is not installed"}
   else->error("Unsupported preference namespace")
  }
 }
 internal fun keys(json:JSONObject,allowed:Set<String>){require(json.keys().asSequence().all{it in allowed}){"Unknown portable field"}}
 internal fun string(json:JSONObject,key:String,min:Int,max:Int):String {val value=json.get(key);require(value is String&&value.length in min..max);return value}
 internal fun integer(json:JSONObject,key:String,min:Long,max:Long):Long {val value=json.get(key);require(value is Int||value is Long);return (value as Number).toLong().also{require(it in min..max)}}
 internal fun number(json:JSONObject,key:String,min:Double,max:Double){val value=json.get(key);require(value is Number&&value.toDouble().isFinite()&&value.toDouble() in min..max)}
 private fun refs(array:JSONArray,max:Int){require(array.length()<=max);val seen=hashSetOf<String>();repeat(array.length()){val value=array.get(it);require(value is String&&reference.matches(value)&&seen.add(value))}}
 private fun profile(json:JSONObject){
  keys(json,setOf("installed","pages","dock","drawer","folders","columns","iconSize","wallpaper"))
  require(json.getJSONArray("installed").length()==0){"App definitions must use the catalogue category"}
  val pages=json.getJSONArray("pages");require(pages.length() in 1..8);repeat(pages.length()){refs(pages.getJSONArray(it),100)}
  refs(json.getJSONArray("dock"),5);if(json.has("drawer"))refs(json.getJSONArray("drawer"),512)
  val folders=json.getJSONArray("folders");require(folders.length()<=64);val ids=hashSetOf<String>();repeat(folders.length()){
   val folder=folders.getJSONObject(it);keys(folder,setOf("id","name","apps"));val id=string(folder,"id",8,101);require(id.startsWith("folder:")&&reference.matches(id)&&ids.add(id));string(folder,"name",1,32);refs(folder.getJSONArray("apps"),100)
  }
  if(json.has("columns"))integer(json,"columns",3,6);if(json.has("iconSize"))integer(json,"iconSize",44,76)
  wallpaper(json.getJSONObject("wallpaper"))
 }
 private fun wallpaper(json:JSONObject){
  keys(json,setOf("builtin","image","imageRevision","zoom","x","y","blur","dim"))
  if(json.has("builtin"))require(string(json,"builtin",1,20) in setOf("Aurora","Midnight","Dusk"))
  if(json.has("image")&&!json.isNull("image"))require(uuid.matches(string(json,"image",36,36)))
  if(json.has("imageRevision"))integer(json,"imageRevision",0,Long.MAX_VALUE)
  for((key,range) in mapOf("zoom" to (1.0..3.0),"x" to (-1.0..1.0),"y" to (-1.0..1.0),"blur" to (0.0..24.0),"dim" to (0.0..0.8)))if(json.has(key))number(json,key,range.start,range.endInclusive)
 }
}
