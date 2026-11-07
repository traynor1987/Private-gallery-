package uk.co.traynor.privategallery.core.backup

import android.content.Context
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.vault.*

internal class BackupCoordinator(private val context:Context,val access:EvidenceAccess,private val ownsAccess:Boolean=true):AutoCloseable {
 private val closed=AtomicBoolean()
 val space=SpaceContext(context,access)
 private val stageRoot=newStage(context)
 private val secrets=java.util.concurrent.ConcurrentHashMap.newKeySet<CharArray>()
 val owner get()=space.spaceId
 fun current()= !closed.get()&&access.current()
 private fun checkCurrent(){check(current()){ "Backup interrupted; unlock this Space again" }}
 private fun <T> credential(secret:CharArray,action:(CharArray)->T):T {
  secrets.add(secret)
  try{checkCurrent();return action(secret)}finally{secrets.remove(secret);secret.fill('\u0000')}
 }
 private fun <T> primary(action:(AndroidVaultRepository)->T):T=access.leases.acquire().use {lease->
  checkCurrent();val repo=AndroidVaultRepository(context,lease.key(),{commit->lease.commit{checkCurrent();commit()}},{current()&&lease.current()})
  action(repo).also{checkCurrent()}
 }
 private fun hidden()=HiddenRepository(context,checkNotNull(access.hiddenSession),publishIfAllowed={commit->checkCurrent();commit();checkCurrent()})
 private fun snapshot()=if(access.owner==EvidenceCatalog.Owner.PRIMARY)primary{it.backupSnapshot()}else hidden().backupSnapshot()
 private fun media(item:VaultItem,out:OutputStream) {
  val consume:(VaultItem,InputStream)->Unit={actual,input->
   require(actual.id==item.id&&actual.mimeType==item.mimeType&&actual.vaultOnly==item.vaultOnly)
   BackupMedia.verify(item,actual.plaintextSize,actual.plaintextSha256);copy(input,out,PortableArchive.MAX_ITEM)
  }
  if(access.owner==EvidenceCatalog.Owner.PRIMARY)primary{repo->repo.withBackupSource(item.id,{!current()},consume)}
  else hidden().withBackupSource(item.id,{!current()},consume)
 }
 data class Receipt(val bytes:Long,val sha256:String,val items:Int)
 class RestorePlan internal constructor(internal val archive:File,internal val secret:CharArray,val media:Int,val bytes:Long,val settings:Int,val names:List<String>,val created:Long,val apps:Int,val collections:Int):AutoCloseable {
  private val ended=AtomicBoolean()
  internal fun requireOpen(){check(!ended.get())}
  override fun close(){if(ended.compareAndSet(false,true)){secret.fill('\u0000');archive.delete()}}
 }
 private val plans=java.util.concurrent.ConcurrentHashMap.newKeySet<RestorePlan>()
 private val retirement=access.onRetire{close()}
 fun export(provider:BackupProvider,secret:CharArray):Receipt=credential(secret){key->
  checkCurrent();record("Unverified","Backup started",0)
  val archive=File(stageRoot,"backup-${UUID.randomUUID()}.enc")
  var values:Map<String,ByteArray> = emptyMap()
  val metadata=mutableListOf<ByteArray>()
  try {
   values=space.backupValues();space.validateBackupValues(values)
   val state=snapshot();require(state.items.all{it.state in setOf(VaultItemState.COMPLETE,VaultItemState.TRASHED)}){"Finish pending media operations before backup"};val items=state.items.filter{it.state in setOf(VaultItemState.COMPLETE,VaultItemState.TRASHED)};require(items.size<=10000)
   val manifest=JSONObject().put("schema",1).put("minReader",1).put("maxReader",1).put("owner",owner).put("minNovaVersion","1.7.11.6").put("maxNovaVersion","1.7.*").put("created",System.currentTimeMillis()).put("excluded",BackupPolicy.unsupported).toString().encodeToByteArray()
   val collections=BackupMedia.collections(state);metadata+=manifest;metadata+=collections;require(collections.size<=8*1024*1024){"Collection metadata exceeds portable archive limit"};BackupMedia.merge(VaultIndexSnapshot(emptyList()),emptyList(),items.associate{it.id to it.id},collections)
   fun entry(category:String,body:ByteArray,name:String?=null):PortableArchive.Entry {val json=JSONObject().put("category",category);if(name!=null)json.put("name",name);return PortableArchive.Entry(json.toString().encodeToByteArray().also{metadata+=it}){it.write(body)}}
   val entries=mutableListOf(entry("manifest",manifest))
   items.forEach{item->val descriptor=BackupMedia.metadata(item);BackupMedia.item(descriptor);entries+=PortableArchive.Entry(descriptor.toString().encodeToByteArray().also{metadata+=it}){out->media(item,out)}}
   entries+=entry("collections",collections)
   values.forEach{(name,bytes)->entries+=entry("settings",bytes,name)}
   val catalogue=BackupApps(context,access).snapshot();metadata+=catalogue;entries+=entry("apps",catalogue)
   FileOutputStream(archive).use{out->PortableArchive.write(out,key.copyOf(),entries,::current);out.fd.sync()}
   checkCurrent();val expected=hash(archive)
   provider.write().use{out->FileInputStream(archive).use{input->copy(input,out,PortableArchive.MAX_TOTAL+64L*1024*1024)}}
   // The actual provider bytes must authenticate AND match the complete local artifact.
   val digest=MessageDigest.getInstance("SHA-256")
   provider.read().use {raw->java.security.DigestInputStream(raw,digest).use{input->PortableArchive.read(input,key.copyOf(),::current){_,_->}}}
   require(MessageDigest.isEqual(expected,digest.digest())){"Destination differs from verified archive"};checkCurrent()
   val receipt=Receipt(archive.length(),expected.joinToString(""){"%02x".format(it)},items.size)
   record("Verified",provider.label,receipt.bytes);if(owner=="primary")BackupSchedule.completed(context);receipt
  }catch(failure:Throwable){if(current())runCatching{record("Failed","Destination not verified; keep your originals",0)};throw failure}
  finally{values.values.forEach{it.fill(0)};metadata.forEach{it.fill(0)};archive.delete()}
 }
 fun preview(provider:BackupProvider,secret:CharArray):RestorePlan=credential(secret){key->
  checkCurrent();val file=File(stageRoot,"restore-${UUID.randomUUID()}.enc")
  try {
   provider.read().use{input->FileOutputStream(file).use{out->copy(input,out,PortableArchive.MAX_TOTAL+64L*1024*1024);out.fd.sync()}}
   var manifestSeen=false;var collectionSeen=false;var count=0;var bytes=0L;var created=0L;var apps=0;var appsSeen=false;var collectionCount=0
   val names=mutableListOf<String>();val ids=hashSetOf<String>();val values=linkedMapOf<String,ByteArray>()
   try {
    FileInputStream(file).use{input->PortableArchive.read(input,key.copyOf(),::current){meta,payload->
     val json=JSONObject(meta.decodeToString());val category=json.getString("category")
     if(!manifestSeen){require(category=="manifest");val manifest=JSONObject(readBounded(payload,65536).decodeToString());require(manifest.getInt("schema")==1&&manifest.getInt("minReader")<=1&&manifest.getInt("maxReader")>=1);BackupPolicy.requireOwner(manifest.getString("owner"),owner);require(manifest.getString("minNovaVersion")=="1.7.11.6"&&manifest.getString("maxNovaVersion")=="1.7.*");created=manifest.getLong("created");manifestSeen=true}
     else when(category){
      "media"->{val item=BackupMedia.item(json);require(ids.add(item.id)&&++count<=10000);bytes=Math.addExact(bytes,item.plaintextSize);if(names.size<12)names+=item.displayName;val digest=MessageDigest.getInstance("SHA-256");var length=0L;val buffer=ByteArray(65536);try{while(true){val read=payload.read(buffer);if(read<0)break;length+=read;digest.update(buffer,0,read)}}finally{buffer.fill(0)};BackupMedia.verify(item,length,digest.digest())}
      "collections"->{require(!collectionSeen);collectionSeen=true;val data=readBounded(payload,8*1024*1024);try{BackupMedia.requireOwnerCollections(data,owner);collectionCount=BackupMedia.merge(VaultIndexSnapshot(emptyList()),emptyList(),ids.associateWith{it},data).collections.size}finally{data.fill(0)}}
      "apps"->{require(!appsSeen);appsSeen=true;val data=readBounded(payload,384*1024);try{apps=BackupApps(context,access).validate(data).apps.size}finally{data.fill(0)}}
      "settings"->{val name=json.getString("name");require(!values.containsKey(name)&&values.size<16);values[name]=readBounded(payload,8*1024*1024)}
      else->error("Unsupported archive category")
     }
    }}
    require(manifestSeen&&collectionSeen);space.validateBackupValues(values);checkCurrent()
    RestorePlan(file,key.copyOf(),count,bytes,values.size,names,created,apps,collectionCount).also{plan->plans.add(plan);try{checkCurrent()}catch(failure:Throwable){plans.remove(plan);plan.close();throw failure}}
   }finally{values.values.forEach{it.fill(0)}}
  }catch(failure:Throwable){file.delete();throw failure}
 }
 /** Media and settings are separate explicit restore actions, each with its own atomic commit. */
 fun restoreMedia(plan:RestorePlan,restoreFavourite:Boolean=false):Int {
  require(plans.contains(plan)){"Restore preview belongs to a different or retired operation"};plan.requireOpen();checkCurrent()
  record("Unverified","Restore in progress",plan.bytes,"Media restore")
  val producer=BackupMediaProducer {accept->
   var collections:ByteArray?=null
   try {FileInputStream(plan.archive).use{input->PortableArchive.read(input,plan.secret.copyOf(),::current){meta,payload->
    val json=JSONObject(meta.decodeToString());when(json.getString("category")){"media"->accept(BackupMedia.item(json),payload);"collections"->collections=readBounded(payload,8*1024*1024)}
   }};collections}catch(failure:Throwable){collections?.fill(0);throw failure}
  }
  try {
   val count=if(access.owner==EvidenceCatalog.Owner.PRIMARY)primary{it.restorePortable(producer,restoreFavourite)}else hidden().restorePortable(producer,restoreFavourite)
   checkCurrent();record("Verified","Restored $count media copies and collections",plan.bytes,"Media restore");return count
  }catch(failure:Throwable){if(current())runCatching{record("Failed","Existing media preserved; new copy publication may need review",0,"Media restore")};throw failure}
 }
 fun restoreSettings(plan:RestorePlan){
  require(plans.contains(plan)){"Restore preview belongs to a different or retired operation"};plan.requireOpen();checkCurrent();val values=linkedMapOf<String,ByteArray>()
  record("Unverified","Settings restore in progress",0,"Settings restore")
  var apps:ByteArray?=null
  try {
   FileInputStream(plan.archive).use{input->PortableArchive.read(input,plan.secret.copyOf(),::current){meta,payload->val json=JSONObject(meta.decodeToString());when(json.getString("category")){"settings"->values[json.getString("name")]=readBounded(payload,8*1024*1024);"apps"->apps=readBounded(payload,384*1024)}}}
   val binding=apps?.let{BackupApps(context,access).homeBinding(it)}
   space.restoreBackupValues(values,binding?.first?:emptyMap(),binding?.second?:org.json.JSONArray());checkCurrent();record("Verified","Restored supported settings; unlock to reload",0,"Settings restore")
  }catch(failure:Throwable){if(current())runCatching{record("Failed","Settings restore did not verify; reopen to inspect",0,"Settings restore")};throw failure}
  finally{apps?.fill(0);values.values.forEach{it.fill(0)}}
 }
 fun restoreApps(plan:RestorePlan):Int {
  require(plans.contains(plan)){"Restore preview belongs to a different or retired operation"};plan.requireOpen();checkCurrent()
  record("Unverified","Saved apps restore in progress",0,"App definitions restore")
  var bytes:ByteArray?=null
  try {
   FileInputStream(plan.archive).use{input->PortableArchive.read(input,plan.secret.copyOf(),::current){meta,payload->if(JSONObject(meta.decodeToString()).getString("category")=="apps")bytes=readBounded(payload,384*1024)}}
   val count=BackupApps(context,access).restore(checkNotNull(bytes));checkCurrent();record("Verified","Added $count saved app definitions",0,"App definitions restore");return count
  }catch(failure:Throwable){if(current())runCatching{record("Failed","Saved apps restore did not verify",0,"App definitions restore")};throw failure}
  finally{bytes?.fill(0)}
 }
 data class HistoryEntry(val status:String,val operation:String,val destination:String,val bytes:Long,val date:Long)
 data class HistorySnapshot(val lastBackup:HistoryEntry?,val events:List<HistoryEntry>)
 fun historySnapshot():HistorySnapshot {
  checkCurrent();val preferences=space.getSharedPreferences("nova_backup_history_v1",0)
  fun entry(event:JSONObject):HistoryEntry {
   val status=event.getString("status").let{if(it=="Unverified")"Interrupted / unverified"else it}
   return HistoryEntry(status,event.getString("operation"),event.getString("destination"),event.getLong("bytes"),event.getLong("date"))
  }
  val last=preferences.getString("lastBackup",null)?.let(::JSONObject)?.let(::entry)
  val events=org.json.JSONArray(preferences.getString("events","[]"))
  return HistorySnapshot(last,List(events.length()){entry(events.getJSONObject(it))})
 }
 fun history():String {
  val snapshot=historySnapshot()
  fun line(event:HistoryEntry):String {
   return "${event.status} · ${event.operation} · ${event.destination} · ${event.bytes} bytes · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.date))}"
  }
  return (snapshot.lastBackup?.let{"Last successful backup: ${line(it)}"}?:"No verified backup yet")+"\n"+snapshot.events.joinToString("\n",transform=::line)
 }
 private fun record(status:String,destination:String,bytes:Long,operation:String="Backup"){
  checkCurrent();val preferences=space.getSharedPreferences("nova_backup_history_v1",0)
  val event=JSONObject().put("status",status).put("operation",operation).put("destination",destination.take(160)).put("bytes",bytes).put("date",System.currentTimeMillis())
  val old=org.json.JSONArray(preferences.getString("events","[]"));val next=org.json.JSONArray().put(event)
  val start=if(status!="Unverified"&&old.length()>0&&old.getJSONObject(0).getString("status")=="Unverified"&&old.getJSONObject(0).getString("operation")==operation)1 else 0
  for(index in start until minOf(old.length(),start+19))next.put(old.getJSONObject(index))
  val editor=preferences.edit().putString("events",next.toString())
  if(operation=="Backup"&&status=="Verified")editor.putString("lastBackup",event.toString())
  check(editor.commit())
 }
 fun storageBytes():Long {checkCurrent();return snapshot().items.sumOf{it.plaintextSize}}
 private fun hash(file:File):ByteArray {val digest=MessageDigest.getInstance("SHA-256");FileInputStream(file).use{input->java.security.DigestInputStream(input,digest).use{copy(it,OutputStream.nullOutputStream(),PortableArchive.MAX_TOTAL+64L*1024*1024)}};return digest.digest()}
 private fun copy(input:InputStream,out:OutputStream,limit:Long):Long {val buffer=ByteArray(65536);var count=0L;try{while(true){checkCurrent();val read=input.read(buffer);if(read<0)break;require(read>0);count=Math.addExact(count,read.toLong());require(count<=limit);out.write(buffer,0,read)};checkCurrent();return count}finally{buffer.fill(0)}}
 private fun readBounded(input:InputStream,limit:Int):ByteArray {val out=object:ByteArrayOutputStream(){fun wipe(){buf.fill(0)}};return try{copy(input,out,limit.toLong());out.toByteArray()}finally{out.wipe()}}
 override fun close(){if(closed.compareAndSet(false,true)){secrets.toTypedArray().forEach{it.fill('\u0000')};secrets.clear();plans.toTypedArray().forEach{it.close()};plans.clear();space.close();if(ownsAccess)access.close();stageRoot.deleteRecursively()}}
 companion object {
  private val cleaned=hashSetOf<String>()
  private fun newStage(context:Context):File=synchronized(cleaned){
   val root=File(context.noBackupFilesDir,"nova-backup-ciphertext")
   if(cleaned.add(root.absolutePath))root.listFiles().orEmpty().forEach{it.deleteRecursively()}
   File(root,UUID.randomUUID().toString()).apply{check(mkdirs())}
  }
 }
}
