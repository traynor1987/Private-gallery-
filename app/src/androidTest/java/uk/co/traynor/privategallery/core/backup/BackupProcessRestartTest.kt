package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/** Host-driven killed-process phases, deliberately excluded from ordinary connected-test XML. */
class BackupProcessRestartTest {
 private val instrumentation=InstrumentationRegistry.getInstrumentation()
 private val args=InstrumentationRegistry.getArguments()
 private val token=checkNotNull(args.getString("nova62Token")).also{require(it.matches(Regex("[a-f0-9]{32}")))}
 private val boundary=checkNotNull(args.getString("nova62Boundary")).also{require(it in listOf("staging","uncommitted","committed","verified"))}
 private val hidden=args.getString("nova62Owner")=="hidden"
 private val owner=if(hidden)"hidden"else "primary"
 private val base=instrumentation.targetContext
 private val root=File(base.noBackupFilesDir,"backup-process62/$token/$owner-$boundary")
 private val proof=File(root,"proof.json")
 private fun guard(){check(base.packageName=="uk.co.traynor.privategallery.hiddenprototype");check(android.os.Build.VERSION.SDK_INT==37)}
 private fun hash(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
 private fun files(dir:File)=JSONObject().also{out->dir.walkTopDown().filter{it.isFile}.sortedBy{it.path}.forEach{out.put(it.relativeTo(dir).path,hash(it))}}
 private fun same(expected:JSONObject,dir:File){val actual=files(dir);assertEquals(expected.keySet(),actual.keySet());expected.keys().forEach{assertEquals(it,expected.getString(it),actual.getString(it))}}
 private fun JSONObject.keySet()=keys().asSequence().toSet()
 private fun sync(dir:File){FileChannel.open(dir.toPath(),StandardOpenOption.READ).use{it.force(true)}}
 private fun hold(data:JSONObject):Nothing{
  proof.writeText(data.put("token",token).put("owner",owner).put("boundary",boundary).put("pid",Process.myPid()).toString())
  java.io.FileOutputStream(proof,true).use{it.fd.sync()};sync(root)
  instrumentation.sendStatus(0,Bundle().apply{putString("nova62_ready",JSONObject().put("token",token).put("owner",owner).put("boundary",boundary).put("pid",Process.myPid()).toString())})
  // The host must kill this target process. A deadline is a failure, never a passing seed test.
  SystemClock.sleep(45000);throw AssertionError("Host did not terminate prepared process")
 }
 @Test fun prepareBoundaryAndAwaitHostTermination(){
  guard();assertFalse("Unique disposable phase root",root.exists());root.mkdirs()
  val f=BackupFixture62(File(root,"target"),hidden);val item=f.insert("process original preserved")
  f.coordinator.space.getSharedPreferences("nova_widgets_v1",0).edit().putBoolean("clock:$owner",false).commit()
  val data=JSONObject().put("mediaId",item.id).put("metadata",hash(f.metadata())).put("old",files(File(f.access.root,"home2")))
  if(boundary=="staging"||boundary=="verified"){
   val document=BackupDocuments62.create();data.put("uri",document.toString())
   val provider=SafBackupProvider(f.context,document,f.access)
   val p=if(boundary=="staging")object:BackupProvider{override val label=provider.label;override fun read()=provider.read();override fun write():java.io.OutputStream{
    val stages=File(f.context.noBackupFilesDir,"nova-backup-ciphertext");assertTrue("Encrypted archive exists at provider handoff",stages.walkTopDown().any{it.isFile&&it.length()>0});data.put("stageRoot",stages.path);hold(data)
   }}else provider
   f.coordinator.export(p,"process restart recovery key".toCharArray());assertTrue(f.coordinator.history().contains("Last successful backup"));hold(data)
  }
  val nextFixture=BackupFixture62(File(root,"next-source"),hidden)
  nextFixture.coordinator.space.getSharedPreferences("nova_widgets_v1",0).edit().putBoolean("clock:$owner",true).commit()
  val target=File(f.access.root,"home2");val next=File(target.parentFile,"home2-backup-next-process");File(nextFixture.access.root,"home2").copyRecursively(next)
  data.put("new",files(next));nextFixture.close()
  SettingsDirectoryTransaction(target){dir->sync(dir)
   if(dir==target.parentFile&&File(dir,"home2-backup-old").exists()){
    val committed=File(dir,"home2-backup-commit").exists()
    if((boundary=="committed")==committed){assertTrue(target.isDirectory);hold(data)}
   }
  }.install(next,{true}){it()}
  error("Required transaction boundary was not observed")
 }
 @Test fun verifyRecoveryInNewProcess(){
  guard();val data=JSONObject(proof.readText());assertEquals(token,data.getString("token"));assertEquals(owner,data.getString("owner"));assertEquals(boundary,data.getString("boundary"));assertNotEquals(data.getInt("pid"),Process.myPid())
  BackupFixture62(File(root,"target"),hidden).use{f->
   assertEquals(data.getString("metadata"),hash(f.metadata()));assertArrayEquals("process original preserved".encodeToByteArray(),f.read(data.getString("mediaId")))
   if(boundary=="committed"||boundary=="uncommitted"){
    same(data.getJSONObject(if(boundary=="committed")"new"else "old"),File(f.access.root,"home2"))
    assertEquals(boundary=="committed",f.coordinator.space.getSharedPreferences("nova_widgets_v1",0).getBoolean("clock:$owner",false))
    assertFalse(File(f.access.root,"home2-backup-old").exists());assertFalse(File(f.access.root,"home2-backup-commit").exists())
   }else{
    val verified=boundary=="verified";assertEquals(verified,f.coordinator.history().contains("Last successful backup"))
    if(!verified){
     val history=f.coordinator.space.getSharedPreferences("nova_backup_history_v1",0)
     assertNull(history.getString("lastBackup",null));assertEquals("Unverified",org.json.JSONArray(history.getString("events","[]")).getJSONObject(0).getString("status"))
     assertTrue(f.coordinator.history().contains("Interrupted / unverified"));assertFalse(File(data.getString("stageRoot")).walkTopDown().any{it.isFile})
    }
    if(verified)f.coordinator.preview(SafBackupProvider(f.context,android.net.Uri.parse(data.getString("uri")),f.access),"process restart recovery key".toCharArray()).use{assertEquals(1,it.media)}
    BackupDocuments62.delete(android.net.Uri.parse(data.getString("uri")))
   }
   root.walkTopDown().filter{it.isFile&&it!=proof}.forEach{file->assertFalse("No media plaintext in ${file.name}",file.readBytes().toString(Charsets.ISO_8859_1).contains("process original preserved"))}
  }
  instrumentation.sendStatus(0,Bundle().apply{putString("nova62_verified",JSONObject().put("token",token).put("owner",owner).put("boundary",boundary).put("oldPid",data.getInt("pid")).put("pid",Process.myPid()).toString())})
  root.deleteRecursively()
 }
}
