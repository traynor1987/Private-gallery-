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
  fun assertPicker(type:Class<out android.view.View>,expected:Boolean) {
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(type))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure->
     if(failure!=null)throw failure
     val flags=(view.rootView.layoutParams as android.view.WindowManager.LayoutParams).flags
     assertEquals(expected,flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
    }
  }
  // Espresso's InputManager injection can arrive during a native window fade on API37.
  // Public UiAutomation injection synchronises WindowManager animations before ACTION_DOWN.
  // Still dispatch real input; never invoke Button.performClick() or a production callback.
  fun clickNativeButton(id:Int) {
   var center=android.graphics.Point()
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(id))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure->
     if(failure!=null)throw failure
     assertTrue(view.isShown && view.isEnabled && view.isClickable)
     val location=IntArray(2);view.getLocationOnScreen(location)
     center=android.graphics.Point(location[0]+view.width/2,location[1]+view.height/2)
    }
   val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
   val down=android.os.SystemClock.uptimeMillis()
   for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
    val event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,center.x.toFloat(),center.y.toFloat(),0)
    event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
    try {assertTrue("Native picker input injection must succeed",automation.injectInputEvent(event,true))}
    finally {event.recycle()}
   }
   compose.waitForIdle()
  }
  fun selectDate() {
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.DatePicker::class.java))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure->
     if(failure!=null)throw failure
     (view as android.widget.DatePicker).updateDate(2024,5,6)
    }
   clickNativeButton(android.R.id.button1)
  }
  for(value in listOf(true,false)) {
   compose.runOnIdle {secure=value}
   compose.waitForIdle()
   compose.runOnIdle {assertEquals("Fixture must apply owner policy before opening a picker",value,hostActivity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
   val before=item;val previousUpdates=updates
   // Cancelling either native stage must not publish adjusted metadata.
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker(android.widget.DatePicker::class.java,value)
   clickNativeButton(android.R.id.button2)
   compose.runOnIdle {assertEquals(before,item);assertEquals(previousUpdates,updates)}
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker(android.widget.DatePicker::class.java,value)
   selectDate()
   assertPicker(android.widget.TimePicker::class.java,value)
   clickNativeButton(android.R.id.button2)
   compose.runOnIdle {assertEquals(before,item);assertEquals(previousUpdates,updates)}
   // Confirming date then time must commit exactly one metadata-only update.
   compose.onNodeWithText("Edit Date Taken").performScrollTo().performClick()
   assertPicker(android.widget.DatePicker::class.java,value)
   selectDate()
   assertPicker(android.widget.TimePicker::class.java,value)
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TimePicker::class.java))
    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check {view,failure->
     if(failure!=null)throw failure
     (view as android.widget.TimePicker).apply {hour=13;minute=42}
    }
   clickNativeButton(android.R.id.button1)
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
