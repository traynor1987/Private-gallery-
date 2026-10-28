package uk.co.traynor.privategallery.ui

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.browser.v2.BrowserVpnGate
import uk.co.traynor.privategallery.core.browser.v2.NoopBrowserV2Listener
import java.util.concurrent.atomic.AtomicBoolean

/** Actual trusted DocumentsUI handoff/cancel; no file is selected or website contacted.
 * ComponentActivity isolates Browser UI lifecycle, not MainActivity authentication or OEM policy. */
class BrowserSystemPicker1762Test {
    private val compose=createAndroidComposeRule<ComponentActivity>()
    private val viewport=BrowserViewportRule {392 to 840}
    @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val automation get()=instrumentation.uiAutomation
    private val mounted=mutableStateOf(true)
    private lateinit var session:BrowserV2Session
    private var previousPolicy:BrowserUploadPolicy?=null
    private var pickerPackage:String?=null

    private class Params:WebChromeClient.FileChooserParams() {
        override fun getMode()=MODE_OPEN
        override fun getAcceptTypes()=arrayOf("image/png")
        override fun getPermissionMode()=PERMISSION_MODE_READ
        override fun isCaptureEnabled()=false
        override fun getTitle():CharSequence?=null
        override fun getFilenameHint():String?=null
        override fun createIntent()=android.content.Intent()
    }
    private fun await(message:String,timeout:Long=10_000,predicate:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<deadline) {
            if(predicate())return
            SystemClock.sleep(50)
        }
        assertTrue(message,predicate())
    }
    private fun main(predicate:()->Boolean):Boolean {
        var result=false
        instrumentation.runOnMainSync {result=predicate()}
        return result
    }
    private fun nodes(root:AccessibilityNodeInfo):List<AccessibilityNodeInfo> {
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?){if(node==null)return;result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(root);return result
    }
    /** Fresh exact accessibility label, bounded to its current active window, then real touch. */
    private fun touchChooseDevice(targetPackage:String) {
        val bounds=Rect()
        var window=-1
        await("Visible device choice in target's active window") {
            val root=automation.rootInActiveWindow ?: return@await false
            if(root.packageName?.toString()!=targetPackage)return@await false
            val node=nodes(root).firstOrNull {it.isVisibleToUser && it.isEnabled && it.text?.toString()=="Choose from device"} ?: return@await false
            val visible=Rect();root.getBoundsInScreen(visible);node.getBoundsInScreen(bounds)
            window=root.windowId
            !bounds.isEmpty && bounds.intersect(visible)
        }
        // Re-read immediately before touch so a stale dialog/window cannot authorize it.
        val root=checkNotNull(automation.rootInActiveWindow)
        assertEquals(targetPackage,root.packageName?.toString());assertEquals(window,root.windowId)
        val fresh=nodes(root).single {it.isVisibleToUser && it.isEnabled && it.text?.toString()=="Choose from device"}
        fresh.getBoundsInScreen(bounds)
        val visible=Rect();root.getBoundsInScreen(visible)
        assertTrue("Device choice intersects its active window",!bounds.isEmpty && bounds.intersect(visible))
        val downAt=SystemClock.uptimeMillis()
        for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(downAt,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source=InputDevice.SOURCE_TOUCHSCREEN
            try {assertTrue("System touch injected",automation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun pickerVisible()=pickerPackage!=null && automation.rootInActiveWindow?.packageName?.toString()==pickerPackage
    private fun returnWithSystemBack() {
        // A remembered DocumentsUI directory can require a parent navigation before cancellation.
        repeat(5) {
            if(!pickerVisible())return
            assertTrue("OS Back delivered to trusted picker",automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            val until=SystemClock.elapsedRealtime()+1_000
            while(pickerVisible() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(50)
        }
    }
    @Suppress("DEPRECATION") private fun grantedAndroidPermissions():Set<String> {
        val context=instrumentation.targetContext
        val info=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS)
        val flags=info.requestedPermissionsFlags ?: return emptySet()
        return info.requestedPermissions.orEmpty().filterIndexed {index,_->flags[index] and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED!=0}.toSet()
    }
    private fun persistedGrants()=instrumentation.targetContext.contentResolver.persistedUriPermissions.map {
        Triple(it.uri,it.isReadPermission,it.isWritePermission)
    }.toSet()

    @After fun cleanup() {
        returnWithSystemBack()
        if(::session.isInitialized) {
            instrumentation.runOnMainSync {mounted.value=false}
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {session.destroyAll()}
        }
        previousPolicy?.let {BrowserUploadPreference.write(instrumentation.targetContext,it)}
    }

    @Test fun trustedSystemPickerActuallyStopsOwnerAndBackReturnsOneCancellation() {
        val base=compose.activity
        previousPolicy=BrowserUploadPreference.read(base)
        BrowserUploadPreference.write(base,BrowserUploadPolicy.VAULT_AND_DEVICE)
        val trusted=checkNotNull(BrowserDeviceDocumentContract(false).createIntent(base,arrayOf("image/png")).component)
        pickerPackage=trusted.packageName
        val permissionsBefore=grantedAndroidPermissions();val persistedBefore=persistedGrants()
        val stopped=AtomicBoolean()
        val received=mutableListOf<List<Uri>?>();val callbackStates=mutableListOf<Lifecycle.State>()
        instrumentation.runOnMainSync {
            base.lifecycle.addObserver(LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP)stopped.set(true)})
        }
        session=BrowserV2Session(base,BrowserVpnGate{true},NoopBrowserV2Listener)
        // The real ComponentActivity registry is inherited; no substituted launcher/result gate.
        compose.setContent {PrivateGalleryTheme {if(mounted.value)BrowserV2Home(
            session,BrowserSearchEngine.GOOGLE,{_,_->},{_,_->},false,{},emptyList(),
            {_,_,_->},{},{it(emptyList())},{it()},{},staticContentHost=true,
        )}}
        compose.runOnIdle {
            assertNotNull(session.activeWebViewOrNull())
            assertTrue(session.onShowFileChooser(session.tabs.activeTab.id,ValueCallback {
                received+=it?.toList();callbackStates+=base.lifecycle.currentState
            },Params()))
        }
        touchChooseDevice(base.packageName)
        // No Compose/Espresso calls while the external Activity owns the window.
        await("Resolved trusted system picker package is visible",15_000){pickerVisible()}
        await("Full-screen trusted picker actually stopped production UI owner") {
            stopped.get() && main {base.lifecycle.currentState==Lifecycle.State.CREATED}
        }
        assertTrue("Website callback remains pending throughout owned picker stop",main {received.isEmpty()})
        assertEquals(permissionsBefore,grantedAndroidPermissions());assertEquals(persistedBefore,persistedGrants())
        returnWithSystemBack()
        await("Owner resumed after real system picker Back",15_000) {
            main {base.lifecycle.currentState==Lifecycle.State.RESUMED && received.size==1}
        }
        instrumentation.runOnMainSync {
            assertEquals(listOf<List<Uri>?>(null),received)
            assertEquals(listOf(Lifecycle.State.RESUMED),callbackStates)
            assertFalse(base.isFinishing)
        }
        assertFalse("Picker no longer owns active window",pickerVisible())
        assertEquals("Cancel cannot add Android permission grants",permissionsBefore,grantedAndroidPermissions())
        assertEquals("Cancel cannot persist any URI grant",persistedBefore,persistedGrants())
        // Drain queued main-thread work and prove cancellation was not delivered twice.
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {assertEquals(1,received.size);assertNull(received.single())}
    }
}
