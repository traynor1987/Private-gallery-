package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule

/** Actual MainActivity authority transitions in the isolated test identity; no owner credentials. */
class Home2MainAuthorityTest {
 @get:Rule val compose=createEmptyComposeRule()
 private fun state(activity:MainActivity)=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 @Suppress("UNCHECKED_CAST") private fun route(activity:MainActivity) = MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
 @Test fun retained_hidden_authority_never_blocks_an_independent_primary_credential_request(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val owner=state(activity)
   try {
    owner.session.lock();owner.key=null
    assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){77}))
    owner.hidden.selected=false;route(activity).value=Route.LOCK
    assertFalse(activity.novaCredentialsRequested)
    activity.requestNovaCredentials()
    assertTrue("Primary Enter must remain usable while an independent Hidden session is retained",activity.novaCredentialsRequested)
    assertFalse(owner.session.isUnlocked)
    assertTrue(owner.hidden.session.isUnlocked)
   }finally{activity.closeNovaCredentials();owner.hidden.leave();owner.session.lock();owner.key=null;route(activity).value=Route.LOCK}
  }}
 }
 @Test fun home_revokes_browser_interaction_synchronously_before_the_next_composition(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val owner=state(activity);val key=ByteArray(32){41}
   try {
    owner.key=key;owner.session.unlock();owner.hidden.leave();route(activity).value=Route.BROWSER
    owner.homePrimaryAuthentication=owner.session.captureAuthenticationEpoch();owner.homeVisible=false
    assertTrue(activity.primarySessionRetainedForNova());assertFalse(activity.primaryBrowserCoveredByNovaHome())
    activity.returnPrivateHome()
    assertTrue("Home authority changes before Compose can recompose",activity.primaryBrowserCoveredByNovaHome())
    assertTrue(activity.primarySessionRetainedForNova())
    activity.panicExit()
    assertFalse(activity.primarySessionRetainedForNova());assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.session.isUnlocked)
   }finally{owner.hidden.leave();owner.session.lock();owner.key=null;key.fill(0);route(activity).value=Route.LOCK}
  }}
 }
 @Suppress("UNCHECKED_CAST")
 @Test fun wallpaper_capture_uses_actual_primary_image_owner_inside_retained_hidden_and_retires_on_space_change_and_panic(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val retained=state(activity);val key=ByteArray(32){53}
   val requests=MainActivity::class.java.getDeclaredField("novaWallpaperRequest\$delegate").apply{isAccessible=true}.get(activity)as MutableState<Any?>
   fun captured():uk.co.traynor.privategallery.core.home2.WallpaperOwner {
    val request=checkNotNull(requests.value)
    return request.javaClass.getDeclaredField("owner").apply{isAccessible=true}.get(request)as uk.co.traynor.privategallery.core.home2.WallpaperOwner
   }
   try {
    retained.key=key;retained.session.unlock()
    assertTrue(retained.hidden.session.complete(retained.hidden.session.beginAuthentication(),ByteArray(32){71}))
    retained.hidden.selected=true;retained.hidden.visible=false;retained.hidden.authenticated=true;retained.primaryGallery=true
    retained.homeVisible=false;retained.homeHiddenAuthentication=retained.hidden.session.authentication();route(activity).value=Route.GALLERY
    activity.openNovaWallpaper("captured-primary-image",current={retained.hidden.selected&&retained.primaryGallery&&!retained.hidden.visible})
    val primary=captured()
    assertEquals(uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.Owner.PRIMARY,primary.owner)
    primary.open(activity)!!.use{assertEquals(uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.Owner.PRIMARY,it.access.owner)}
    retained.hidden.visible=true
    assertFalse(primary.current());assertTrue(retained.hidden.session.isUnlocked)
    requests.value=null
    val authority=retained.hidden.session.authority()
    activity.openNovaWallpaper("captured-hidden-image",retained.hidden.session,current={retained.hidden.visible&&retained.hidden.session.isAuthorityCurrent(authority)})
    val hidden=captured();assertEquals(uk.co.traynor.privategallery.core.evidence.EvidenceCatalog.Owner.HIDDEN,hidden.owner)
    hidden.open(activity)!!.use{coordinator->retained.hidden.session.revokeOperations();assertFalse(coordinator.current())}
    assertFalse(hidden.current());assertTrue(retained.hidden.session.isUnlocked)
    requests.value=null;retained.hidden.visible=false;retained.hidden.selected=false
    activity.openNovaWallpaper("panic-primary-image",current={true})
    val panic=captured();assertTrue(panic.current());activity.panicExit();assertFalse(panic.current())
   }finally{requests.value=null;retained.hidden.leave();retained.session.lock();retained.key=null;key.fill(0);route(activity).value=Route.LOCK}
  }}
 }
 @Test fun real_main_primary_image_inside_retained_hidden_previews_and_applies_only_primary_wallpaper(){
  val saved=mutableListOf<Pair<java.io.File,java.io.File>>()
  val roots=mutableListOf<java.io.File>()
  val key=ByteArray(32){53};var original:uk.co.traynor.privategallery.core.vault.VaultItem?=null
  var payload:java.io.File?=null;var ciphertext=ByteArray(0)
  try {
   ActivityScenario.launch(MainActivity::class.java).use{scenario->
    scenario.onActivity{activity->
     // Preserve any other synthetic fixture ciphertext; this test owns fresh real native roots.
     listOf(java.io.File(activity.filesDir,"vault"),java.io.File(activity.filesDir,"evidence-state"),java.io.File(activity.noBackupFilesDir,"hidden-media-v1"),java.io.File(activity.noBackupFilesDir,"evidence-state")).forEach{root->
      if(root.exists()){val backup=java.io.File(root.parentFile,root.name+"-wallpaper-fixture-"+java.util.UUID.randomUUID());check(root.renameTo(backup));saved.add(root to backup)}
      roots.add(root)
     }
     val retained=state(activity);retained.key=key;retained.session.unlock()
     assertTrue(retained.hidden.session.complete(retained.hidden.session.beginAuthentication(),ByteArray(32){71}));retained.hidden.select()
     enterExternalEditingMediaFixture(activity)
     retained.hidden.visible=false;retained.primaryGallery=true;route(activity).value=Route.GALLERY
     val bitmap=android.graphics.Bitmap.createBitmap(64,48,android.graphics.Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.MAGENTA)}
     val bytes=java.io.ByteArrayOutputStream().use{out->bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);out.toByteArray()};bitmap.recycle()
     original=(uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(activity,key).importVerified(
      uk.co.traynor.privategallery.core.vault.VaultImportSource("main-wallpaper.png","image/png",{java.io.ByteArrayInputStream(bytes)}))as uk.co.traynor.privategallery.core.vault.ImportResult.Imported).item
     bytes.fill(0);payload=java.io.File(activity.filesDir,"vault/payloads/${original!!.id}.vault");ciphertext=payload!!.readBytes()
     @Suppress("UNCHECKED_CAST") val current=MainActivity::class.java.getDeclaredMethod("capturePrimaryEditorAuthority").apply{isAccessible=true}.invoke(activity)as ()->Boolean
     assertTrue(current());activity.openNovaWallpaper(original!!.id,current=current)
    }
    compose.waitUntil(15_000){compose.onAllNodesWithTag("wallpaper-zoom").fetchSemanticsNodes().isNotEmpty()}
    compose.onNodeWithText("Apply to Primary Space").performScrollTo().assertIsDisplayed().performClick()
    compose.waitUntil(10_000){compose.onAllNodesWithTag("wallpaper-editor").fetchSemanticsNodes().isEmpty()}
    scenario.onActivity{activity->
     val retained=state(activity)
     assertTrue(retained.hidden.selected);assertTrue(retained.hidden.session.isUnlocked)
     uk.co.traynor.privategallery.core.evidence.EvidenceAccess.primary(activity,key,{retained.key},{retained.session.isUnlocked},{key.copyOf()},Any()).use{access->
      uk.co.traynor.privategallery.core.home2.SpaceContext(activity,access).use{space->assertNotNull(uk.co.traynor.privategallery.core.home2.WallpaperStore(space).read().image)}
     }
     uk.co.traynor.privategallery.core.evidence.EvidenceAccess.hidden(activity,retained.hidden.session){true}.use{access->
      uk.co.traynor.privategallery.core.home2.SpaceContext(activity,access).use{space->assertNull(uk.co.traynor.privategallery.core.home2.WallpaperStore(space).read().image)}
     }
     assertArrayEquals(ciphertext,payload!!.readBytes())
     assertArrayEquals(original!!.plaintextSha256,uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(activity,key).items().single().plaintextSha256)
     activity.panicExit()
    }
   }
  }finally{key.fill(0);ciphertext.fill(0);roots.forEach{it.deleteRecursively()};saved.asReversed().forEach{(root,backup)->check(backup.renameTo(root))}}
 }
 @Test fun pinning_primary_settings_capture_retires_on_route_space_and_same_key_reauthentication(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val retained=state(activity);val key=ByteArray(32){61}
   try {
    retained.hidden.leave();retained.key=key;retained.session.unlock()
    retained.homePrimaryAuthentication=retained.session.captureAuthenticationEpoch();retained.homeVisible=false;route(activity).value=Route.SETTINGS
    val original=activity.captureNovaPinningAuthority();assertTrue(original())
    route(activity).value=Route.VAULT;assertFalse(original());route(activity).value=Route.SETTINGS
    retained.hidden.selected=true;assertFalse(original());retained.hidden.selected=false
    retained.session.lock();assertFalse(original())
    retained.session.unlock();retained.homePrimaryAuthentication=retained.session.captureAuthenticationEpoch()
    assertFalse("The same key object cannot revive a previous pinning credential",original())
    val fresh=activity.captureNovaPinningAuthority();assertTrue(fresh())
    activity.panicExit();assertFalse(fresh());assertFalse(original())
   }finally{retained.hidden.leave();retained.session.lock();retained.key=null;key.fill(0);route(activity).value=Route.LOCK}
  }}
 }
 @Test fun pinning_hidden_settings_capture_requires_its_original_hidden_authentication(){
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{activity->
   val retained=state(activity);val key=ByteArray(32){62}
   try {
    retained.session.lock();retained.key=null
    assertTrue(retained.hidden.session.complete(retained.hidden.session.beginAuthentication(),key.copyOf()))
    retained.hidden.selected=true;retained.hidden.authenticated=true
    retained.homeHiddenAuthentication=retained.hidden.session.authentication();retained.homeVisible=false;route(activity).value=Route.SETTINGS
    val original=activity.captureNovaPinningAuthority();assertTrue(original())
    retained.hidden.selected=false;assertFalse(original());retained.hidden.selected=true
    retained.hidden.session.lock();assertFalse(original())
    assertTrue(retained.hidden.session.complete(retained.hidden.session.beginAuthentication(),key.copyOf()))
    retained.hidden.authenticated=true;retained.homeHiddenAuthentication=retained.hidden.session.authentication()
    assertFalse("A later Hidden PIN session cannot inherit the old action",original())
    val fresh=activity.captureNovaPinningAuthority();assertTrue(fresh())
    activity.panicExit();assertFalse(fresh());assertFalse(original());assertFalse(retained.hidden.session.isUnlocked)
   }finally{retained.hidden.leave();retained.session.lock();retained.key=null;key.fill(0);route(activity).value=Route.LOCK}
  }}
 }

}
