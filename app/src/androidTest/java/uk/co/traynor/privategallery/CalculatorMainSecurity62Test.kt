package uk.co.traynor.privategallery

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.MutableState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.hidden.*
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

/** Real MainActivity calculator gesture → existing PIN UI → shared credential comparison → Panic. */
class CalculatorMainSecurity62Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val activity get()=compose.activity
 private val owner get()=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 @Suppress("UNCHECKED_CAST") private fun<T> state(name:String)=MainActivity::class.java.getDeclaredField(name+"\$delegate").apply{isAccessible=true}.get(activity)as MutableState<T>
 @Test fun actualCalculatorAdmissionRequiresIndependentPinAndPanicRejectsRetainedCallbacks(){
  check(activity.packageName.endsWith(".hiddenprototype"))
  val root=File(activity.cacheDir,"calculator-main62-${UUID.randomUUID()}").apply{mkdirs()}
  val roots=listOf(File(activity.filesDir,"evidence-state"),File(activity.noBackupFilesDir,"evidence-state"),File(activity.noBackupFilesDir,"hidden-v1-default"),File(activity.noBackupFilesDir,"hidden-media-v1"))
  val saved=roots.associateWith{file->if(file.exists())File(root,"saved-${UUID.randomUUID()}").also{check(file.renameTo(it))}else null}
  val prefix="calculator-main62-${UUID.randomUUID()}";val prefs=mutableSetOf<String>()
  val context=object:ContextWrapper(activity){override fun getNoBackupFilesDir()=File(root,"keys").apply{mkdirs()};override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("$prefix-$name",mode).also{prefs+="$prefix-$name"}}
  val keysField=MainActivity::class.java.getDeclaredField("keys").apply{isAccessible=true};val previousKeys=keysField.get(activity);val keys=PinVaultKeyStore(context);val primary=keys.create("613842".toCharArray())
  val hidden=HiddenCredentialStore(activity).create("905184".toCharArray());val setup=HiddenSession{android.os.SystemClock.elapsedRealtime()};check(setup.complete(setup.beginAuthentication(),hidden.copyOf()))
  val store=HiddenCalculatorEntryStore(activity);val previousHide=state<Boolean>("hideContent").value
  fun launch(){compose.runOnIdle{owner.hidden.leave();owner.key=primary;owner.session.unlock();state<Route>("route").value=Route.VAULT;activity.returnPrivateHome()};compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("drawer-search").performTextInput("Nova Calculator");compose.onNodeWithTag("app-nova-calculator").performClick();compose.onNodeWithTag("calculator").assertIsDisplayed()}
  fun hold():()->Boolean{val node=compose.onNodeWithTag("calculator-key-=").performScrollTo();return checkNotNull(node.fetchSemanticsNode().config[SemanticsActions.OnLongClick].action)}
  fun submit(pin:String){pin.forEach{compose.onNodeWithText(it.toString(),useUnmergedTree=false).performClick()};compose.onNodeWithText("Unlock").performClick()}
  try{
   compose.runOnIdle{keysField.set(activity,keys);state<Boolean>("hideContent").value=false}
   launch();val disabled=hold();compose.runOnIdle{repeat(5){disabled()};assertFalse(owner.hidden.selected)}
   store.setEnabled(true,setup,checkNotNull(setup.authentication())){true}
   val allowed=hold();compose.runOnIdle{repeat(5){allowed()};assertTrue(owner.hidden.selected);assertFalse(owner.hidden.session.isUnlocked);assertFalse(owner.hidden.authenticated)}
   compose.onNodeWithText("Enter passcode").assertIsDisplayed();submit("613842")
   compose.waitUntil(15000){compose.onAllNodes(hasText("Could not unlock",substring=true)).fetchSemanticsNodes().isNotEmpty()}
   compose.runOnIdle{assertFalse(owner.hidden.session.isUnlocked)}
   submit("905184")
   compose.waitUntil(15000){compose.runOnIdle{owner.hidden.session.isUnlocked&&owner.hidden.authenticated}}
   compose.runOnIdle{activity.panicExit();assertFalse(owner.hidden.session.isUnlocked);assertFalse(owner.session.isUnlocked);repeat(5){allowed()};assertFalse(owner.hidden.selected)}
   compose.onNodeWithTag("nova-protected-lock").assertIsDisplayed()
  }finally{
   compose.runOnIdle{owner.hidden.leave();owner.session.lock();owner.key=null;owner.homeVisible=false;activity.closeNovaCredentials();state<Route>("route").value=Route.LOCK;keysField.set(activity,previousKeys);state<Boolean>("hideContent").value=previousHide}
   compose.waitForIdle();setup.lock();roots.forEach{it.deleteRecursively();saved[it]?.let{old->check(old.renameTo(it))}};prefs.forEach{activity.deleteSharedPreferences(it)};primary.fill(0);hidden.fill(0);root.deleteRecursively()
  }
 }
}
