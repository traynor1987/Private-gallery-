package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import uk.co.traynor.privategallery.core.evidence.*

/** Actual Main factories and epochs; controlled fresh debug application only, never a production vault. */
class EvidenceAuthority179Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun originatingVaultFactoryPickerEpochHideAndPanicNeverReuseAuthority() {
  val activity=compose.activity;assertTrue(BuildConfig.DEBUG);assertEquals("uk.co.traynor.privategallery.hiddenprototype",activity.packageName)
  val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
  @Suppress("UNCHECKED_CAST") val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity)as MutableState<Route>
  @Suppress("UNCHECKED_CAST") val ownerState=MainActivity::class.java.getDeclaredField("evidenceOwner\$delegate").apply{isAccessible=true}.get(activity)as MutableState<EvidenceOwnerSession?>
  val primaryRoot=File(activity.filesDir,"evidence-state");val hiddenRoot=File(activity.noBackupFilesDir,"evidence-state")
  assertFalse("Fresh controlled evidence fixture only",primaryRoot.exists());assertFalse(hiddenRoot.exists())
  val key=ByteArray(32){27};val hiddenKey=ByteArray(32){83}
  try {
   compose.runOnIdle {
    activity.openEvidenceLocker();assertNull(ownerState.value)
    state.key=key;state.session.unlock();route.value=Route.VAULT
    activity.openEvidenceLocker();val primary=checkNotNull(ownerState.value);assertEquals(EvidenceCatalog.Owner.PRIMARY,primary.owner)
    val old=checkNotNull(primary.newController(activity));assertTrue(old.current())
    state.hidden.select();assertFalse(primary.sameAuthentication());assertFalse(old.current())
    check(state.hidden.session.complete(state.hidden.session.beginAuthentication(),hiddenKey.copyOf()));state.hidden.authenticated=true
    activity.openEvidenceLocker(state.hidden.session);val hidden=checkNotNull(ownerState.value);assertEquals(EvidenceCatalog.Owner.HIDDEN,hidden.owner)
    val operation=checkNotNull(hidden.newController(activity));assertTrue(operation.current())
    state.session.lock();assertTrue("Primary expiry cannot authenticate or revoke Hidden evidence",hidden.sameAuthentication());assertTrue(operation.current())
    state.hidden.session.revokeOperations();assertFalse(operation.current());assertTrue(hidden.sameAuthentication())
    val fresh=checkNotNull(hidden.newController(activity));assertTrue(fresh.current());assertFalse("Old operation cannot revive for picker return",operation.current())
    state.hidden.session.lock();assertFalse(hidden.sameAuthentication());assertFalse(fresh.current())
    check(state.hidden.session.complete(state.hidden.session.beginAuthentication(),hiddenKey.copyOf()));assertFalse("A reauthentication cannot revive an old picker owner",hidden.sameAuthentication())
    activity.openEvidenceLocker(state.hidden.session);val beforeHide=checkNotNull(ownerState.value)
    val hide=MainActivity::class.java.getDeclaredMethod("applyHideContent",Boolean::class.javaPrimitiveType).apply{isAccessible=true}
    hide.invoke(activity,true);assertFalse(beforeHide.sameAuthentication());assertNull(ownerState.value)
    hide.invoke(activity,false)
    activity.openEvidenceLocker(state.hidden.session);val beforePanic=checkNotNull(ownerState.value)
    activity.panicExit();assertFalse(beforePanic.sameAuthentication());assertNull(ownerState.value);assertFalse(state.hidden.session.isUnlocked);assertFalse(state.session.isUnlocked)
   }
  }finally{
   compose.runOnIdle {ownerState.value?.close();ownerState.value=null;state.hidden.leave();state.session.lock();state.key=null;route.value=Route.LOCK
    MainActivity::class.java.getDeclaredMethod("applyHideContent",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(activity,false)}
   key.fill(0);hiddenKey.fill(0);primaryRoot.deleteRecursively();hiddenRoot.deleteRecursively()
  }
 }
}
