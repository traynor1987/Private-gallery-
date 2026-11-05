package uk.co.traynor.novaos.preview
import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONObject

class HomeBackup(context:Context){
 private val recovery=context.getSharedPreferences("nova_home_restore_recovery",0)
 var pending by mutableStateOf<JSONObject?>(null);private set
 var pendingProfile:String?=null;private set
 var error by mutableStateOf<String?>(null);private set
 var message by mutableStateOf<String?>(null);private set
 fun export(data:PreviewData,widgets:WidgetData):String {
  return JSONObject().put("format","nova-home-layout").put("version",1).put("layout",data.layoutExport()).put("widgets",WidgetData.encode(widgets.slots(data.state.current),true)).put("theme",data.state.theme).put("reducedMotion",widgets.reduced).put("demoLock",widgets.demoLock).toString(2).also{HomeBackupCodec.validate(it)}
 }
 fun prepare(raw:String,profile:String){runCatching{val valid=HomeBackupCodec.validate(raw);WidgetData.decode(valid.getJSONArray("widgets"));pending=valid;pendingProfile=profile;error=null}.onFailure{pending=null;error="Backup rejected: ${it.message?.take(160)}"}}
 fun cancel(){pending=null;pendingProfile=null}
 fun recover(data:PreviewData,widgets:WidgetData){
  val raw=recovery.getString("pending",null)?:return
  runCatching{val s=JSONObject(raw);val a=data.rollbackLayout(s.getString("home"));val b=widgets.restoreRaw(s.getString("profile"),s.getString("widgets"));val c=widgets.reduced(s.optBoolean("reduced",false));val d=widgets.demoLock(s.optBoolean("demoLock",false));check(a&&b&&c&&d);check(recovery.edit().remove("pending").commit());message="Recovered the Home layout after an interrupted restore."}.onFailure{error="An interrupted restore needs recovery. Snapshots are preserved; no new restore was applied."}
 }
 fun restore(data:PreviewData,widgets:WidgetData):Boolean {
  val candidate=pending?:return false;val profile=pendingProfile?:return false
  if(profile!=data.state.current){error="Preview profile changed. Select the backup again for this profile.";return false}
  val home=data.snapshotLayout();val layout=widgets.snapshot(profile);val oldReduced=widgets.reduced;val oldLock=widgets.demoLock
  return runCatching{
   check(!recovery.contains("pending")){"A previous restore needs recovery first"}
   check(recovery.edit().putString("pending",JSONObject().put("home",home).put("widgets",layout).put("profile",profile).put("reduced",oldReduced).put("demoLock",oldLock).toString()).commit())
   val success=LayoutTransaction.apply({val ok=data.restoreLayout(candidate.getJSONObject("layout"));if(ok)data.setTheme(candidate.getString("theme"));ok&&data.error==null},{val a=widgets.save(profile,WidgetData.decode(candidate.getJSONArray("widgets")));val b=widgets.reduced(candidate.getBoolean("reducedMotion"));val c=widgets.demoLock(candidate.getBoolean("demoLock"));a&&b&&c},{data.rollbackLayout(home)},{val a=widgets.restoreRaw(profile,layout);val b=widgets.reduced(oldReduced);val c=widgets.demoLock(oldLock);a&&b&&c})
   check(recovery.edit().remove("pending").commit())
   if(success){message="Home layout restored. Installed apps and private data were preserved.";cancel();error=null}else error="Restore failed; the previous Home layout was restored."
   success
  }.getOrElse{error=it.message?:"Restore failed. Recovery snapshots are preserved.";false}
 }
}
