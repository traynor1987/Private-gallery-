package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Network
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** Schedule credentials live only in current-Primary encrypted storage, excluded from portable data. */
internal object AutomaticBackupSettings {
 const val NAME="nova_backup_automatic_v1"
 class Config(val tree:Uri,val recovery:CharArray,val days:Int,val charging:Boolean,val wifi:Boolean):AutoCloseable {
  override fun close(){recovery.fill('\u0000')}
 }
 fun read(space:SpaceContext):Config? {
  check(space.spaceId=="primary"&&space.current())
  val values=space.getSharedPreferences(NAME,0).all
  if(values.isEmpty()||values["armed"]==false)return null
  require(values.keys==setOf("schema","armed","tree","recovery","days","charging","wifi"))
  require(values["schema"]==1&&values["armed"]==true&&values["days"] in setOf(1,7)&&values["charging"] is Boolean&&values["wifi"] is Boolean)
  val tree=Uri.parse(values["tree"] as String);require(tree.scheme=="content"&&DocumentsContract.isTreeUri(tree))
  val key=(values["recovery"] as String).toCharArray()
  try{require(key.size in 32..1024);check(space.current());return Config(tree,key,values["days"] as Int,values["charging"] as Boolean,values["wifi"] as Boolean)}catch(error:Throwable){key.fill('\u0000');throw error}
 }
 fun armed(space:SpaceContext):Boolean=read(space)?.use{true}?:false
 fun arm(context:Context,space:SpaceContext,tree:Uri,recovery:CharArray,days:Int,charging:Boolean,wifi:Boolean){
  try {
   check(space.spaceId=="primary"&&space.current());require(days==1||days==7);require(recovery.size in 32..1024&&tree.scheme=="content"&&DocumentsContract.isTreeUri(tree))
   context.contentResolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
   val prefs=space.getSharedPreferences(NAME,0)
   check(prefs.edit().clear().putInt("schema",1).putBoolean("armed",false).putString("tree",tree.toString()).putString("recovery",recovery.concatToString()).putInt("days",days).putBoolean("charging",charging).putBoolean("wifi",wifi).commit())
   try {
    BackupSchedule.configure(context,days,charging,wifi)
    check(prefs.edit().putBoolean("armed",true).commit());read(space)?.use{check(it.tree==tree&&it.recovery.contentEquals(recovery))}?:error("Schedule readback failed")
   }catch(failure:Throwable){runCatching{prefs.edit().clear().commit()};runCatching{BackupSchedule.configure(context,0,charging,wifi)};throw failure}
  }finally{recovery.fill('\u0000')}
 }
 fun disable(context:Context,space:SpaceContext){check(space.spaceId=="primary"&&space.current());check(space.getSharedPreferences(NAME,0).edit().clear().commit());BackupSchedule.configure(context,0,false,false)}
}

/** Only a foreground Home owns this controller. Pause/retirement closes active provider IO. */
internal class AutomaticBackupController(private val context:Context,private val origin:EvidenceAccess):AutoCloseable {
 private val foreground=AtomicBoolean(false)
 private val active=AtomicReference<EvidenceAccess?>(null)
 private val retirement=origin.onRetire{pause()}
 fun resume(){if(origin.owner==EvidenceCatalog.Owner.PRIMARY&&origin.current())foreground.set(true)}
 fun pause(){foreground.set(false);active.getAndSet(null)?.close()}
 fun runIfEligible():Boolean {
  if(origin.owner!=EvidenceCatalog.Owner.PRIMARY||!foreground.get()||!origin.current()||!running.compareAndSet(false,true))return false
  var access:EvidenceAccess?=null
  try {
   val prefs=context.getSharedPreferences("nova-backup-due",0);val now=System.currentTimeMillis();val days=prefs.getInt("days",0)
   if(days !in setOf(1,7))return false
   val due=prefs.getLong("due",0).takeIf{it>0}?: (prefs.getLong("configured",now)+days*86400000L).takeIf{it<=now}?:return false
   val child=EvidenceAccess.primary(context,origin,{if(foreground.get()&&origin.current())origin else null},{foreground.get()&&origin.current()},{origin.leases.acquire().use{it.key().copyOf()}},Any());access=child;active.set(child)
   // Publication and pause can race: recheck after registering the child so pause cannot miss it.
   if(!foreground.get()||!origin.current()){child.close();return false}
   BackupCoordinator(context,child).use{coordinator->
    val config=AutomaticBackupSettings.read(coordinator.space)?:return false
    val wipe=child.onRetire{config.close()}
    try {config.use{
     require(it.days==days)
     val battery=context.getSystemService(BatteryManager::class.java)
     val connectivity=context.getSystemService(ConnectivityManager::class.java)
     val wifi=connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let{it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}==true
     if(!AutomaticBackupPolicy.eligible(true,foreground.get(),child.current(),due,now,prefs.getLong("attempt",0),it.charging,battery.isCharging,it.wifi,wifi))return false
     child.checkCurrent();check(prefs.edit().putLong("attempt",now).commit())
     val receiver=if(it.charging)object:BroadcastReceiver(){override fun onReceive(c:Context?,intent:Intent?){if(!battery.isCharging)child.close()}}else null
     val network=if(it.wifi)object:ConnectivityManager.NetworkCallback(){
      override fun onLost(network:Network){child.close()}
      override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){if(!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))child.close()}
     }else null
     var receiverRegistered=false;var networkRegistered=false
     try {
      if(receiver!=null){context.registerReceiver(receiver,IntentFilter(Intent.ACTION_POWER_DISCONNECTED),Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true}
      if(network!=null){connectivity.registerDefaultNetworkCallback(network);networkRegistered=true}
      val currentWifi=connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.let{caps->caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}==true
      if((it.charging&&!battery.isCharging)||(it.wifi&&!currentWifi)){child.close();return false}
      coordinator.export(TreeDestination(context,it.tree,child),it.recovery.copyOf())
      return true
     }finally{if(receiverRegistered)runCatching{context.unregisterReceiver(receiver)};if(networkRegistered)runCatching{connectivity.unregisterNetworkCallback(network!!)}}
    }}finally{wipe.close()}
   }
  }catch(_:Exception){return false}finally{access?.close();active.compareAndSet(access,null);running.set(false)}
 }
 override fun close(){pause();retirement.close()}
 private class TreeDestination(private val context:Context,private val tree:Uri,private val access:EvidenceAccess):BackupProvider {
  private var destination:SafBackupProvider?=null
  override val label get()=destination?.label?:"Automatic local document"
  override fun write():OutputStream {
   check(destination==null);access.checkCurrent()
   val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
   val date=java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(java.util.Date())
   val uri=checkNotNull(DocumentsContract.createDocument(context.contentResolver,parent,"application/octet-stream","NovaOS-$date-${UUID.randomUUID()}.novabak"))
   access.checkCurrent();return SafBackupProvider(context,uri,access).also{destination=it}.write()
  }
  override fun read():InputStream=checkNotNull(destination).read()
 }
 companion object {private val running=AtomicBoolean(false)}
}
