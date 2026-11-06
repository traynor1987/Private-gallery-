package uk.co.traynor.privategallery.core.backup
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
class PortablePreferenceSchemaTest {
 private fun check(name:String,values:Map<String,Any>)=PortablePreferenceSchema.validate(name,values,"primary")
 private fun layout()=JSONObject().put("schema",1).put("current","primary").put("theme","dark").put("profile",JSONObject().put("installed",JSONArray()).put("pages",JSONArray().put(JSONArray())).put("dock",JSONArray()).put("folders",JSONArray()).put("wallpaper",JSONObject().put("builtin","Aurora")))
 @Test fun scalarTypesAndBoundsAreRequired(){
  for((name,values) in listOf("nova_widgets_v1" to mapOf("reduced" to "true"),"nova_preview_v1" to mapOf("native-theme-bridged" to 1),"nova_shell_v1" to mapOf("edge" to "true"),"nova_shell_v1" to mapOf("displayDim" to Float.NaN),"nova_shell_v1" to mapOf("edgePosition" to 2f)))assertThrows(Exception::class.java){check(name,values)}
 }
 @Test fun completeHomeSchemaMustBeUsable(){
  check("nova_preview_v1",mapOf("state" to layout().toString()))
  for(field in listOf("pages","dock","wallpaper")){
   val state=layout();val profile=state.getJSONObject("profile")
   if(field=="pages")profile.put(field,JSONArray())else profile.put(field,"wrong type")
   assertThrows(Exception::class.java){check("nova_preview_v1",mapOf("state" to state.toString()))}
  }
 }
 @Test fun wallpaperAndMindRecordsAreStrict(){
  assertThrows(Exception::class.java){check("nova_preview_v1",mapOf("wallpaper-applied-v1" to "{\"zoom\":\"1\"}"))}
  assertThrows(Exception::class.java){check("nova_mind_v1",mapOf("primary" to "not a journal array"))}
  assertThrows(Exception::class.java){check("nova_mind_v1",mapOf("preferred" to "unknown"))}
  check("nova_mind_v1",mapOf("primary" to "[]","preferred" to "breathing"))
 }
 @Test fun futureAppValuesRequireTheirActualCodec(){
  for(name in listOf("nova_arcade_v1","nova_calculator_v1"))assertThrows(Exception::class.java){check(name,mapOf("anything" to "unvalidated"))}
 }
}
