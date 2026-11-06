package uk.co.traynor.privategallery.core.backup

import org.json.JSONArray
import org.json.JSONObject

/** Known offline widget fields only. Notes are encrypted user content; other configs are excluded. */
internal object PortableWidgets {
 fun sanitize(raw:String):String {
  require(raw.length<=300000);val source=JSONArray(raw);require(source.length()<=64)
  val slots=hashSetOf<String>();val items=hashSetOf<String>();val rectangles=mutableMapOf<Int,MutableList<IntArray>>()
  return JSONArray().apply{repeat(source.length()){index->
   val slot=source.getJSONObject(index);PortablePreferenceSchema.keys(slot,setOf("id","page","x","y","w","h","active","items"));val id=PortablePreferenceSchema.string(slot,"id",1,80);require(id.length in 1..80&&slots.add(id))
   val page=PortablePreferenceSchema.integer(slot,"page",0,7).toInt();require(page in 0..7)
   val x=PortablePreferenceSchema.integer(slot,"x",0,3).toInt();val y=PortablePreferenceSchema.integer(slot,"y",0,23).toInt();val w=PortablePreferenceSchema.integer(slot,"w",1,4).toInt();val h=PortablePreferenceSchema.integer(slot,"h",1,24).toInt()
   val bounds=intArrayOf(x,y,w,h);val occupied=rectangles.getOrPut(page){mutableListOf()}
   require(uk.co.traynor.privategallery.home2.WidgetRules.fits(bounds,occupied,-1));occupied+=bounds
   val entries=slot.getJSONArray("items");require(entries.length() in 1..8)
   val clean=JSONArray().apply{repeat(entries.length()){n->
    val item=entries.getJSONObject(n);PortablePreferenceSchema.keys(item,setOf("id","type","config"));val itemId=PortablePreferenceSchema.string(item,"id",1,80);require(itemId.length in 1..80&&items.add(itemId))
    val type=PortablePreferenceSchema.string(item,"type",1,40);require(type in setOf("clock","media","recents","mind","note","search"))
    val config=if(item.has("config"))PortablePreferenceSchema.string(item,"config",0,2000)else ""
    val sizes=when(type){"clock"->setOf(2 to 1,2 to 2,4 to 1);"mind"->setOf(2 to 1,2 to 2,4 to 1);"search"->setOf(2 to 1,4 to 1);"note"->setOf(2 to 2,4 to 2,4 to 4);"recents"->setOf(2 to 2,4 to 1,4 to 2);else->setOf(2 to 2,4 to 1,4 to 2,4 to 4)};require(w to h in sizes)
    val note=if(type=="note")config else "";require(note.length<=2000)
    put(JSONObject().put("id",itemId).put("type",type).put("config",note))
   }}
   val active=if(slot.has("active"))PortablePreferenceSchema.integer(slot,"active",0,(entries.length()-1).toLong()).toInt()else 0;require(active in 0 until entries.length())
   put(JSONObject().put("id",id).put("page",page).put("x",x).put("y",y).put("w",w).put("h",h).put("active",active).put("items",clean))
  }}.toString()
 }
}
