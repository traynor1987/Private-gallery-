package uk.co.traynor.privategallery.core.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.vault.*

/** API37 synthetic fixtures only; never touches the owner's vault or credentials. */
class BackupRuntimeTest {
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private fun scoped(root:File)=object:ContextWrapper(base){
  override fun getApplicationContext():Context=this
  override fun getFilesDir()=File(root,"files").apply{mkdirs()}
  override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
 }
 private class LocalFile(private val file:File):BackupProvider {
  override val label="Test destination"
  override fun read()=FileInputStream(file)
  override fun write()=FileOutputStream(file)
 }
 private class Fixture(val context:Context,val isHidden:Boolean,val key:ByteArray):AutoCloseable {
  val live=AtomicBoolean(true)
  val session=HiddenSession{100L}.also{check(it.complete(it.beginAuthentication(),key.copyOf()))}
  val access=if(isHidden)EvidenceAccess.hidden(context,session){live.get()}else EvidenceAccess.primary(context,key,{key},{live.get()},{key.copyOf()},Any())
  val backup=BackupCoordinator(context,access)
  val primary=AndroidVaultRepository(context,key)
  val hidden=HiddenRepository(context,session)
  fun insert(bytes:ByteArray,name:String="same-name.txt"):VaultItem=if(isHidden)hidden.importMedia(name,"text/plain",ByteArrayInputStream(bytes),true)else (primary.importVerified(VaultImportSource(name,"text/plain",{ByteArrayInputStream(bytes)},createDistinctCopy=true))as ImportResult.Imported).item
  fun items()=if(isHidden)hidden.items()else primary.items()
  fun read(id:String)=if(isHidden)hidden.withTransferSource(id,{false}){_,stream->stream.readBytes()}else primary.withHiddenCopySource(id,{false}){_,stream->stream.readBytes()}
  fun addCollection(id:String){if(isHidden){val c=hidden.createCollection("Backed up collection");hidden.addItemsToCollection(c.id,listOf(id))}else{val c=primary.createCollection("Backed up collection");primary.addItemsToCollection(c.id,listOf(id))}}
  fun metadataFile()=if(isHidden)File(context.noBackupFilesDir,"hidden-media-v1/metadata-00000000-0000-0000-0000-000000000000.enc")else File(context.filesDir,"vault/vault-index.enc")
  override fun close(){backup.close();session.lock();key.fill(0)}
 }
 @Test fun bothSpacesReencryptMediaCollectionsNotesAndPreferencesOnReplacementDevice(){
  for(hidden in listOf(false,true)){
   val root=File(base.cacheDir,"backup-roundtrip-${UUID.randomUUID()}").apply{mkdirs()}
   Fixture(scoped(File(root,"source")),hidden,ByteArray(32){11}).use{source->Fixture(scoped(File(root,"replacement")),hidden,ByteArray(32){83}).use{destination->
    try{
     val first="portable first original".encodeToByteArray();val second="portable second original".encodeToByteArray()
     val a=source.insert(first);val b=source.insert(second);source.addCollection(a.id);if(hidden)source.hidden.trash(listOf(b.id))else source.primary.moveToRecentlyDeleted(b.id)
     val owner=source.backup.owner
     val journal=org.json.JSONArray().put(org.json.JSONObject().put("id",UUID.randomUUID().toString()).put("time",1000).put("text","portable journal").put("mood","").put("journal",true)).toString()
     source.backup.space.getSharedPreferences("nova_mind_v1",0).edit().putString(owner,journal).commit()
     source.backup.space.getSharedPreferences("nova_widgets_v1",0).edit().putString("layout:$owner","[{\"id\":\"slot\",\"page\":0,\"x\":0,\"y\":0,\"w\":2,\"h\":2,\"active\":0,\"items\":[{\"id\":\"note\",\"type\":\"note\",\"config\":\"portable note\"}]}]").commit()
     destination.backup.space.getSharedPreferences("credentials",0).edit().putString("token","keep-existing").commit()
     destination.backup.space.getSharedPreferences("nova_mind_v1",0).edit().putString(owner,"[]").commit()
     val existing=destination.insert("existing destination media".encodeToByteArray(),"existing.txt")
     val provider=LocalFile(File(root,"portable.enc"));val key="long random replacement recovery key".toCharArray()
     source.backup.export(provider,key.copyOf())
     assertFalse(File(root,"portable.enc").readBytes().toString(Charsets.ISO_8859_1).contains("portable note"))
     assertThrows(Exception::class.java){destination.backup.preview(provider,"wrong recovery key".toCharArray())}
     val plan=destination.backup.preview(provider,key.copyOf())
     assertEquals(2,plan.media);assertEquals(2,destination.backup.restoreMedia(plan))
     assertEquals(3,destination.items().size);assertEquals(2,destination.items().count{it.displayName=="same-name.txt"})
     assertArrayEquals("existing destination media".encodeToByteArray(),destination.read(existing.id))
     val copies=destination.items().filter{it.id!=existing.id};assertTrue(copies.none{it.id==a.id})
     assertTrue(copies.any{destination.read(it.id).contentEquals(first)});assertTrue(copies.any{destination.read(it.id).contentEquals(second)})
     assertEquals(1,if(hidden)destination.hidden.collections().size else destination.primary.collections().size)
     destination.backup.restoreSettings(plan)
     assertEquals(journal,destination.backup.space.getSharedPreferences("nova_mind_v1",0).getString(owner,null))
     assertTrue(destination.backup.space.getSharedPreferences("nova_widgets_v1",0).getString("layout:$owner","")!!.contains("portable note"))
     assertEquals("keep-existing",destination.backup.space.getSharedPreferences("credentials",0).getString("token",null))
     assertTrue(source.backup.history().contains("Last successful backup"));assertTrue(destination.backup.history().contains("No verified backup yet"))
     plan.close();assertThrows(Exception::class.java){destination.backup.restoreMedia(plan)}
    }finally{root.deleteRecursively()}
   }}
  }
 }
 @Test fun bothSpacesRestoreDatesPinsAndConfirmedFavouriteThenAllowLocalOverrides(){
  for(hidden in listOf(false,true)) {
   val root=File(base.cacheDir,"backup-presentation-${UUID.randomUUID()}").apply{mkdirs()}
   try {Fixture(scoped(File(root,"source")),hidden,ByteArray(32){21}).use{source->Fixture(scoped(File(root,"target")),hidden,ByteArray(32){62}).use{destination->
    val item=source.insert("presentation original".encodeToByteArray());source.addCollection(item.id)
    val c=if(hidden)source.hidden.collections().single()else source.primary.collections().single()
    if(hidden){source.hidden.adjustMediaDate(item.id,3000,"Europe/London");source.hidden.pinCollection(c.id,true);source.hidden.setFavouriteCollection(c.id)}
    else{source.primary.adjustMediaDate(item.id,3000,"Europe/London");source.primary.pinCollection(c.id,true);source.primary.setFavouriteCollection(c.id)}
    val provider=LocalFile(File(root,"archive"));val key="presentation recovery key".toCharArray();source.backup.export(provider,key.copyOf())
    val plan=destination.backup.preview(provider,key.copyOf());destination.backup.restoreMedia(plan,true)
    val copy=destination.items().single();assertEquals(3000L,copy.adjustedTakenAtEpochMillis);assertEquals("Europe/London",copy.adjustedTimeZone)
    val restored=if(hidden)destination.hidden.collections().single()else destination.primary.collections().single();assertTrue(restored.isPinned);assertNotEquals(c.id,restored.id)
    assertEquals(restored.id,if(hidden)destination.hidden.favouriteCollection()?.id else destination.primary.favouriteCollection()?.id)
    if(hidden){destination.hidden.adjustMediaDate(copy.id,null,null);destination.hidden.pinCollection(restored.id,false)}else{destination.primary.adjustMediaDate(copy.id,null,null);destination.primary.pinCollection(restored.id,false)}
    assertNull(destination.items().single().adjustedTakenAtEpochMillis);assertFalse(if(hidden)destination.hidden.collections().single().isPinned else destination.primary.collections().single().isPinned)
    // Reopening real encrypted repositories preserves the import and the newer override.
    assertNull(if(hidden)HiddenRepository(destination.context,destination.session).items().single().adjustedTakenAtEpochMillis else AndroidVaultRepository(destination.context,destination.key).items().single().adjustedTakenAtEpochMillis)
   }}}finally{root.deleteRecursively()}
  }
 }
 @Test fun foreignAndRetiredPlansCannotCrossAuthorities(){
  val root=File(base.cacheDir,"backup-authority-${UUID.randomUUID()}").apply{mkdirs()}
  try{Fixture(scoped(File(root,"source")),false,ByteArray(32){3}).use{source->Fixture(scoped(File(root,"other")),true,ByteArray(32){8}).use{other->
   source.insert("source original".encodeToByteArray());val provider=LocalFile(File(root,"archive"));val key="authority-bound recovery key".toCharArray();source.backup.export(provider,key.copyOf());val plan=source.backup.preview(provider,key.copyOf())
   assertThrows(Exception::class.java){other.backup.preview(provider,key.copyOf())}
   assertThrows(Exception::class.java){other.backup.restoreMedia(plan)};assertThrows(Exception::class.java){other.backup.restoreSettings(plan)};assertTrue(other.items().isEmpty())
   source.live.set(false);assertThrows(Exception::class.java){source.backup.restoreMedia(plan)}
  }}}finally{root.deleteRecursively()}
 }
 @Test fun interruptedAndOutOfStorageBatchKeepsExistingIndexInBothSpaces(){
  for(hidden in listOf(false,true)){
   val root=File(base.cacheDir,"backup-rollback-${UUID.randomUUID()}").apply{mkdirs()}
   try{Fixture(scoped(root),hidden,ByteArray(32){19}).use{destination->
    val original=destination.insert("preserved original".encodeToByteArray());val before=destination.metadataFile().readBytes()
    for(failure in listOf("interrupted","full")){
     val bytes="new staged media".encodeToByteArray();val item=original.copy(id=UUID.randomUUID().toString(),plaintextSize=bytes.size.toLong(),plaintextSha256=java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
     val producer=BackupMediaProducer{accept->accept(item,ByteArrayInputStream(bytes));throw IOException(if(failure=="full")"No space left on device"else "Interrupted before commit")}
     assertThrows(Exception::class.java){if(hidden)destination.hidden.restorePortable(producer)else destination.primary.restorePortable(producer)}
     assertArrayEquals(before,destination.metadataFile().readBytes());assertEquals(listOf(original.id),destination.items().map{it.id});assertArrayEquals("preserved original".encodeToByteArray(),destination.read(original.id))
    }
   }}finally{root.deleteRecursively()}
  }
 }
 @Test fun providerTamperingNeverRecordsVerifiedDestination(){
  val root=File(base.cacheDir,"backup-provider-${UUID.randomUUID()}").apply{mkdirs()}
  try{Fixture(scoped(root),false,ByteArray(32){6}).use{source->
   source.insert("original".encodeToByteArray());val file=File(root,"tampered")
   val provider=object:BackupProvider{override val label="Untrusted provider";override fun write()=FileOutputStream(file);override fun read():InputStream{val bytes=file.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();return ByteArrayInputStream(bytes)}}
   assertThrows(Exception::class.java){source.backup.export(provider,"provider test recovery key".toCharArray())}
   assertFalse(source.backup.history().contains("Last successful backup"));assertTrue(source.backup.history().contains("Failed"));assertEquals(1,source.items().size)
  }}finally{root.deleteRecursively()}
 }
 @Test fun largeMultiChunkMediaRoundtripUsesBoundedStreamsInBothSpaces(){
  val size=17L*1024*1024+31
  fun generated()=object:InputStream(){var position=0L
   override fun read():Int=if(position==size)-1 else ((position++%251).toInt())
   override fun read(buffer:ByteArray,offset:Int,length:Int):Int {if(length==0)return 0;if(position==size)return -1;val count=minOf(length.toLong(),size-position).toInt();repeat(count){buffer[offset+it]=((position+it)%251).toByte()};position+=count;return count}
  }
  fun hash(input:InputStream):ByteArray {val digest=java.security.MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536);var total=0L;input.use{while(true){val count=it.read(buffer);if(count<0)break;digest.update(buffer,0,count);total+=count}};assertEquals(size,total);buffer.fill(0);return digest.digest()}
  val expected=hash(generated())
  for(hidden in listOf(false,true)){
   val root=File(base.cacheDir,"backup-large-${UUID.randomUUID()}").apply{mkdirs()}
   try{Fixture(scoped(File(root,"source")),hidden,ByteArray(32){43}).use{source->Fixture(scoped(File(root,"replacement")),hidden,ByteArray(32){72}).use{destination->
    val descriptor=VaultItem(UUID.randomUUID().toString(),"video/mp4","large-synthetic-payload.mp4",1,size,expected,ByteArray(12),VaultItemState.COMPLETE)
    val producer=BackupMediaProducer{accept->generated().use{accept(descriptor,it)};null}
    if(hidden)source.hidden.restorePortable(producer)else source.primary.restorePortable(producer)
    val provider=LocalFile(File(root,"archive"));val key="large replacement-device recovery key".toCharArray()
    source.backup.export(provider,key.copyOf());val plan=destination.backup.preview(provider,key.copyOf());assertEquals(1,destination.backup.restoreMedia(plan));val restored=destination.items().single()
    val actual=if(hidden)destination.hidden.withTransferSource(restored.id,{false}){_,input->hash(input)}else destination.primary.withHiddenCopySource(restored.id,{false}){_,input->hash(input)}
    assertArrayEquals(expected,actual);assertEquals(size,restored.plaintextSize)
   }}}finally{root.deleteRecursively()}
  }
 }
 @Test fun savedAppDefinitionsStripCredentialQueriesPreserveExistingIdsAndMapHomeShortcuts(){
  for(hidden in listOf(false,true)){
   val root=File(base.cacheDir,"backup-apps-${UUID.randomUUID()}").apply{mkdirs()}
   try{Fixture(scoped(File(root,"source")),hidden,ByteArray(32){13}).use{source->Fixture(scoped(File(root,"replacement")),hidden,ByteArray(32){27}).use{destination->
    fun store(f:Fixture,action:(uk.co.traynor.privategallery.core.privateapps.PrivateAppStore)->Unit){f.access.leases.acquire().use{lease->
     val store=if(hidden)uk.co.traynor.privategallery.core.privateapps.HiddenPrivateAppStore(f.context.noBackupFilesDir,f.session,f.session.authority(),false,{f.access.current()},{commit->lease.commit{commit.run()}})
     else uk.co.traynor.privategallery.core.privateapps.PrimaryPrivateAppStore(File(f.context.filesDir,"private-apps"),lease.key(),{f.access.current()},{commit->lease.commit{commit()}})
     action(store)
    }}
    val sourceId=UUID.randomUUID().toString();val existingId=UUID.randomUUID().toString()
    store(source){it.update{catalog->catalog.upsert(uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App(sourceId,"Portable app","https://example.org/app?token=DO_NOT_EXPORT#private","","browser","",false,true,true))}}
    store(destination){it.update{catalog->catalog.upsert(uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog.App(existingId,"Existing app","https://example.net/","","browser","",true,true,true))}}
    val data=BackupApps(source.context,source.access).snapshot();assertFalse(data.toString(Charsets.ISO_8859_1).contains("DO_NOT_EXPORT"))
    val target=BackupApps(destination.context,destination.access);assertEquals(1,target.restore(data));assertEquals(0,target.restore(data))
    store(destination){assertTrue(it.read().apps.any{app->app.id()==existingId&&app.requireAuthentication()});assertEquals(2,it.read().apps.size)}
    val binding=target.homeBinding(data);val mapped=checkNotNull(binding.first["web:$sourceId"]);assertNotEquals("web:$sourceId",mapped);assertTrue(binding.second.toString().contains(mapped))
    val profile=org.json.JSONObject().put("installed",org.json.JSONArray()).put("pages",org.json.JSONArray().put(org.json.JSONArray().put("web:$sourceId"))).put("dock",org.json.JSONArray().put("web:$sourceId")).put("folders",org.json.JSONArray()).put("wallpaper",org.json.JSONObject().put("builtin","Aurora"))
    val state=org.json.JSONObject().put("schema",1).put("current",source.backup.owner).put("theme","dark").put(source.backup.owner,profile)
    source.backup.space.getSharedPreferences("nova_preview_v1",0).edit().putString("state",state.toString()).commit()
    val values=source.backup.space.backupValues();try{destination.backup.space.restoreBackupValues(values,binding.first,binding.second)}finally{values.values.forEach{it.fill(0)}}
    val restored=destination.backup.space.getSharedPreferences("nova_preview_v1",0).getString("state","")!!
    assertTrue(restored.contains(mapped));assertFalse(restored.contains("web:$sourceId"));data.fill(0)
   }}}finally{root.deleteRecursively()}
  }
 }
}
