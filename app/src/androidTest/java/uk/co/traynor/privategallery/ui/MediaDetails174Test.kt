package uk.co.traynor.privategallery.ui
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
class MediaDetails174Test {
 @get:Rule val compose=createComposeRule()
 @Test fun nativeDateAndTimePickersFollowOwnerScreenshotPolicy() {
  var secure by mutableStateOf(true)
  lateinit var hostActivity:android.app.Activity
  var item=VaultItem("picker","image/jpeg","picker.jpg",1000,100,ByteArray(32),byteArrayOf(),VaultItemState.COMPLETE,capturedAtEpochMillis=1700000000000)
  var updates=0
  val originalHash=item.plaintextSha256.copyOf()
  compose.setContent {PrivateGalleryTheme {
   val activity=checkNotNull(androidx.activity.compose.LocalActivity.current)
   hostActivity=activity
   val secureValue=secure // Observe during composition, before the deferred SideEffect.
   DisposableEffect(activity) {
    val old=activity.window.attributes.flags
    onDispose {activity.window.setFlags(old and android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   }
   SideEffect {activity.window.setFlags(if(secureValue)android.view.WindowManager.LayoutParams.FLAG_SECURE else 0,android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   MediaDetailsDialog("picker","Hidden Vault",{_,done->done(Result.success(item))},{_,time,zone,done->
    item=item.copy(adjustedTakenAtEpochMillis=time,adjustedTimeZone=zone);updates++;done(Result.success(Unit))
   },null,{},{})
  }}
  // The historical method name stays stable for release evidence. The app now owns
  // both Compose dialogs; inspect the actual focused dialog window, not a fake callback.
  fun assertPicker(tag:String,expected:Boolean) {
   compose.onNodeWithTag(tag).assertExists()
   val focused=object:org.hamcrest.TypeSafeMatcher<androidx.test.espresso.Root>() {
    override fun describeTo(description:org.hamcrest.Description){description.appendText("focused app dialog")}
    override fun matchesSafely(root:androidx.test.espresso.Root)=root.decorView.hasWindowFocus()
   }
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
    .inRoot(org.hamcrest.Matchers.allOf(androidx.test.espresso.matcher.RootMatchers.isDialog(),focused)).check {view,failure ->
     if(failure!=null)throw failure
     val flags=(view.rootView.layoutParams as android.view.WindowManager.LayoutParams).flags
     assertEquals(expected,flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
  }
  fun selectDate() {
   val locale=hostActivity.resources.configuration.locales[0]
   val pattern=android.text.format.DateFormat.getBestDateTimePattern(locale,"yMd")
   val order=pattern.filter {it in "yMd"}.fold(""){result,c->if(c in result)result else result+c}
   val digits=order.map {when(it){'y'->"2024";'M'->"06";else->"06"}}.joinToString("")
   compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("media-date-picker")))
    .performScrollTo().performTextReplacement(digits)
   compose.onNodeWithText("Next").assertIsEnabled().performClick()
  }
  for(value in listOf(true,false)) {
   compose.runOnIdle {secure=value}
   compose.waitForIdle()
   compose.runOnIdle {assertEquals("Fixture must apply owner policy before opening a picker",value,hostActivity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
   val before=item;val previousUpdates=updates
   // Cancelling either themed stage must not publish adjusted metadata.
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker("media-date-picker",value)
   compose.onNodeWithText("Cancel").performClick()
   compose.runOnIdle {assertEquals(before,item);assertEquals(previousUpdates,updates)}
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker("media-date-picker",value)
   selectDate()
   assertPicker("media-time-picker",value)
   compose.onNodeWithText("Cancel").performClick()
   compose.runOnIdle {assertEquals(before,item);assertEquals(previousUpdates,updates)}
   // Confirming date then time must commit exactly one metadata-only update.
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker("media-date-picker",value)
   selectDate()
   assertPicker("media-time-picker",value)
   compose.onNodeWithTag("media-hour").performScrollTo().performTextReplacement("13")
   compose.onNodeWithTag("media-minute").performScrollTo().performTextReplacement("42")
   compose.onNodeWithText("Save date").performClick()
   compose.runOnIdle {
    assertEquals(previousUpdates+1,updates)
    val selected=java.time.Instant.ofEpochMilli(checkNotNull(item.adjustedTakenAtEpochMillis)).atZone(java.time.ZoneId.of(item.adjustedTimeZone))
    assertEquals(java.time.LocalDateTime.of(2024,6,6,13,42),selected.toLocalDateTime())
    assertEquals(before.capturedAtEpochMillis,item.capturedAtEpochMillis)
    assertArrayEquals(originalHash,item.plaintextSha256)
   }
  }
 }
 @Test fun restoreOriginalDateRefreshesDetailsWithoutChangingMediaHash() {
  val hash=ByteArray(32){7}
  var item=VaultItem("id","image/jpeg","private.jpg",1000,100,hash,byteArrayOf(),VaultItemState.COMPLETE,capturedAtEpochMillis=1700000000000,adjustedTakenAtEpochMillis=1800000000000,adjustedTimeZone="UTC")
  var changes=0
  compose.setContent {PrivateGalleryTheme {MediaDetailsDialog("id","Hidden Vault",{_,done->done(Result.success(item))},{_,time,zone,done->item=item.copy(adjustedTakenAtEpochMillis=time,adjustedTimeZone=zone);done(Result.success(Unit))},null,{changes++},{})}}
  compose.onNodeWithText("Date Taken · Adjusted").assertExists()
  compose.onNodeWithText("Restore Original Date").performScrollTo().performClick()
  compose.onNodeWithText("Date Taken · Adjusted").assertDoesNotExist()
  compose.runOnIdle {assertNull(item.adjustedTakenAtEpochMillis);assertEquals(1700000000000L,item.capturedAtEpochMillis);assertArrayEquals(hash,item.plaintextSha256);assertEquals(1,changes)}
 }
}
