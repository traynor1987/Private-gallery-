package uk.co.traynor.privategallery.core.vault

import org.json.JSONObject

/** Portable presentation values can live in the atomic index; later local overrides stay separate. */
internal object VaultPresentationMetadata {
 fun encode(item:VaultItem):JSONObject = JSONObject().apply {
  item.addedAtEpochMillis?.let{put("added",it)};item.capturedAtEpochMillis?.let{put("captured",it)}
  item.adjustedTakenAtEpochMillis?.let{put("adjusted",it)};item.adjustedTimeZone?.let{put("zone",it)}
  item.modifiedAtEpochMillis?.let{put("modified",it)}
 }.also{apply(item,it)}
 fun apply(item:VaultItem,json:JSONObject):VaultItem {
  require(json.keys().asSequence().all{it in setOf("added","captured","adjusted","zone","modified")})
  fun time(name:String):Long? = if(!json.has(name))null else json.get(name).let{value->require(value is Long||value is Int);(value as Number).toLong().also{require(it in 1..253402300799000L)}}
  val adjusted=time("adjusted");val zone=if(json.has("zone"))json.get("zone").also{require(it is String&&it.length<=100)} as String else null
  require((adjusted==null)==(zone==null));zone?.let{java.time.ZoneId.of(it)}
  return item.copy(addedAtEpochMillis=time("added"),capturedAtEpochMillis=time("captured"),adjustedTakenAtEpochMillis=adjusted,adjustedTimeZone=zone,modifiedAtEpochMillis=time("modified"))
 }
}
