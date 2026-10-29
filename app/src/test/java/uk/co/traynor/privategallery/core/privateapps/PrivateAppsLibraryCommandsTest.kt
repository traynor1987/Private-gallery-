package uk.co.traynor.privategallery.core.privateapps

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.io.path.createTempDirectory

class PrivateAppsLibraryCommandsTest {
 private fun app(name:String)=PrivateAppCatalog.App(UUID.randomUUID().toString(),name,"https://example.test/","","browser","",false,true,false)

 @Test fun favourites_and_order_persist_without_overwriting_newer_fields() {
  val root=createTempDirectory("apps-library").toFile()
  val key=ByteArray(32){it.toByte()}
  fun store()=PrimaryPrivateAppStore(root,key,{true},{it()})
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{true},{action,bytes,id->PrivateAppCommands.execute(store(),action,bytes,id)},{})
  try {
   val first=app("First");val second=app("Second");val third=app("Third")
   listOf(first,second,third).forEach{controller.save(it)}
   controller.save(second.withName("New title"))
   controller.setPinned(second.id(),true)
   controller.setAddressBar(second.id(),false)
   assertEquals(listOf(first.id(),second.id(),third.id()),store().read().apps.map{it.id()})
   assertEquals("New title",store().read().apps[1].name())
   controller.move(second.id(),-1)
   val reopened=store().read().apps
   assertEquals(listOf(second.id(),first.id(),third.id()),reopened.map{it.id()})
   assertTrue(reopened.first().pinned())
   assertFalse(reopened.first().addressBar())
   controller.setPinned(second.id(),false)
   controller.move(second.id(),1)
   assertEquals(listOf(first.id(),second.id(),third.id()),store().read().apps.map{it.id()})
   assertFalse(store().read().apps[1].pinned())
   controller.remove(second.id())
   controller.setPinned(second.id(),true)
   controller.setAddressBar(second.id(),true)
   controller.move(second.id(),-1)
   assertEquals(listOf(first.id(),third.id()),store().read().apps.map{it.id()})
  }finally{key.fill(0);root.deleteRecursively()}
 }

 @Test fun revocation_and_invalid_moves_never_send_mutations() {
  var live=true;var calls=0
  val controller=PrivateAppsController(PrivateAppStore.Owner.HIDDEN_TOR,{live},{_,_,_->calls++;PrivateAppCatalog(true,emptyList())},{})
  val id=UUID.randomUUID().toString()
  assertThrows(IllegalArgumentException::class.java){controller.move(id,0)}
  assertThrows(IllegalArgumentException::class.java){controller.move(id,2)}
  assertEquals(0,calls)
  live=false
  assertThrows(IllegalStateException::class.java){controller.setPinned(id,true)}
  assertThrows(IllegalStateException::class.java){controller.setPinned(id,false)}
  assertThrows(IllegalStateException::class.java){controller.setAddressBar(id,true)}
  assertThrows(IllegalStateException::class.java){controller.move(id,-1)}
  assertThrows(IllegalStateException::class.java){controller.move(id,1)}
  assertEquals(0,calls)
 }

 @Test fun revocation_after_command_prevents_returning_library_state() {
  var live=true
  val controller=PrivateAppsController(PrivateAppStore.Owner.HIDDEN_VPN,{live},{_,_,_->live=false;PrivateAppCatalog(false,emptyList())},{})
  assertThrows(IllegalStateException::class.java){controller.setPinned(UUID.randomUUID().toString(),true)}
 }

 @Test fun app_launch_callback_receives_current_record_only_while_authorized() {
  val saved=app("Updated app");var live=true;var launched:PrivateAppCatalog.App?=null
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{live},{_,_,_->PrivateAppCatalog(false,listOf(saved))},
   {error("Dedicated launch must not use generic navigation")},onLaunchApp={launched=it})
  controller.launch(saved.id())
  assertEquals(saved,launched)
  launched=null;live=false
  assertThrows(IllegalStateException::class.java){controller.launch(saved.id())}
  assertNull(launched)
 }
}
