package uk.co.traynor.privategallery.ui

import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.core.camera.*
import java.io.File
import java.util.UUID

/** TEST ONLY package and isolated synthetic files. Never owner media. */
class VaultCameraUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun blackPhotoTapsVolumeAndNavigationGestures() = noPreviewPhotoControls(false)
    @Test fun readingPhotoTapsVolumeAndNavigationGestures() = noPreviewPhotoControls(true)
    private fun noPreviewPhotoControls(reading: Boolean) {
        val base=compose.activity.applicationContext
        android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${base.packageName} android.permission.CAMERA")).use {it.readBytes()}
        val root=File(base.cacheDir,"camera-ui-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(base){override fun getApplicationContext()=this;override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()}}
        val access=VaultCaptureAccess(context,null,{ByteArray(32){2}},{true})
        val controller=VaultCameraController(context,access)
        var exited=false
        try {
            compose.runOnIdle {compose.activity.setContent {PrivateGalleryTheme {
                VaultCameraScreen(controller){controller.revoke();exited=true}
            }}}
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Camera modes and settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Camera modes and settings").performClick()
            compose.runOnIdle { assertFalse(controller.entered); assertFalse(controller.bound) }
            compose.onNodeWithText(if(reading) "Reading View Camera" else "Black-Screen Camera").performScrollTo().performClick()
            compose.waitUntil(10_000){compose.onAllNodes(hasText("Open camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Open camera").performScrollTo().performClick()
            compose.waitUntil(30_000){controller.bound}
            val surface=compose.onNodeWithContentDescription(if(reading) "Take photo from reading area" else "Black photo capture")
            if(reading) surface.performScrollTo()
            surface.performTouchInput {swipeLeft()}
            compose.runOnIdle {assertEquals(0,controller.completedRevision);assertTrue(access.journal.entries().isEmpty())}
            surface.performTouchInput {click();click()}
            compose.waitUntil(30_000){controller.completedRevision==1}
            assertEquals(1,access.recordings().size)
            // Real public Activity key callbacks, including key-repeat rejection.
            val before=controller.completedRevision
            compose.waitUntil(2_000){!controller.busy}
            android.os.SystemClock.sleep(550)
            compose.runOnIdle {
                val activity=compose.activity
                assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN)))
                assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_DOWN,1)))
                assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_VOLUME_DOWN,KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_VOLUME_DOWN)))
            }
            compose.waitUntil(30_000){controller.completedRevision>before}
            assertEquals(2,access.recordings().size)
            compose.onNodeWithContentDescription(if(reading) "Exit Reading View Camera" else "Stop and exit camera").performClick()
            compose.runOnIdle {assertTrue(exited);assertFalse(access.allowed())}
        } finally {compose.runOnIdle{controller.revoke();compose.activity.cameraVolumeAction=null};root.deleteRecursively()}
    }

    @Test fun activityForegroundLossLocksBothVaultsAndNeverReopensHiddenCamera() {
        val state=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
        @Suppress("UNCHECKED_CAST")
        val camera=MainActivity::class.java.getDeclaredField("vaultCamera\$delegate").apply{isAccessible=true}.get(compose.activity) as MutableState<VaultCameraController?>
        @Suppress("UNCHECKED_CAST")
        val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(compose.activity) as MutableState<Route>
        try {
            compose.runOnIdle {
                state.key=ByteArray(32){9};state.session.unlock();state.hidden.select()
                state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){7})
                route.value=Route.VAULT
                compose.activity.openVaultCamera(state.hidden.session)
                assertTrue(checkNotNull(camera.value).enter())
            }
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertNull(camera.value);assertFalse(state.session.isUnlocked);assertFalse(state.hidden.session.isUnlocked)
            assertFalse(state.hidden.selected);assertNull(state.key)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.runOnIdle {
                assertNull(camera.value);assertFalse(state.hidden.selected)
                assertTrue(route.value==Route.LOCK||route.value==Route.SETUP)
            }
        } finally {compose.runOnIdle {camera.value?.revoke();camera.value=null;state.hidden.leave();state.key?.fill(0);state.key=null;state.session.lock()}}
    }
}
