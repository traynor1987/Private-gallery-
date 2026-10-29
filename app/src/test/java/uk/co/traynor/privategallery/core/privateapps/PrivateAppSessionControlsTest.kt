package uk.co.traynor.privategallery.core.privateapps

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.function.UnaryOperator

class PrivateAppSessionControlsTest {
 private class Store(private val mode:PrivateAppStore.Owner=PrivateAppStore.Owner.PRIMARY):PrivateAppStore {
  var value=PrivateAppCatalog(mode==PrivateAppStore.Owner.HIDDEN_TOR,emptyList())
  override fun owner()=mode
  override fun read()=value
  override fun update(change:UnaryOperator<PrivateAppCatalog>):PrivateAppCatalog {value=change.apply(value);return value}
 }
 private fun app()=PrivateAppCatalog.App(UUID.randomUUID().toString(),"App","https://example.test/","","browser","",false,true,true)
 @Test fun fresh_authentication_is_single_use_fenced_and_resolves_unchanged_definition() {
  val store=Store();var live=true;val pending=mutableListOf<(Boolean)->Unit>();val opened=mutableListOf<String>()
  val controller=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{opened+=it},authenticate={pending+=it})
  val entry=app();controller.save(entry)
  controller.launch(entry.id());assertTrue(opened.isEmpty());pending.last()(false);assertTrue(opened.isEmpty())
  controller.launch(entry.id());val old=pending.last();controller.launch(entry.id());old(true);assertTrue(opened.isEmpty())
  pending.last()(true);pending.last()(true);assertEquals(listOf(entry.startUrl()),opened)
  controller.launch(entry.id());controller.setAddressBar(entry.id(),false);pending.last()(true);assertEquals(1,opened.size)
  controller.launch(entry.id());live=false;pending.last()(true);live=true;pending.last()(true);assertEquals(1,opened.size)
  controller.launch(entry.id());controller.remove(entry.id());pending.last()(true);assertEquals(1,opened.size)
 }
 @Test fun shared_retention_is_primary_only_and_revoked_controls_have_no_effect() {
  val store=Store();var live=true;var keep=false;var clears=0
  val controller=PrivateAppsController(store.owner(),{live},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{},
   keepSignedIn={keep},changeKeepSignedIn={keep=it},clearProfile={clears++})
  controller.setKeepSignedIn(true);assertTrue(controller.keepsSignedIn());controller.clearSharedProfile();assertEquals(1,clears)
  live=false;assertThrows(IllegalStateException::class.java){controller.setKeepSignedIn(false)}
  assertThrows(IllegalStateException::class.java){controller.clearSharedProfile()};assertTrue(keep);assertEquals(1,clears)
  val hidden=PrivateAppsController(PrivateAppStore.Owner.HIDDEN_TOR,{true},{_,_,_->PrivateAppCatalog(true,emptyList())},{},keepSignedIn={true},changeKeepSignedIn={error("must not retain Hidden")})
  assertFalse(hidden.supportsRetention());assertThrows(IllegalStateException::class.java){hidden.setKeepSignedIn(true)}
 }
 @Test fun atomic_settings_reset_preserves_identity_and_authentication_and_never_resurrects_removed_app() {
  val store=Store();val controller=PrivateAppsController(store.owner(),{true},{a,b,id->PrivateAppCommands.execute(store,a,b,id)},{})
  val entry=app();controller.save(entry);controller.setPinned(entry.id(),true);controller.setAddressBar(entry.id(),false)
  val reset=controller.resetSettings(entry.id()).apps.single()
  assertEquals(entry.id(),reset.id());assertEquals(entry.startUrl(),reset.startUrl());assertTrue(reset.requireAuthentication());assertTrue(reset.addressBar());assertFalse(reset.pinned());assertEquals("",reset.icon())
  controller.setRequireAuthentication(entry.id(),false);assertFalse(controller.load().apps.single().requireAuthentication())
  controller.setRequireAuthentication(entry.id(),true);controller.remove(entry.id());assertTrue(controller.resetSettings(entry.id()).apps.isEmpty())
 }
 @Test fun profile_clear_barrier_rejects_stale_duplicate_and_failed_persistence() {
  val barrier=PrivateAppClearBarrier();var persisted=0
  val old=barrier.begin();val current=barrier.begin()
  assertTrue(barrier.pending());assertFalse(barrier.complete(old){persisted++});assertTrue(barrier.pending())
  assertThrows(IllegalStateException::class.java){barrier.complete(current){error("storage unavailable")}}
  assertTrue(barrier.pending());assertTrue(barrier.complete(current){persisted++});assertFalse(barrier.pending())
  assertFalse(barrier.complete(current){persisted++});assertEquals(1,persisted)
 }
}
