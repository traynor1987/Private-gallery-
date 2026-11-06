package uk.co.traynor.privategallery.core.home2

import org.json.JSONObject
import uk.co.traynor.privategallery.home2.Wallpaper

/** Authoritative wallpaper record, separate from potentially retained Home layout snapshots.
 * Storage and publication still use SpaceContext's captured authentication leases and encryption.
 */
internal class WallpaperStore(private val context:SpaceContext) {
 private val preferences=context.getSharedPreferences("nova_preview_v1",0)
 fun read():Wallpaper {
  val dedicated=preferences.getString(KEY,null)
  if(dedicated!=null)return decode(JSONObject(dedicated))
  val state=preferences.getString("state",null)?.let(::JSONObject)
  return state?.optJSONObject(context.spaceId)?.optJSONObject("wallpaper")?.let(::decode)
   ?:Wallpaper(builtin=if(context.spaceId=="hidden")"Midnight"else "Aurora")
 }
 fun apply(draft:Wallpaper,bytes:ByteArray?):Wallpaper=context.wallpaperTransaction {
  val previous=read()
  var candidate:String?=null
  var committed=false
  try {
   candidate=bytes?.let(context::writeWallpaper)
   val next=draft.copy(image=candidate?:draft.image,imageRevision=System.currentTimeMillis())
   check(preferences.edit().putString(KEY,encode(next).toString()).commit()) {"Wallpaper could not be saved"}
   committed=true
   // Cleanup failure cannot undo a successful preference commit.
   runCatching{context.pruneWallpapers(setOfNotNull(next.image))}
   next
  }finally {
   if(!committed&&candidate!=null&&context.current())runCatching{context.pruneWallpapers(setOfNotNull(previous.image))}
  }
 }
 companion object {
  const val KEY="wallpaper-applied-v1"
  fun encode(w:Wallpaper)=JSONObject().put("builtin",w.builtin).put("image",w.image).put("imageRevision",w.imageRevision)
   .put("zoom",w.zoom).put("x",w.x).put("y",w.y).put("blur",w.blur).put("dim",w.dim)
  fun decode(w:JSONObject):Wallpaper {
   fun number(key:String,default:Float,min:Float,max:Float)=w.optDouble(key,default.toDouble()).toFloat().let{if(it.isFinite())it.coerceIn(min,max)else default}
   return Wallpaper(w.optString("builtin","Aurora").takeIf{it in listOf("Aurora","Midnight","Dusk")}?:"Aurora",
    w.optString("image").takeIf{it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))},
    number("zoom",1f,1f,3f),number("x",0f,-1f,1f),number("y",0f,-1f,1f),number("blur",0f,0f,24f),number("dim",.12f,0f,.8f),w.optLong("imageRevision",0).coerceAtLeast(0))
  }
 }
}
