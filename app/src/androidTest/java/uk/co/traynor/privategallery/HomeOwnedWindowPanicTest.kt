@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package uk.co.traynor.privategallery

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RectF
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
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

    @Test fun backupOwnedWindowThreeFingerPanicRevokesBothSpaces()=withHiddenHome {
        compose.onNodeWithTag("open-apps").performClick()
        compose.onNodeWithTag("app-backup").performScrollTo().performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(10_000){compose.runOnIdle{bindings().isNotEmpty()}}
        val old=compose.runOnIdle{owner.hidden.session.authentication()}
        injectThreeFingers(compose.runOnIdle{focusedRoot()})
        compose.runOnIdle{assertRevoked(old)}
        assertPinRequired()
        compose.onNodeWithTag("nova-backup").assertDoesNotExist()
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
        var old:uk.co.traynor.privategallery.core.hidden.HiddenSession.Authentication?=null
        val diagnostic=JSONObject().put("fixture","synthetic_hidden_home_popup").put("stage","created")
        val nativeEvents=JSONArray()
        diagnostic.put("nativeEventsAtPopupBoundary",nativeEvents)
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val originalServiceInfo=automation.serviceInfo
        val originalFlags=originalServiceInfo.flags
        lateinit var overlay:ComposeView
        compose.runOnIdle {
            overlay=ComposeView(activity).apply {setContent {
                val observedInput=Modifier.composed {
                    val actualContext=LocalContext.current
                    this.motionEventSpy {event ->
                        // Observation only: the production modifier remains the sole Panic gate.
                        val arrival=JSONObject().put("actionMasked",event.actionMasked).put("actionIndex",event.actionIndex)
                            .put("pointerCount",event.pointerCount).put("downTime",event.downTime).put("eventTime",event.eventTime)
                            .put("receivedUptime",android.os.SystemClock.uptimeMillis())
                            .put("localContextClass",actualContext.javaClass.name)
                            .put("contextOwnsActualActivity",actualContext.playerActivity()===activity)
                            .put("rawX",event.rawX).put("rawY",event.rawY)
                            .put("gatesAtArrival",panicGateDiagnostic(old))
                        val pointers=JSONArray()
                        for(index in 0 until event.pointerCount) pointers.put(JSONObject().put("id",event.getPointerId(index))
                            .put("x",event.getX(index)).put("y",event.getY(index)))
                        arrival.put("pointers",pointers)
                        nativeEvents.put(arrival)
                        Log.i("PGPanicFixture","Native popup input boundary: $arrival")
                    }
                }
                PanicHomeDropdownMenu(shown,{shown=false},modifier=observedInput) {
                    DropdownMenuItem(text={Text("Synthetic child action")},onClick={childClicks++;shown=false})
                }
            }}
            activity.addContentView(overlay,android.view.ViewGroup.LayoutParams(-1,-1))
        }
        try {
            originalServiceInfo.flags=originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo=originalServiceInfo
            compose.onNodeWithText("Synthetic child action").assertIsDisplayed()
            val globalBeforeSelection=systemWindowDiagnostic(automation)
            compose.runOnIdle {
                val original=popupWindowDiagnostic()
                diagnostic.put("originalBeforeSelection",original).put("globalWindowsBeforeSelection",globalBeforeSelection)
                    .put("stage","waiting_for_popup_focus")
                Log.i("PGPanicFixture","Original windows before popup target selection: $original")
            }
            compose.waitUntil(10_000){compose.runOnIdle {focusedPopupRoot()!=null}}
            val popup=compose.runOnIdle {checkNotNull(focusedPopupRoot())}
            // Compose bounds are in this popup's window. Convert them to display coordinates,
            // rather than using the center of whichever window happened to have focus first.
            val child=compose.onNodeWithText("Synthetic child action").fetchSemanticsNode().boundsInWindow
            val globalBeforeInjection=systemWindowDiagnostic(automation)
            val point=compose.runOnIdle {
                assertSame("The selected popup must still own actual window focus",popup,focusedPopupRoot())
                val screen=IntArray(2).also {popup.getLocationOnScreen(it)}
                val inWindow=IntArray(2).also {popup.getLocationInWindow(it)}
                val childOnScreen=RectF(child.left+screen[0]-inWindow[0],child.top+screen[1]-inWindow[1],
                    child.right+screen[0]-inWindow[0],child.bottom+screen[1]-inWindow[1])
                val popupOnScreen=RectF(screen[0].toFloat(),screen[1].toFloat(),(screen[0]+popup.width).toFloat(),(screen[1]+popup.height).toFloat())
                val point=childOnScreen.centerX() to childOnScreen.centerY()
                for(offset in listOf(-12f,0f,12f)) {
                    assertTrue("Every injected pointer must start inside the actual popup child",childOnScreen.contains(point.first+offset,point.second))
                    assertTrue("Every injected pointer must start inside the focused popup window",popupOnScreen.contains(point.first+offset,point.second))
                }
                old=owner.hidden.session.authentication()
                diagnostic.put("selectedRoot",windowDiagnostic(popup)).put("childScreenBounds",rectDiagnostic(childOnScreen))
                    .put("point",JSONArray().put(point.first).put(point.second))
                    .put("oldAuthBefore",owner.hidden.session.isAuthenticationCurrent(old)).put("childClicksBefore",childClicks)
                    .put("gatesBeforeInjection",panicGateDiagnostic(old)).put("globalWindowsBeforeInjection",globalBeforeInjection)
                    .put("stage","injecting")
                Log.i("PGPanicFixture","Selected popup input target: $diagnostic")
                point
            }
            injectThreeFingers(popup,point)
            val globalAfterInjection=systemWindowDiagnostic(automation)
            compose.runOnIdle {
                diagnostic.put("oldAuthAfter",owner.hidden.session.isAuthenticationCurrent(old)).put("childClicksAfter",childClicks)
                    .put("afterInjection",popupWindowDiagnostic()).put("gatesAfterInjection",panicGateDiagnostic(old))
                    .put("globalWindowsAfterInjection",globalAfterInjection).put("stage","asserting_revocation")
                assertRevoked(checkNotNull(old));assertEquals("Consumed gesture tail must not click through the popup",0,childClicks);shown=false
            }
            assertPinRequired()
            diagnostic.put("stage","passed")
        } finally {
            try {
                val globalFinally=systemWindowDiagnostic(automation)
                compose.runOnIdle {
                    diagnostic.put("oldAuthAfter",owner.hidden.session.isAuthenticationCurrent(old)).put("childClicksAfter",childClicks)
                        .put("finalWindows",popupWindowDiagnostic()).put("finalGates",panicGateDiagnostic(old))
                        .put("globalWindowsFinally",globalFinally)
                    Log.i("PGPanicFixture","Popup gesture result: $diagnostic")
                }
                val additional=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ?: File(activity.externalMediaDirs.firstOrNull() ?: activity.cacheDir,"additional_test_output").absolutePath
                val directory=File(additional,"home-window-panic61").apply {check(isDirectory||mkdirs())}
                File(directory,"popup-three-finger.json").writeText(diagnostic.toString(2))
            } finally {
                try {
                    originalServiceInfo.flags=originalFlags
                    automation.serviceInfo=originalServiceInfo
                } finally {compose.runOnIdle {shown=false;overlay.disposeComposition();(overlay.parent as? android.view.ViewGroup)?.removeView(overlay)}}
            }
        }
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
    private fun focusedPopupRoot():View? = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {
        it!==activity.window.decorView && it.isAttachedToWindow && it.hasWindowFocus() &&
            it.context.playerActivity()===activity && it.javaClass.simpleName=="PopupLayout"
    }
    private fun windowDiagnostic(root:View):JSONObject {
        val screen=IntArray(2).also {root.getLocationOnScreen(it)}
        return JSONObject().put("class",root.javaClass.name).put("activityDecor",root===activity.window.decorView)
            .put("attached",root.isAttachedToWindow).put("focused",root.hasWindowFocus())
            .put("screenBounds",JSONArray().put(screen[0]).put(screen[1]).put(screen[0]+root.width).put(screen[1]+root.height))
    }
    private fun popupWindowDiagnostic():JSONArray = JSONArray().apply {
        android.view.inspector.WindowInspector.getGlobalWindowViews().filter {it.context.playerActivity()===activity}
            .forEach {put(windowDiagnostic(it))}
    }
    private fun rectDiagnostic(rect:RectF)=JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom)
    private fun panicGateDiagnostic(old:uk.co.traynor.privategallery.core.hidden.HiddenSession.Authentication?):JSONObject {
        val preferences=activity.getSharedPreferences("privacy-shield",Context.MODE_PRIVATE)
        val accessibility=activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val gate=MainActivity::class.java.getDeclaredField("panicTouchGate").apply {isAccessible=true}.get(activity)
        val consumed=gate.javaClass.getDeclaredField("consumed").apply {isAccessible=true}.getBoolean(gate)
        return JSONObject().put("enabled",preferences.getBoolean("enabled",false))
            .put("threeFinger",preferences.getBoolean("three-finger",true)).put("touchExploration",accessibility.isTouchExplorationEnabled)
            .put("accessibilityEnabled",accessibility.isEnabled).put("panicGateConsumed",consumed)
            .put("activityResumed",state<Boolean>("activityResumed").value).put("route",state<Route>("route").value.name)
            .put("oldHiddenAuthCurrent",owner.hidden.session.isAuthenticationCurrent(old)).put("hiddenUnlocked",owner.hidden.session.isUnlocked)
            .put("hiddenSelected",owner.hidden.selected).put("primaryUnlocked",owner.session.isUnlocked)
            .put("blackScreenActive",activity.novaBlackScreenActive).put("privacyCoverVisible",activity.privacyCoverVisible())
    }
    private fun systemWindowDiagnostic(automation:UiAutomation):JSONArray=JSONArray().apply {
        for(window in automation.windows) {
            val root=window.root
            val bounds=android.graphics.Rect().also {window.getBoundsInScreen(it)}
            val entry=JSONObject().put("title",window.title?.toString() ?: JSONObject.NULL).put("type",window.type).put("layer",window.layer)
                .put("focused",window.isFocused).put("active",window.isActive).put("package",root?.packageName?.toString() ?: JSONObject.NULL)
                .put("screenBounds",JSONArray().put(bounds.left).put(bounds.top).put(bounds.right).put(bounds.bottom))
            if(root?.packageName?.toString()=="com.android.systemui") {
                val pinningNodes=JSONArray()
                for(id in listOf("screen_pinning_description","screen_pinning_text_area","screen_pinning_ok_button","screen_pinning_cancel_button","screen_pinning_buttons")) {
                    for(node in root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/$id")) {
                        val nodeBounds=android.graphics.Rect().also {node.getBoundsInScreen(it)}
                        pinningNodes.put(JSONObject().put("resourceId",node.viewIdResourceName).put("visible",node.isVisibleToUser)
                            .put("clickable",node.isClickable).put("screenBounds",JSONArray().put(nodeBounds.left).put(nodeBounds.top).put(nodeBounds.right).put(nodeBounds.bottom)))
                    }
                }
                entry.put("pinningResourceNodes",pinningNodes)
            }
            put(entry)
        }
    }
    private fun injectThreeFingers(root:View,selectedPoint:Pair<Float,Float>?=null) {
        val point=selectedPoint ?: compose.runOnIdle {IntArray(2).also {root.getLocationOnScreen(it)}.let {it[0]+root.width/2f to it[1]+root.height/2f}}
        val down=android.os.SystemClock.uptimeMillis()
        for((action,count) in listOf(MotionEvent.ACTION_DOWN to 1,(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 3,(MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 3,(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)) to 2,MotionEvent.ACTION_UP to 1)) {
            event(down,action,count,point.first,point.second).let {try {assertTrue(InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(it,true))} finally {it.recycle()}}
        }
        compose.waitForIdle()
    }
    private fun event(down:Long,action:Int,count:Int,x:Float,y:Float):MotionEvent = MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,count,
        Array(count){index->MotionEvent.PointerProperties().apply {id=index;toolType=MotionEvent.TOOL_TYPE_FINGER}},
        Array(count){index->MotionEvent.PointerCoords().apply {this.x=x+(index-1)*12;this.y=y;pressure=1f;size=1f}},0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)

    @Test fun activeBackupIoStopsAtRealMainLockPanicAndBackgroundInBothSpaces() {
        for(hidden in listOf(false,true)) for(action in listOf("lock","panic","background")) withHiddenHome(hidden) {
            val access=compose.runOnIdle {MainActivity::class.java.getDeclaredMethod("homeAccess").apply{isAccessible=true}.invoke(activity) as uk.co.traynor.privategallery.core.evidence.EvidenceAccess}
            val uri=uk.co.traynor.privategallery.core.backup.BackupDocuments62.create()
            try {
                uk.co.traynor.privategallery.core.backup.BackupCoordinator(activity,access).use{backup->backup.export(uk.co.traynor.privategallery.core.backup.SafBackupProvider(activity,uri,access),"active main recovery key".toCharArray())}
                uk.co.traynor.privategallery.core.backup.BackupDocuments62.mode(uri,"block-read")
                compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("app-backup").performScrollTo().performClick()
                compose.onNodeWithText("Restore").performScrollTo().performClick()
                uk.co.traynor.privategallery.core.backup.BackupDocumentsUi62.open(checkNotNull(uri.lastPathSegment))
                // The system picker can remove all Compose roots until its result resumes Main.
                compose.waitUntil(10000){try{compose.onAllNodesWithText("Recovery key").fetchSemanticsNodes().isNotEmpty()}catch(transition:IllegalStateException){if(transition.message?.contains("No compose hierarchies")!=true)throw transition;false}}
                compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("active main recovery key")
                val blocked=uk.co.traynor.privategallery.core.backup.BackupDocuments62.stats().getInt("blocked")
                val cancelled=uk.co.traynor.privategallery.core.backup.BackupDocuments62.stats().getInt("cancelled")
                compose.onNodeWithText("Validate and preview").performScrollTo().performClick()
                compose.waitUntil(15000){uk.co.traynor.privategallery.core.backup.BackupDocuments62.stats().getInt("blocked")>blocked}
                when(action){
                    "lock"->compose.runOnIdle{activity.lockNovaSpace()}
                    "panic"->injectThreeFingers(compose.runOnIdle{focusedRoot()})
                    else->compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
                }
                compose.waitUntil(5000){uk.co.traynor.privategallery.core.backup.BackupDocuments62.stats().getInt("cancelled")>cancelled}
                if(action=="background")compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
                compose.waitUntil(5000){compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isEmpty()}
                compose.onAllNodes(hasText("Restore preview",substring=true)).assertCountEquals(0)
                if(action=="panic")compose.runOnIdle{assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.session.isUnlocked)}
            }finally{access.close();uk.co.traynor.privategallery.core.backup.BackupDocuments62.delete(uri)}
        }
    }

    @Test fun backupControlsRemainReachableAcrossActualNarrowExpandedResizeAndRecreation()=withHiddenHome(false) {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command:String)=automation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().decodeToString()}
        val size=shell("wm size");val override=Regex("Override size: (\\d+x\\d+)").find(size)?.groupValues?.get(1)
        try {
            for((width,height,label) in listOf(Triple(360,720,"cover"),Triple(840,720,"expanded"))) {
                shell("wm size ${width}x$height")
                compose.waitForIdle()
                if(compose.onAllNodesWithTag("nova-backup").fetchSemanticsNodes().isEmpty()) {
                    compose.runOnIdle{activity.returnPrivateHome()};compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("app-backup").performScrollTo().performClick()
                }
                compose.onNodeWithText("Create backup").performScrollTo().assertIsDisplayed();compose.onNodeWithText("Restore").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Nova Backup").performScrollTo();ReleaseScreenshots62.capture(compose.onNodeWithTag("nova-backup"),"backup-primary-$label")
                compose.onAllNodes(hasText("Hidden",substring=true,ignoreCase=true)).assertCountEquals(0)
            }
            compose.activityRule.scenario.recreate();compose.waitForIdle();compose.onAllNodesWithText("I saved the recovery key").assertCountEquals(0);compose.onAllNodesWithTag("nova-backup").assertCountEquals(0)
        }finally{shell(if(override==null)"wm size reset" else "wm size $override")}
    }

    @Test fun realSettingsRestoreLocksAndReloadsHomeMindAndWidgetsBeforeFurtherEdits()=withHiddenHome(false) {
        val modelField=MainActivity::class.java.getDeclaredField("homeThemeData").apply{isAccessible=true}
        val oldModel=compose.runOnIdle {checkNotNull(modelField.get(activity)) as uk.co.traynor.privategallery.home2.PreviewData}
        compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("app-mind").performScrollTo().performClick();compose.onNodeWithTag("nova-mind").assertIsDisplayed()
        compose.onNodeWithTag("nova-home").performClick();compose.onNodeWithTag("preview-home").assertIsDisplayed()
        val sourceRoot=uk.co.traynor.privategallery.core.backup.BackupFixture62.temporary("live-settings62")
        val uri=uk.co.traynor.privategallery.core.backup.BackupDocuments62.create()
        try {
            uk.co.traynor.privategallery.core.backup.BackupFixture62(sourceRoot).use{source->
                val model=uk.co.traynor.privategallery.home2.PreviewData(source.coordinator.space,null)
                model.change{it.copy(pages=listOf(listOf("mind","nova-calculator")),dock=listOf("mind"),columns=5)}
                uk.co.traynor.privategallery.home2.MindData(source.coordinator.space).prefer("grounding")
                val widgets=uk.co.traynor.privategallery.home2.WidgetData(source.coordinator.space)
                assertTrue(widgets.save("primary",listOf(uk.co.traynor.privategallery.home2.WidgetSlot("restored-slot",0,0,0,2,2,listOf(uk.co.traynor.privategallery.home2.WidgetItem("restored-note","note","Restored synthetic note")),0))))
                source.coordinator.export(uk.co.traynor.privategallery.core.backup.SafBackupProvider(source.context,uri,source.access),"live settings recovery".toCharArray())
            }
            compose.onNodeWithTag("open-apps").performClick();compose.onNodeWithTag("app-backup").performScrollTo().performClick();compose.onNodeWithText("Restore").performScrollTo().performClick()
            uk.co.traynor.privategallery.core.backup.BackupDocumentsUi62.open(checkNotNull(uri.lastPathSegment))
            // The system picker can remove all Compose roots until its result resumes Main.
                compose.waitUntil(10000){try{compose.onAllNodesWithText("Recovery key").fetchSemanticsNodes().isNotEmpty()}catch(transition:IllegalStateException){if(transition.message?.contains("No compose hierarchies")!=true)throw transition;false}}
            compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("live settings recovery");compose.onNodeWithText("Validate and preview").performScrollTo().performClick()
            compose.waitUntil(15000){compose.onAllNodesWithText("Replace supported settings…").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Replace supported settings…").performScrollTo().performClick();compose.onNodeWithText("Replace settings").performClick()
            compose.waitUntil(15000){compose.runOnIdle{!owner.session.isUnlocked}};compose.onNodeWithTag("nova-protected-lock").assertIsDisplayed()
            // A stale model's old capability must refuse writes after successful restore.
            compose.runOnIdle{assertTrue(runCatching{oldModel.setTheme("dark")}.isFailure||oldModel.error!=null)}
            compose.onNodeWithText("Enter").performClick();"613842".forEach{compose.onNodeWithText(it.toString()).performClick()}
            compose.waitUntil(15000){compose.onAllNodesWithTag("preview-home").fetchSemanticsNodes().isNotEmpty()}
            val restored=compose.runOnIdle{modelField.get(activity) as uk.co.traynor.privategallery.home2.PreviewData};assertNotSame(oldModel,restored);assertEquals(5,restored.profile.columns);assertEquals(listOf("mind"),restored.profile.dock)
            compose.onNodeWithTag("app-mind").performClick();compose.onNodeWithTag("calm-now").performScrollTo().performClick();compose.onNodeWithTag("grounding-step").assertIsDisplayed()
            compose.onNodeWithTag("nova-home").performClick();compose.onNodeWithTag("preview-home").assertIsDisplayed()
            compose.onAllNodesWithText("Restored synthetic note").onFirst().assertExists()
            compose.runOnIdle{restored.change{it.copy(iconSize=72)};activity.lockNovaSpace()}
            compose.onNodeWithText("Enter").performClick();"613842".forEach{compose.onNodeWithText(it.toString()).performClick()}
            compose.waitUntil(15000){compose.onAllNodesWithTag("preview-home").fetchSemanticsNodes().isNotEmpty()}
            val reopened=compose.runOnIdle{modelField.get(activity) as uk.co.traynor.privategallery.home2.PreviewData};assertEquals(5,reopened.profile.columns);assertEquals(72,reopened.profile.iconSize);assertEquals(listOf("mind"),reopened.profile.dock)
        }finally{uk.co.traynor.privategallery.core.backup.BackupDocuments62.delete(uri);sourceRoot.deleteRecursively()}
    }

    @Test fun realForegroundHomeExecutesScheduledPrimaryBackupWithoutManualBackupAction()=withHiddenHome(false) {
        val access=compose.runOnIdle {MainActivity::class.java.getDeclaredMethod("homeAccess").apply{isAccessible=true}.invoke(activity) as uk.co.traynor.privategallery.core.evidence.EvidenceAccess}
        val tree=checkNotNull(uk.co.traynor.privategallery.core.backup.BackupDocuments62.call("tree").getParcelable("uri",android.net.Uri::class.java))
        var document:android.net.Uri?=null
        try {
            uk.co.traynor.privategallery.core.backup.BackupCoordinator(activity,access).use {backup->
                uk.co.traynor.privategallery.core.backup.AutomaticBackupSettings.arm(activity,backup.space,tree,"synthetic real Main automatic recovery key".toCharArray(),1,false,false)
                val prefs=activity.getSharedPreferences("nova-backup-due",0)
                check(prefs.edit().putLong("configured",System.currentTimeMillis()-86400001L).putLong("due",0).commit())
                val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cmd jobscheduler run -f ${activity.packageName} 171162").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes().decodeToString()}
                assertTrue(output,output.contains("Running job"))
                compose.waitUntil(10000){prefs.getLong("due",0)>0||backup.history().contains("Last successful backup")}
                compose.waitUntil(40000){backup.history().contains("Last successful backup")}
                val id=checkNotNull(uk.co.traynor.privategallery.core.backup.BackupDocuments62.call("latest").getString("id"))
                document=uk.co.traynor.privategallery.core.backup.BackupDocuments62.inPersistedTree(id)
                backup.preview(uk.co.traynor.privategallery.core.backup.SafBackupProvider(activity,document!!,access),"synthetic real Main automatic recovery key".toCharArray()).use {assertTrue(it.settings>0)}
                compose.onNodeWithTag("preview-home").assertIsDisplayed();compose.onAllNodesWithTag("nova-backup").assertCountEquals(0)
                assertEquals(0L,prefs.getLong("due",0));assertFalse(backup.space.backupValues().keys.any{it.contains(uk.co.traynor.privategallery.core.backup.AutomaticBackupSettings.NAME)})
                uk.co.traynor.privategallery.core.backup.AutomaticBackupSettings.disable(activity,backup.space)
            }
        } finally {access.close();document?.let{uk.co.traynor.privategallery.core.backup.BackupDocuments62.delete(it)}}
    }

    private fun withHiddenHome(hidden:Boolean=true,test:()->Unit) {
        assertTrue("Never run this synthetic fixture against production",activity.packageName.endsWith(".hiddenprototype"))
        // The real system picker backgrounds/locks Main. Obtain its persisted grant before unlocking this fixture.
        uk.co.traynor.privategallery.core.backup.BackupDocuments62.persistedTree()
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
        val schedulePrefs=activity.getSharedPreferences("nova-backup-due",0);val priorSchedule=schedulePrefs.all.toMap()
        val scheduler=activity.getSystemService(android.app.job.JobScheduler::class.java);val priorJob=scheduler.getPendingJob(171162)
        val priorHide=state<Boolean>("hideContent").value;val priorBiometric=state<Boolean>("biometricEnabled").value
        val isolated=listOf(File(activity.filesDir,"vault"),File(activity.filesDir,"evidence-state"),File(activity.noBackupFilesDir,"hidden-media-v1"),File(activity.noBackupFilesDir,"evidence-state"))
        val saved=isolated.associateWith {directory->
            if(!directory.exists())null else File(root,"saved-${java.util.UUID.randomUUID()}").also {check(directory.renameTo(it)){"Preserve existing synthetic Home files before this fixture"}}
        }
        try {
            compose.runOnIdle {
                check(panic.edit().putBoolean("enabled",true).putBoolean("three-finger",true).putBoolean("quick-lock",false).commit())
                keysField.set(activity,keys);state<Boolean>("hideContent").value=false;state<Boolean>("biometricEnabled").value=false
                owner.key=key;owner.session.unlock()
                if(hidden){owner.hidden.select();assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){79}));owner.hidden.authenticated=true}else owner.hidden.leave()
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
            scheduler.cancel(171162);if(priorJob!=null)scheduler.schedule(priorJob)
            schedulePrefs.edit().clear().also {editor->priorSchedule.forEach{(name,value)->when(value){is Boolean->editor.putBoolean(name,value);is Int->editor.putInt(name,value);is Long->editor.putLong(name,value);is String->editor.putString(name,value)}}}.commit()
            preferences.forEach {activity.deleteSharedPreferences(it)};key.fill(0);root.deleteRecursively()
        }
    }
}
