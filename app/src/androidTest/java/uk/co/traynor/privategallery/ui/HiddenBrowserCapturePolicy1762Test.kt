package uk.co.traynor.privategallery.ui

import android.app.AlertDialog
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState

/** Same production capture guard on actual Android Activity/Dialog windows. No network bypass. */
class HiddenBrowserCapturePolicy1762Test {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun waitFor(message:String,test:()->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        while(android.os.SystemClock.elapsedRealtime()<deadline){if(test())return;Thread.sleep(25)}
        assertTrue(message,test())
    }
    private fun secure(activity:ComponentActivity)=activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0
    private fun frame(guard:HiddenBrowserCaptureGuard){instrumentation.runOnMainSync{guard.compositionCommitted(guard.revision)}}
    @Test fun defaultOffAndAuthenticatedHiddenOptInRequireFreshOwnedFrame() {
        val authority=AtomicBoolean(true)
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            lateinit var activity:ComponentActivity;lateinit var guard:HiddenBrowserCaptureGuard
            scenario.onActivity {a ->activity=a;a.setContentView(TextView(a).apply{text="Synthetic private browser"})
                guard=HiddenBrowserCaptureGuard(a,{authority.get()},{fail("Window protection failed")});guard.start();guard.resume()}
            waitFor("Default-off foreground is visible but secure"){frame(guard);!guard.covered && secure(activity)}
            scenario.onActivity{guard.updatePolicy(true);assertTrue("Policy update alone cannot clear secure flag",secure(it))}
            waitFor("Authenticated opt-in clears only after actual owned frame"){frame(guard);!guard.covered && !secure(activity)}
            scenario.onActivity{authority.set(false);guard.focusChanged(true)}
            waitFor("Lost authority remains concealed and secure"){frame(guard);guard.covered && secure(activity)}
            scenario.onActivity{guard.close()}
        }
    }
    @Test fun pauseCoversActivityAndOwnedDialogAndResumeNeedsAnotherFrame() {
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            lateinit var activity:ComponentActivity;lateinit var guard:HiddenBrowserCaptureGuard;lateinit var dialog:AlertDialog
            scenario.onActivity {a ->activity=a;a.setContentView(LinearLayout(a).apply{addView(TextView(a).apply{text="Synthetic browser"})})
                guard=HiddenBrowserCaptureGuard(a,{true},{fail("Window protection failed")});guard.start();guard.updatePolicy(true)
                a.lifecycle.addObserver(object:DefaultLifecycleObserver {
                    override fun onResume(owner:LifecycleOwner){guard.resume()}
                    override fun onPause(owner:LifecycleOwner){guard.pause()}
                })
            }
            waitFor("Opt-in foreground frame"){frame(guard);!guard.covered && !secure(activity)}
            scenario.onActivity {a ->dialog=AlertDialog.Builder(a).setTitle("Owned browser prompt").setView(EditText(a).apply{hint="Response"}).setPositiveButton("OK",null).create();dialog.show()}
            waitFor("Owned dialog focus is not mistaken for background"){frame(guard);!guard.covered}
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onActivity {a ->
                assertTrue(secure(a));assertTrue(guard.covered)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,a.window.decorView.importantForAccessibility)
                assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,dialog.window!!.decorView.importantForAccessibility)
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            waitFor("Fresh resumed owned dialog frame"){frame(guard);!guard.covered && !secure(activity)}
            scenario.onActivity {dialog.dismiss();guard.close()}
        }
    }
    @Test fun hiddenPolicyCannotInheritPrimaryOptInOrLockedHiddenCredentials() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
            val settings=activity.getSharedPreferences("private-gallery-settings",0)
            val priorPrimary=settings.getString("allow-screenshots",null)
            val hidden=HiddenPreferences(activity);val priorHidden=hidden.allowScreenshots
            val apply=MainActivity::class.java.getDeclaredMethod("applyAllowScreenshots",Boolean::class.javaPrimitiveType).apply{isAccessible=true}
            try {
                state.hidden.selected=false;state.session.unlock();apply.invoke(activity,true)
                assertFalse("Primary opt-in grants no Hidden capture policy",activity.hiddenBrowserCaptureAllowed())
                assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){71}))
                state.hidden.selected=true;apply.invoke(activity,false)
                assertFalse("Hidden defaults remain independent",activity.hiddenBrowserCaptureAllowed())
                apply.invoke(activity,true);assertTrue(activity.hiddenBrowserCaptureAllowed())
                state.hidden.session.lock();assertFalse("Locked Hidden has no capture capability",activity.hiddenBrowserCaptureAllowed())
            }finally{
                hidden.allowScreenshots=priorHidden
                settings.edit().apply{if(priorPrimary==null)remove("allow-screenshots")else putString("allow-screenshots",priorPrimary)}.commit()
                state.hidden.leave();state.session.lock()
            }
        }}
    }
    @Test fun parentSelectedThemeIsAuthoritativeAndIndependentOfHiddenCaptureOptIn() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val settings=activity.getSharedPreferences("private-gallery-settings",0)
            val prior=settings.getString("app-theme",null)
            val apply=MainActivity::class.java.getDeclaredMethod("applyTheme",uk.co.traynor.privategallery.core.ui.AppTheme::class.java).apply{isAccessible=true}
            try {
                for(theme in listOf(uk.co.traynor.privategallery.core.ui.AppTheme.DARK,uk.co.traynor.privategallery.core.ui.AppTheme.LIGHT)) {
                    apply.invoke(activity,theme)
                    assertEquals(theme,activity.hiddenBrowserTheme())
                    assertFalse("Theme selection grants no capture authority",activity.hiddenBrowserCaptureAllowed())
                }
            }finally{apply.invoke(activity,uk.co.traynor.privategallery.core.ui.ThemePreference.decode(prior))}
        }}
    }

    @Test fun hiddenOptInNeverClearsAnExplicitSecureCredentialWindow() {
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            lateinit var activity:ComponentActivity;lateinit var guard:HiddenBrowserCaptureGuard;lateinit var dialog:AlertDialog
            scenario.onActivity {a ->activity=a;a.setContentView(TextView(a).apply{text="Synthetic browser"})
                guard=HiddenBrowserCaptureGuard(a,{true},{fail("Window protection failed")});guard.start();guard.updatePolicy(true);guard.resume()}
            waitFor("Opt-in foreground frame"){frame(guard);!guard.covered && !secure(activity)}
            scenario.onActivity {a ->dialog=AlertDialog.Builder(a).setTitle("Explicit secure credential").setMessage("Synthetic only").setPositiveButton("Close",null).create()
                dialog.window!!.addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show()}
            waitFor("Owned secure dialog frame restores Activity only"){frame(guard);!guard.covered && !secure(activity)}
            scenario.onActivity {assertTrue(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0);dialog.dismiss();guard.close()}
        }
    }

    @Test fun actualImeOverOwnedPromptDoesNotMasqueradeAsBackground() {
        val automation=instrumentation.uiAutomation
        fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use{it.readBytes().toString(Charsets.UTF_8).trim()}
        val previous=shell("settings get secure show_ime_with_hard_keyboard")
        val oldFlags=automation.serviceInfo.flags
        try {
            shell("settings put secure show_ime_with_hard_keyboard 1")
            val info=automation.serviceInfo;info.flags=oldFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.serviceInfo=info
            ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                lateinit var activity:ComponentActivity;lateinit var guard:HiddenBrowserCaptureGuard;lateinit var dialog:AlertDialog;lateinit var input:EditText
                scenario.onActivity {a ->activity=a;a.setContentView(TextView(a).apply{text="Synthetic browser"})
                    guard=HiddenBrowserCaptureGuard(a,{true},{fail("Window protection failed")});guard.start();guard.updatePolicy(true);guard.resume()}
                waitFor("Opt-in foreground frame"){frame(guard);!guard.covered && !secure(activity)}
                scenario.onActivity {a ->input=EditText(a).apply{hint="Synthetic response"};dialog=AlertDialog.Builder(a).setTitle("Owned prompt").setView(input).setPositiveButton("Close",null).create();dialog.show();input.requestFocus()}
                waitFor("Owned prompt has focus"){frame(guard);dialog.window!!.decorView.hasWindowFocus() && !guard.covered}
                scenario.onActivity {it.getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)}
                waitFor("Actual keyboard window visible"){automation.windows.any{it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}}
                waitFor("Keyboard retains owned foreground policy"){frame(guard);!guard.covered && !secure(activity)}
                scenario.onActivity {dialog.dismiss();guard.close()}
            }
        }finally{
            if(previous=="null")shell("settings delete secure show_ime_with_hard_keyboard")
            else {require(previous in setOf("0","1"));shell("settings put secure show_ime_with_hard_keyboard $previous")}
            val info=automation.serviceInfo;info.flags=oldFlags;automation.serviceInfo=info
        }
    }

}
