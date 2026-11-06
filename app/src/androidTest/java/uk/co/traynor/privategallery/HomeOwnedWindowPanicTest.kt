package uk.co.traynor.privategallery

import android.content.Context
import android.content.ContextWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.home2.PanicHomeDropdownMenu
import uk.co.traynor.privategallery.ui.playerActivity

/** Actual MainActivity and owned windows. System input never calls panicExit directly. */
class HomeOwnedWindowPanicTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val activity get()=compose.activity
    private val owner get()=ViewModelProvider(activity)[ProtectedSessionState::class.java]
    @Suppress("UNCHECKED_CAST") private fun <T> state(name:String)=MainActivity::class.java.getDeclaredField(name+"\$delegate").apply {isAccessible=true}.get(activity) as MutableState<T>
    @Suppress("UNCHECKED_CAST") private fun bindings()=MainActivity::class.java.getDeclaredField("ownedPanicWindows").apply {isAccessible=true}.get(activity) as Map<Window,*>

    @Test fun systemThreeFingerTouchInHiddenQuickPanelLocksBothSpacesAndRequiresPrimaryPin()=withHiddenHome {
        compose.onNodeWithContentDescription("Open Quick Panel").performClick()
        compose.onNodeWithTag("quick-overlay").assertIsDisplayed()
        compose.waitUntil(10_000){compose.runOnIdle {bindings().isNotEmpty()}}
        val old=compose.runOnIdle {owner.hidden.session.authentication()}
        injectThreeFingers(compose.runOnIdle {focusedRoot()})
        compose.runOnIdle {assertRevoked(old)}
        assertPinRequired()
    }

    @Test fun drawerWindowUsesOneSharedCallbackAndConcealsSynchronouslyBeforeRecomposition()=withHiddenHome {
        compose.onNodeWithTag("open-apps").performClick()
        compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
        compose.waitUntil(10_000){compose.runOnIdle {bindings().isNotEmpty()}}
        lateinit var window:Window;lateinit var original:Window.Callback
        compose.runOnIdle {
            window=bindings().keys.last();val installed=window.callback
            val binding=checkNotNull(bindings()[window])
            original=binding.javaClass.getDeclaredField("original").apply {isAccessible=true}.get(binding) as Window.Callback
            val nested=activity.bindOwnedPanicWindow(window)
            assertSame("Nested content must not wrap the same native callback twice",installed,window.callback)
            nested.close();assertSame(installed,window.callback)
            val old=owner.hidden.session.authentication()
            val down=android.os.SystemClock.uptimeMillis()
            for((action,count) in listOf(MotionEvent.ACTION_DOWN to 1,(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 3)) {
                event(down,action,count,100f,100f).let {try {window.callback.dispatchTouchEvent(it)} finally {it.recycle()}}
            }
            assertTrue("Native curtain is immediate, before Compose can dispose the Hidden sheet",activity.privacyCoverVisible())
            assertRevoked(old)
            event(down,MotionEvent.ACTION_CANCEL,1,100f,100f).let {try {window.callback.dispatchTouchEvent(it)} finally {it.recycle()}}
        }
        assertPinRequired()
        compose.runOnIdle {assertTrue(bindings().isEmpty());assertSame("Disposed bridge restores the original callback",original,window.callback)}
    }

    @Test fun popupSystemThreeFingerTouchCannotLeakAChildClickAfterPanic()=withHiddenHome {
        var shown by mutableStateOf(true);var childClicks=0
        lateinit var overlay:ComposeView
        compose.runOnIdle {
            overlay=ComposeView(activity).apply {setContent {
                PanicHomeDropdownMenu(shown,{shown=false}) {
                    DropdownMenuItem(text={Text("Synthetic child action")},onClick={childClicks++;shown=false})
                }
            }}
            activity.addContentView(overlay,android.view.ViewGroup.LayoutParams(-1,-1))
        }
        try {
            compose.onNodeWithText("Synthetic child action").assertIsDisplayed()
            val old=compose.runOnIdle {owner.hidden.session.authentication()}
            injectThreeFingers(compose.runOnIdle {focusedRoot()})
            compose.runOnIdle {assertRevoked(old);assertEquals("Consumed gesture tail must not click through the popup",0,childClicks);shown=false}
            assertPinRequired()
        } finally {compose.runOnIdle {shown=false;overlay.disposeComposition();(overlay.parent as? android.view.ViewGroup)?.removeView(overlay)}}
    }

    private fun assertRevoked(old:uk.co.traynor.privategallery.core.hidden.HiddenSession.Authentication) {
        assertFalse(owner.hidden.session.isAuthenticationCurrent(old));assertFalse(owner.hidden.session.isUnlocked)
        assertFalse(owner.hidden.selected);assertFalse(owner.session.isUnlocked);assertNull(owner.key)
        assertEquals(Route.LOCK,state<Route>("route").value);assertEquals(Route.LOCK,owner.route)
        assertFalse(activity.novaCredentialsRequested);assertTrue(owner.automaticBiometricPromptAttempted)
    }
    private fun assertPinRequired() {
        compose.onNodeWithTag("nova-protected-lock").assertIsDisplayed()
        compose.onNodeWithTag("preview-home").assertDoesNotExist()
        compose.onNodeWithTag("enter-novaos").performClick()
        compose.onNodeWithText("Enter passcode").assertIsDisplayed()
        compose.runOnIdle {assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.session.isUnlocked)}
    }
    private fun focusedRoot():View = android.view.inspector.WindowInspector.getGlobalWindowViews().last {
        it.isAttachedToWindow && it.hasWindowFocus() && it.context.playerActivity()===activity
    }
    private fun injectThreeFingers(root:View) {
        val point=compose.runOnIdle {IntArray(2).also {root.getLocationOnScreen(it)}.let {it[0]+root.width/2f to it[1]+root.height/2f}}
        val down=android.os.SystemClock.uptimeMillis()
        for((action,count) in listOf(MotionEvent.ACTION_DOWN to 1,(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 3,(MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 3,(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,MotionEvent.ACTION_UP to 1)) {
            event(down,action,count,point.first,point.second).let {try {assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(it,true))} finally {it.recycle()}}
        }
        compose.waitForIdle()
    }
    private fun event(down:Long,action:Int,count:Int,x:Float,y:Float):MotionEvent = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,
        Array(count){index->MotionEvent.PointerProperties().apply {id=index;toolType=MotionEvent.TOOL_TYPE_FINGER}},
        Array(count){index->MotionEvent.PointerCoords().apply {this.x=x+(index-1)*12;this.y=y;pressure=1f;size=1f}},0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)

    private fun withHiddenHome(test:()->Unit) {
        assertTrue("Never run this synthetic fixture against production",activity.packageName.endsWith(".hiddenprototype"))
        val root=File(activity.cacheDir,"home-window-panic-${UUID.randomUUID()}").apply {mkdirs()}
        val prefix="panic-window-${UUID.randomUUID()}"
        val preferences=mutableSetOf<String>()
        val context=object:ContextWrapper(activity) {
            override fun getNoBackupFilesDir()=File(root,"private").apply {mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("$prefix-$name",mode).also {preferences+="$prefix-$name"}
        }
        val keysField=MainActivity::class.java.getDeclaredField("keys").apply {isAccessible=true}
        val priorKeys=keysField.get(activity);val keys=PinVaultKeyStore(context);val key=keys.create("613842".toCharArray())
        val panic=activity.getSharedPreferences("privacy-shield",Context.MODE_PRIVATE)
        val names=listOf("enabled","three-finger","quick-lock");val priorPanic=names.associateWith {panic.all[it]}
        val priorHide=state<Boolean>("hideContent").value;val priorBiometric=state<Boolean>("biometricEnabled").value
        val isolated=listOf("home2","home1710").map {File(activity.noBackupFilesDir,"evidence-state/$it")}
        val saved=isolated.associateWith {directory->
            if(!directory.exists())null else File(root,"saved-${directory.name}").also {check(directory.renameTo(it)){"Preserve existing synthetic Home files before this fixture"}}
        }
        try {
            compose.runOnIdle {
                check(panic.edit().putBoolean("enabled",true).putBoolean("three-finger",true).putBoolean("quick-lock",false).commit())
                keysField.set(activity,keys);state<Boolean>("hideContent").value=false;state<Boolean>("biometricEnabled").value=false
                owner.key=key;owner.session.unlock()
                owner.hidden.select();assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){79}));owner.hidden.authenticated=true
                state<Route>("route").value=Route.VAULT;activity.privacySettingsChanged();activity.returnPrivateHome()
            }
            compose.onNodeWithTag("preview-home").assertIsDisplayed()
            test()
        } finally {
            compose.runOnIdle {owner.hidden.leave();owner.session.lock();owner.key=null;owner.homeVisible=false;activity.closeNovaCredentials();keysField.set(activity,priorKeys);state<Route>("route").value=Route.LOCK}
            compose.waitForIdle()
            isolated.forEach {it.deleteRecursively();saved[it]?.let {backup->check(backup.renameTo(it))}}
            compose.runOnIdle {state<Boolean>("hideContent").value=priorHide;state<Boolean>("biometricEnabled").value=priorBiometric}
            panic.edit().also {editor->names.forEach {name->val value=priorPanic[name];if(value==null)editor.remove(name)else editor.putBoolean(name,value as Boolean)}}.commit()
            preferences.forEach {activity.deleteSharedPreferences(it)};key.fill(0);root.deleteRecursively()
        }
    }
}
