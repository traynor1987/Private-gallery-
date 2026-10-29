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
}
