package uk.co.traynor.privategallery.core.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/** Scheduled due checks have no vault capability, key, URI or recovery material. */
internal object BackupSchedule {
 private const val ID=171162
 fun configure(context:Context,days:Int,charging:Boolean,wifi:Boolean){
  require(days in setOf(0,1,7));val scheduler=context.getSystemService(JobScheduler::class.java)
  if(days==0)scheduler.cancel(ID)else {
   val builder=JobInfo.Builder(ID,ComponentName(context,BackupDueJob::class.java)).setPeriodic(days*24L*60*60*1000).setRequiresCharging(charging).setPersisted(true)
   if(wifi)builder.setRequiredNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build())
   check(scheduler.schedule(builder.build())==JobScheduler.RESULT_SUCCESS){"Android could not schedule the due check"}
  }
  check(context.getSharedPreferences("nova-backup-due",0).edit().putInt("days",days).putBoolean("charging",charging).putBoolean("wifi",wifi).putLong("configured",System.currentTimeMillis()).putLong("due",0).putLong("attempt",0).commit())
 }
 fun completed(context:Context){val prefs=context.getSharedPreferences("nova-backup-due",0);if(prefs.getInt("days",0)>0)prefs.edit().putLong("due",0).putLong("configured",System.currentTimeMillis()).apply()}
 fun status(context:Context):String {
  val prefs=context.getSharedPreferences("nova-backup-due",0);val days=prefs.getInt("days",0)
  if(days==0)return "Scheduling off"
  val due=prefs.getLong("due",0)
  val whenDue=if(due>0)"Due since "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(due))else "Next due about "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(prefs.getLong("configured",System.currentTimeMillis())+days*24L*60*60*1000))
  val constraints=listOfNotNull(if(prefs.getBoolean("charging",true))"charging"else null,if(prefs.getBoolean("wifi",true))"Wi-Fi"else null).joinToString(" + ").ifEmpty{"no charging/network restriction"}
  return "$whenDue · $constraints. ${if(days==1)"Daily"else "Weekly"} due check · ${BackupPolicy.scheduledStatus()}. Armed automatic backups run in an eligible foreground Primary session. Locked sessions wait for unlock."
 }
}
class BackupDueJob:JobService(){
 override fun onStartJob(params:JobParameters?):Boolean {
  val prefs=getSharedPreferences("nova-backup-due",0);val days=prefs.getInt("days",0);val now=System.currentTimeMillis()
  if(days in setOf(1,7)&&now>=prefs.getLong("configured",now)+days*86400000L)prefs.edit().putLong("due",now).apply()
  return false
 }
 override fun onStopJob(params:JobParameters?)=false
}
