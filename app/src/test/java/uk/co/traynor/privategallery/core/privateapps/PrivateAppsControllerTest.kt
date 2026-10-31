package uk.co.traynor.privategallery.core.privateapps
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import kotlin.io.path.createTempDirectory
class PrivateAppsControllerTest {
 @Test fun launch_resolves_saved_id_and_revoked_controller_cannot_navigate() {
  val root=createTempDirectory("apps-controller").toFile();var live=true;val visits=mutableListOf<String>()
  val store=PrimaryPrivateAppStore(root,ByteArray(32),{live},{check(live);it()})
  val controller=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{visits.add(it)})
  try {
   val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Fixture","https://example.test/","","browser","",false,true,false)
   controller.save(app);controller.launch(app.id());assertEquals(listOf(app.startUrl()),visits)
   controller.remove(app.id());assertThrows(NoSuchElementException::class.java){controller.launch(app.id())}
   val guarded=PrivateAppCatalog.App(app.id(),app.name(),app.startUrl(),app.scope(),app.display(),app.icon(),false,true,true)
   controller.save(guarded);assertThrows(IllegalStateException::class.java){controller.launch(app.id())}
   controller.save(app);live=false;assertThrows(IllegalStateException::class.java){controller.launch(app.id())}
   assertThrows(IllegalStateException::class.java){controller.load()};assertEquals(1,visits.size)
  }finally{root.deleteRecursively()}
 }
 @Test fun revoke_before_callback_prevents_publication_and_wipes_request() {
  var live=true;var payload:ByteArray?=null
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{live},{_,bytes,_->payload=bytes;live=false;PrivateAppCatalog(false,emptyList())},{error("must not launch")})
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Fixture","https://example.test/","","browser","",false,true,false)
  assertThrows(IllegalStateException::class.java){controller.save(app)}
  assertTrue(checkNotNull(payload).all{it==0.toByte()})
 }
 @Test fun move_to_publishes_once_preserves_complete_records_and_wipes_payload() {
  val root=createTempDirectory("apps-move-to").toFile();var live=true;var commits=0;var payload:ByteArray?=null
  val key=ByteArray(32)
  val store=PrimaryPrivateAppStore(root,key,{live},{check(live);commits++;it()})
  val controller=PrivateAppsController(store.owner(),{live},{action,bytes,id->payload=bytes;PrivateAppCommands.execute(store,action,bytes,id)},{})
  val first=PrivateAppCatalog.App(UUID.randomUUID().toString(),"First","https://example.test/","","browser","",false,true,false)
  val rich=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Rich","https://example.test/app","https://example.test/","standalone","AQID",true,false,true,false,"Retained report")
  try {
   controller.save(first);controller.save(rich);val before=commits
   assertEquals(listOf(rich,first),controller.moveTo(rich.id(),0).apps)
   assertEquals(before+1,commits);assertTrue(checkNotNull(payload).all{it==0.toByte()})
   assertEquals(listOf(rich,first),PrimaryPrivateAppStore(root,key,{true},{it()}).read().apps)
   assertThrows(IllegalArgumentException::class.java){controller.moveTo(rich.id(),2)}
   assertThrows(IllegalArgumentException::class.java){controller.moveTo(rich.id(),-1)}
   assertThrows(IllegalArgumentException::class.java){controller.moveTo(rich.id(),PrivateAppCatalog.MAX_APPS)}
   assertThrows(IllegalArgumentException::class.java){controller.moveTo(UUID.randomUUID().toString(),0)}
   assertEquals(before+1,commits)
   live=false
   assertThrows(IllegalStateException::class.java){controller.moveTo(rich.id(),1)}
   assertEquals(before+1,commits)
  }finally{key.fill(0);root.deleteRecursively()}
 }

 @Test fun drawer_launch_requires_the_original_presentation_after_delayed_authentication() {
  val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Guarded","https://example.test/","","browser","",false,true,true)
  var saved=app;var presentation=true;var live=true;var pending:((Boolean)->Unit)?=null;var launched=0;var closed=0
  val controller=PrivateAppsController(PrivateAppStore.Owner.PRIMARY,{live},{_,_,_->PrivateAppCatalog(false,listOf(saved))},
   {error("Dedicated app launch expected")},onLaunchApp={launched++},authenticate={pending=it})
  controller.launchFromDrawer(app.id(),{presentation}){closed++}
  presentation=false;checkNotNull(pending).invoke(true)
  assertEquals(0,launched);assertEquals(0,closed)
  presentation=true;controller.launchFromDrawer(app.id(),{presentation}){closed++}
  checkNotNull(pending).invoke(false);assertEquals(0,launched)
  controller.launchFromDrawer(app.id(),{presentation}){closed++}
  saved=app.withName("Changed while authentication pending")
  checkNotNull(pending).invoke(true);assertEquals(0,launched);assertEquals(0,closed)
  controller.launchFromDrawer(app.id(),{presentation}){closed++}
  checkNotNull(pending).invoke(true);assertEquals(1,launched);assertEquals(1,closed)
  controller.launchFromDrawer(app.id(),{presentation}){closed++}
  live=false;checkNotNull(pending).invoke(true);assertEquals(1,launched);assertEquals(1,closed)
 }

}
