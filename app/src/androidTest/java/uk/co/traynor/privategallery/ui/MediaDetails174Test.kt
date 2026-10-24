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
  val item=VaultItem("picker","image/jpeg","picker.jpg",1000,100,ByteArray(32),byteArrayOf(),VaultItemState.COMPLETE,capturedAtEpochMillis=1700000000000)
  compose.setContent {PrivateGalleryTheme {
   val activity=checkNotNull(androidx.activity.compose.LocalActivity.current)
   hostActivity=activity
   val secureValue=secure // Observe during composition, before the deferred SideEffect.
   DisposableEffect(activity) {
    val old=activity.window.attributes.flags
    onDispose {activity.window.setFlags(old and android.view.WindowManager.LayoutParams.FLAG_SECURE,android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   }
   SideEffect {activity.window.setFlags(if(secureValue)android.view.WindowManager.LayoutParams.FLAG_SECURE else 0,android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   MediaDetailsDialog("picker","Hidden Vault",{_,done->done(Result.success(item))},{_,_,_,done->done(Result.success(Unit))},null,{},{})
  }}
  fun assertPicker(type:Class<out android.view.View>,expected:Boolean) {
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(type))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure->
     if(failure!=null)throw failure
     val flags=(view.rootView.layoutParams as android.view.WindowManager.LayoutParams).flags
     assertEquals(expected,flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
  }
  for(value in listOf(true,false)) {
   compose.runOnIdle {secure=value}
   compose.waitForIdle()
   compose.runOnIdle {assertEquals("Fixture must apply owner policy before opening a picker",value,hostActivity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker(android.widget.DatePicker::class.java,value)
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(android.R.id.button1))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click())
   assertPicker(android.widget.TimePicker::class.java,value)
   androidx.test.espresso.Espresso.pressBack()
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
