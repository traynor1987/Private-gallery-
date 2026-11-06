package uk.co.traynor.privategallery.core.backup

import android.app.job.JobScheduler
import android.content.Context
import android.net.NetworkCapabilities
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupScheduleRuntimeTest {
 @Test fun androidJobConstraintsContainNoSecretsAndLockedDueCheckCreatesNoArchive(){
  val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
  assertTrue(context.packageName.endsWith(".hiddenprototype"))
  androidx.test.core.app.ActivityScenario.launch(uk.co.traynor.privategallery.MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   activity.panicExit();val state=androidx.lifecycle.ViewModelProvider(activity)[uk.co.traynor.privategallery.ProtectedSessionState::class.java]
   assertFalse(state.session.isUnlocked);assertFalse(state.hidden.session.isUnlocked)
  }}
  instrumentation.waitForIdleSync()
  val prefs=context.getSharedPreferences("nova-backup-due",0);val before=prefs.all.toMap();val scheduler=context.getSystemService(JobScheduler::class.java)
  val previous=scheduler.getPendingJob(171162)
  fun snapshot()=listOf(context.filesDir,context.noBackupFilesDir).flatMap{root->root.walkTopDown().filter{it.isFile}.map{it.path to it.readBytes()}.toList()}.toMap()
  try{
   BackupSchedule.configure(context,1,true,true);var job=checkNotNull(scheduler.getPendingJob(171162))
   assertTrue(job.isPersisted);assertTrue(job.isRequireCharging);assertEquals(86400000L,job.intervalMillis);assertTrue(job.requiredNetwork!!.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));assertTrue(job.extras.isEmpty);assertTrue(job.transientExtras.isEmpty);assertNull(job.clipData)
   BackupSchedule.configure(context,7,false,false);job=checkNotNull(scheduler.getPendingJob(171162));assertFalse(job.isRequireCharging);assertNull(job.requiredNetwork);assertEquals(604800000L,job.intervalMillis)
   prefs.edit().putLong("configured",System.currentTimeMillis()-604800001L).commit()
   val originals=snapshot()
   val output=instrumentation.uiAutomation.executeShellCommand("cmd jobscheduler run -f ${context.packageName} 171162").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().decodeToString()}
   assertFalse(output,output.contains("Error"));val until=android.os.SystemClock.uptimeMillis()+10000
   while(prefs.getLong("due",0)==0L&&android.os.SystemClock.uptimeMillis()<until)Thread.sleep(20)
   assertTrue("Actual JobService persists due state",prefs.getLong("due",0)>0);assertTrue(BackupSchedule.status(context).contains("Needs unlock"))
   val after=snapshot();assertEquals(originals.keys,after.keys);originals.forEach{(path,bytes)->assertArrayEquals("Locked due check must not touch vault/history/archive",bytes,after[path])}
   BackupSchedule.configure(context,0,false,false);assertNull(scheduler.getPendingJob(171162))
  }finally{
   scheduler.cancel(171162);if(previous!=null)assertEquals(JobScheduler.RESULT_SUCCESS,scheduler.schedule(previous))
   prefs.edit().clear().also{edit->before.forEach{(key,value)->when(value){is Int->edit.putInt(key,value);is Long->edit.putLong(key,value);is Boolean->edit.putBoolean(key,value);is String->edit.putString(key,value)}}}.commit()
  }
 }
 @Test fun nativeEncryptedDirectorySyncFailureRestoresExactOriginalCiphertext(){
  val root=BackupFixture62.temporary("sync62")
  try{BackupFixture62(root).use{f->
   f.coordinator.space.getSharedPreferences("nova_widgets_v1",0).edit().putBoolean("clock:primary",false).commit()
   val target=File(f.access.root,"home2");val before=target.listFiles()!!.associate{it.name to it.readBytes()}
   val next=File(f.access.root,"home2-backup-next-failure");target.copyRecursively(next)
   var calls=0;val transaction=SettingsDirectoryTransaction(target){if(++calls>=2)throw java.io.IOException("Deterministic Android directory sync failure")}
   assertThrows(java.io.IOException::class.java){transaction.install(next,{true}){it()}}
   SettingsDirectoryTransaction(target).recover();assertEquals(before.keys,target.listFiles()!!.map{it.name}.toSet());before.forEach{(name,bytes)->assertArrayEquals(bytes,File(target,name).readBytes())}
  }}finally{root.deleteRecursively()}
 }
}
