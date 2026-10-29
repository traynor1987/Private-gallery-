package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs only against the disposable emulator application; never owner data. */
class PrimaryGalleryDeviceRepair1762Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun seededPrimaryGallerySurvivesHiddenNavigationAndRevokesOnLock() {
        val activity=compose.activity
        assertTrue(BuildConfig.DEBUG)
        val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        val qemu=android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("getprop ro.kernel.qemu")).use {it.readBytes().toString(Charsets.UTF_8).trim()}
        assertEquals("Synthetic emulator only","1",qemu)
        assertEquals("Disposable test package only","uk.co.traynor.privategallery.hiddenprototype",activity.packageName)
        val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
        val root=File(activity.filesDir,"vault")
        assertTrue("Fixture must start without existing media",!root.exists() || root.walkTopDown().none {it.isFile})
        val credentials=uk.co.traynor.privategallery.core.security.PinVaultKeyStore(activity)
        assertFalse("Disposable fixture has no owner credentials",credentials.isConfigured)
        val key=credentials.create("918273".toCharArray())
        val repository=AndroidVaultRepository(activity,key)
        val bitmap=android.graphics.Bitmap.createBitmap(12,12,android.graphics.Bitmap.Config.ARGB_8888)
        val plain=java.io.ByteArrayOutputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
        bitmap.recycle()
        val item=(repository.importVerified(VaultImportSource("primary-device-repair.png","image/png",{plain.inputStream()})) as ImportResult.Imported).item
        val payload=File(root,"payloads/${item.id}.vault")
        val encrypted=payload.readBytes()
        @Suppress("UNCHECKED_CAST")
        val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply{isAccessible=true}.get(activity) as MutableState<Route>
        val select=MainActivity::class.java.getDeclaredMethod("selectGallerySource",Boolean::class.javaPrimitiveType).apply{isAccessible=true}
        fun items():List<VaultItem> {
            val latch=CountDownLatch(1);var loaded:List<VaultItem>?=null
            compose.runOnIdle {
                val callback:(List<VaultItem>)->Unit={loaded=it;latch.countDown()}
                MainActivity::class.java.getDeclaredMethod("loadItems",kotlin.jvm.functions.Function1::class.java).apply{isAccessible=true}.invoke(activity,callback)
            }
            assertTrue("Primary query completes",latch.await(10,TimeUnit.SECONDS))
            return checkNotNull(loaded)
        }
        try {
            compose.runOnIdle {state.key=key;state.session.unlock();route.value=Route.VAULT}
            assertEquals(listOf(item.id),items().map{it.id})
            compose.runOnIdle {
                state.hidden.select();assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){17}));state.hidden.authenticated=true
                state.hidden.outside();route.value=Route.GALLERY
                select.invoke(activity,true)
                assertSame("Valid Primary authority must be reused",key,state.key)
                assertTrue(state.session.isUnlocked);assertTrue(state.hidden.session.isUnlocked)
                assertTrue(state.hidden.selected);assertFalse(state.hidden.visible)
            }
            assertEquals(listOf(item.id),items().map{it.id})
            compose.runOnIdle {state.hidden.select()}
            assertTrue("Primary media cannot appear behind Hidden",items().isEmpty())
            compose.runOnIdle {state.hidden.outside();select.invoke(activity,true)}
            assertEquals(listOf(item.id),items().map{it.id})
            compose.runOnIdle {MainActivity::class.java.getDeclaredMethod("lockPrimary",Boolean::class.javaPrimitiveType).apply{isAccessible=true}.invoke(activity,false)}
            assertTrue(items().isEmpty());assertTrue(state.hidden.session.isUnlocked)
            compose.runOnIdle {
                MainActivity::class.java.getDeclaredMethod("returnToPrimaryVault").apply{isAccessible=true}.invoke(activity)
                assertFalse(state.session.isUnlocked);assertNull(state.key)
                assertTrue("Configured Primary lock must preserve Hidden timeout",state.hidden.session.isUnlocked)
                assertFalse(state.hidden.selected);assertFalse(state.hidden.visible)
                assertEquals(Route.LOCK,route.value)
            }
            assertArrayEquals("Browsing never rewrites encrypted payload",encrypted,payload.readBytes())
        } finally {
            compose.runOnIdle {state.hidden.leave();state.key=null;state.session.lock();state.primaryGallery=false;route.value=Route.LOCK}
            plain.fill(0);key.fill(0);encrypted.fill(0);root.deleteRecursively();credentials.clearFailedRestore()
        }
    }
}
