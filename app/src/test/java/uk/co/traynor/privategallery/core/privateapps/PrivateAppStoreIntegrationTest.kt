package uk.co.traynor.privategallery.core.privateapps

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.function.UnaryOperator
import kotlin.io.path.createTempDirectory
import uk.co.traynor.privategallery.core.hidden.HiddenSession

class PrivateAppStoreIntegrationTest {
 private fun app(name:String="Saved app")=PrivateAppCatalog.App(UUID.randomUUID().toString(),name,"https://example.test/app/","https://example.test/app/","standalone","aWNvbg==",true,true,false)
 @Test fun both_vaults_support_crud_restart_and_independent_ownership() {
  val root=createTempDirectory("private-apps-both").toFile()
  val key=ByteArray(32){9};val hidden=HiddenSession{100L}
  assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
  try {
   val primary=PrimaryPrivateAppStore(File(root,"primary"),key,{true},{it()})
   val vpn=HiddenPrivateAppStore(root,hidden,hidden.authority(),false)
   val tor=HiddenPrivateAppStore(root,hidden,hidden.authority(),true)
   assertEquals(PrivateAppStore.Owner.PRIMARY,primary.owner())
   assertEquals(PrivateAppStore.Owner.HIDDEN_VPN,vpn.owner())
   assertEquals(PrivateAppStore.Owner.HIDDEN_TOR,tor.owner())
   for(store:PrivateAppStore in listOf(primary,vpn,tor)) {
    val a=app(store.owner().name);val b=app("Second")
    store.update{it.upsert(a).upsert(b)}
    store.update{it.upsert(a.withName("Renamed")).move(b.id(),-1)}
    assertEquals(listOf(b.id(),a.id()),store.read().apps.map{it.id()})
    assertEquals("Renamed",store.read().apps[1].name())
    assertEquals(a.icon(),store.read().apps[1].icon())
    assertEquals(a.startUrl(),store.read().apps[1].startUrl())
    store.update{it.remove(b.id())}
    assertEquals(1,store.read().apps.size)
   }
   assertNotEquals(primary.read().apps[0].id(),vpn.read().apps[0].id())
   assertNotEquals(vpn.read().apps[0].id(),tor.read().apps[0].id())
   assertEquals(primary.read().apps,PrimaryPrivateAppStore(File(root,"primary"),key,{true},{it()}).read().apps)
   val saved=vpn.read().apps;hidden.lock()
   assertTrue(hidden.complete(hidden.beginAuthentication(),key.copyOf()))
   assertThrows(IllegalStateException::class.java){vpn.read()}
   assertEquals(saved,HiddenPrivateAppStore(root,hidden,hidden.authority(),false).read().apps)
  }finally{hidden.lock();key.fill(0);root.deleteRecursively()}
 }
 @Test fun captured_primary_handle_never_inherits_a_new_unlock() {
  val root=createTempDirectory("private-apps-epoch").toFile()
  val gate=Any();var identity:Any?=Any();var keyCopies=mutableListOf<ByteArray>()
  val access=PrimaryPrivateAppAccess(root,checkNotNull(identity),{identity},{true},{ByteArray(32){5}.also{keyCopies.add(it)}},gate)
  try {
   access.update{it.upsert(app())}
   assertTrue(keyCopies.all{it.all{b->b==0.toByte()}})
   identity=Any() // Same key material after lock/unlock is still a different authentication.
   assertThrows(IllegalStateException::class.java){access.read()}
   assertThrows(IllegalStateException::class.java){access.update{it}}
   val fresh=PrimaryPrivateAppAccess(root,checkNotNull(identity),{identity},{true},{ByteArray(32){5}},gate)
   assertEquals(1,fresh.read().apps.size)
   assertThrows(IllegalStateException::class.java){fresh.update{identity=Any();it.remove(it.apps[0].id())}}
   val final=PrimaryPrivateAppAccess(root,checkNotNull(identity),{identity},{true},{ByteArray(32){5}},gate)
   assertEquals(1,final.read().apps.size)
  }finally{root.deleteRecursively()}
 }
 @Test fun corrupt_primary_file_cannot_be_silently_replaced() {
  val root=createTempDirectory("private-apps-corrupt").toFile();val key=ByteArray(32){4}
  try {
   val store=PrimaryPrivateAppStore(root,key,{true},{it()});store.update{it.upsert(app())}
   val file=File(root,"primary-private-apps-v1.enc")
   val bytes=file.readBytes().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()};file.writeBytes(bytes)
   assertThrows(Exception::class.java){store.update{PrivateAppCatalog(false,emptyList())}}
   assertArrayEquals(bytes,file.readBytes())
  }finally{key.fill(0);root.deleteRecursively()}
 }
 @Test fun missing_catalogue_keeps_existing_browser_records_unchanged() {
  val root=createTempDirectory("private-apps-migration").toFile()
  try {
   val legacy=File(root,"browser-history-v2.enc").apply{writeBytes(byteArrayOf(1,2,3))}
   val store=PrimaryPrivateAppStore(root,ByteArray(32),{true},{it()})
   assertTrue(store.read().apps.isEmpty());assertFalse(File(root,"primary-private-apps-v1.enc").exists())
   store.update{it.upsert(app())};assertArrayEquals(byteArrayOf(1,2,3),legacy.readBytes())
  }finally{root.deleteRecursively()}
 }
}
