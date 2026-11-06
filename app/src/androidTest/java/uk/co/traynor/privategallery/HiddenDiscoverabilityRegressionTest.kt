package uk.co.traynor.privategallery

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenEntrySequence

/** Actual Activity admission boundaries; stored discovery flags and navigation are not credentials. */
class HiddenDiscoverabilityRegressionTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 private val activity get()=compose.activity
 private val owner get()=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 @Suppress("UNCHECKED_CAST") private fun <T> state(name:String)=MainActivity::class.java.getDeclaredField(name+"\$delegate").apply{isAccessible=true}.get(activity) as MutableState<T>
 private fun completeSequence()=HiddenEntrySequence(android.os.SystemClock::elapsedRealtime).also {sequence->
  repeat(5){assertFalse(sequence.tap(false))};assertFalse(sequence.tap(true));repeat(3){assertFalse(sequence.tap(false))};assertTrue(sequence.tap(false))
 }
 private fun primary(route:Route=Route.VAULT) {
  owner.hidden.leave();owner.key=ByteArray(32){37};owner.session.unlock()
  owner.homePrimaryAuthentication=owner.session.captureAuthenticationEpoch();owner.homeVisible=false
  state<Route>("route").value=route
 }
 private fun cleanup() {owner.hidden.leave();owner.key?.fill(0);owner.key=null;owner.session.lock();state<Route>("route").value=Route.LOCK}
 @Test fun ordinary_switch_never_enters_hidden_for_any_persisted_discovery_or_concealment_flag() {
  compose.runOnIdle {
   val oldSecret=state<Boolean>("secretDiscovered").value;val oldHide=state<Boolean>("hideContent").value
   try {for(discovered in listOf(false,true))for(concealed in listOf(false,true)) {
    primary();state<Boolean>("secretDiscovered").value=discovered;state<Boolean>("hideContent").value=concealed
    activity.switchNovaSpace()
    assertFalse("Public switch cannot select Hidden: discovered=$discovered concealed=$concealed",owner.hidden.selected)
    assertFalse(owner.hidden.session.isUnlocked)
   }} finally {state<Boolean>("secretDiscovered").value=oldSecret;state<Boolean>("hideContent").value=oldHide;cleanup()}
  }
 }
 @Test fun settings_admission_requires_fresh_completed_sequence_and_never_authenticates_hidden() {
  compose.runOnIdle {
   val oldHide=state<Boolean>("hideContent").value
   try {
    primary(Route.SETTINGS);state<Boolean>("hideContent").value=false
    activity.openHiddenFromSettings(HiddenEntrySequence(android.os.SystemClock::elapsedRealtime))
    assertFalse(owner.hidden.selected)
    val completed=completeSequence()
    activity.openHiddenFromSettings(completed)
    assertTrue(owner.hidden.selected);assertFalse(owner.hidden.authenticated);assertFalse(owner.hidden.session.isUnlocked)
    owner.hidden.leave();activity.openHiddenFromSettings(completed)
    assertFalse("A completed sequence is one use",owner.hidden.selected)
    primary(Route.VAULT);activity.openHiddenFromSettings(completeSequence());assertFalse(owner.hidden.selected)
    primary(Route.SETTINGS);state<Boolean>("hideContent").value=true
    activity.openHiddenFromSettings(completeSequence());assertFalse(owner.hidden.selected)
   }finally{state<Boolean>("hideContent").value=oldHide;cleanup()}
  }
 }
 @Test fun intent_extras_and_activity_restoration_cannot_recreate_hidden_entry() {
  compose.runOnIdle {cleanup();owner.hidden.selected=true;owner.hidden.visible=true;assertFalse(owner.hidden.session.isUnlocked)}
  compose.activityRule.scenario.recreate()
  compose.runOnIdle {assertFalse(owner.hidden.selected);assertFalse(owner.hidden.session.isUnlocked)}
  val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
  ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("hidden",true).putExtra("secret-discovered",true).putExtra("route","HIDDEN")).use {scenario->
   scenario.onActivity {a->val restored=ViewModelProvider(a)[ProtectedSessionState::class.java];assertFalse(restored.hidden.selected);assertFalse(restored.hidden.session.isUnlocked)}
  }
 }
 @Test fun authenticated_hidden_exit_remains_available_without_a_primary_entry_path() {
  compose.runOnIdle {
   val oldHide=state<Boolean>("hideContent").value
   try {
    primary();state<Boolean>("hideContent").value=false
    assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){83}))
    owner.hidden.select();owner.hidden.authenticated=true
    val hiddenAuthentication=owner.hidden.session.authentication()
    activity.switchNovaSpace()
    assertFalse(owner.hidden.selected);assertFalse(owner.hidden.visible)
    assertTrue("Returning to Primary preserves Hidden's independent authentication timeout",owner.hidden.session.isAuthenticationCurrent(hiddenAuthentication))
    activity.switchNovaSpace()
    assertFalse("Primary navigation cannot re-enter even while Hidden authentication is retained",owner.hidden.selected)
    assertFalse(owner.hidden.visible);assertTrue(owner.hidden.session.isAuthenticationCurrent(hiddenAuthentication))
    activity.panicExit()
    assertFalse(owner.hidden.session.isAuthenticationCurrent(hiddenAuthentication));assertFalse(owner.hidden.session.isUnlocked)
    assertFalse(owner.session.isUnlocked);assertNull(owner.key)
   }finally{state<Boolean>("hideContent").value=oldHide;cleanup()}
  }
 }
 @Test fun rendered_original_version_sequence_opens_only_the_independent_hidden_pin_entry() {
  assertTrue(activity.packageName.endsWith(".hiddenprototype"))
  val root=java.io.File(activity.cacheDir,"hidden-entry-ui-${java.util.UUID.randomUUID()}").apply{mkdirs()}
  val directories=listOf("home2","home1710").map{java.io.File(activity.filesDir,"evidence-state/$it")}
  val saved=directories.associateWith{directory->if(!directory.exists())null else java.io.File(root,directory.name).also{check(directory.renameTo(it))}}
  val preferences=MainActivity::class.java.getDeclaredField("appSettings").apply{isAccessible=true}.get(activity) as android.content.SharedPreferences
  val names=listOf("secret-discovered","app-theme","app-theme-home-pending")
  val before=names.associateWith{preferences.all[it]}
  val oldHide=compose.runOnIdle{state<Boolean>("hideContent").value}
  val oldSecret=compose.runOnIdle{state<Boolean>("secretDiscovered").value}
  try {
   compose.runOnIdle{primary(Route.SETTINGS);state<Boolean>("hideContent").value=false;state<Boolean>("secretDiscovered").value=true}
   compose.onNodeWithText("Updates & About").performScrollTo().performClick()
   repeat(5){compose.onNodeWithText("Installed").performScrollTo().performClick()}
   compose.runOnIdle{assertFalse(owner.hidden.selected)}
   compose.onNodeWithText("NovaOS ${BuildConfig.VERSION_NAME}").performScrollTo().performClick()
   repeat(3){compose.onNodeWithText("Installed").performScrollTo().performClick()}
   compose.runOnIdle{assertFalse(owner.hidden.selected)}
   compose.onNodeWithText("Installed").performScrollTo().performClick()
   compose.onNode(isDialog()).assertExists()
   compose.onNodeWithText("Close").assertIsDisplayed()
   compose.onAllNodesWithContentDescription("Use biometrics",substring=true).assertCountEquals(0)
   compose.onAllNodesWithContentDescription("Use fingerprint",substring=true).assertCountEquals(0)
   compose.onAllNodesWithText("fingerprint",substring=true,ignoreCase=true).assertCountEquals(0)
   compose.onAllNodesWithText("biometric",substring=true,ignoreCase=true).assertCountEquals(0)
   compose.runOnIdle{assertTrue(owner.hidden.selected);assertFalse(owner.hidden.authenticated);assertFalse(owner.hidden.session.isUnlocked)}
   compose.onNodeWithText("Close").performClick()
  } finally {
   compose.runOnIdle{cleanup();owner.homeVisible=false;state<Boolean>("hideContent").value=oldHide;state<Boolean>("secretDiscovered").value=oldSecret}
   compose.waitForIdle()
   directories.forEach{it.deleteRecursively();saved[it]?.let{backup->check(backup.renameTo(it))}}
   preferences.edit().also{editor->names.forEach{name->when(val value=before[name]){null->editor.remove(name);is Boolean->editor.putBoolean(name,value);is String->editor.putString(name,value)}}}.commit()
   root.deleteRecursively()
  }
 }

}
