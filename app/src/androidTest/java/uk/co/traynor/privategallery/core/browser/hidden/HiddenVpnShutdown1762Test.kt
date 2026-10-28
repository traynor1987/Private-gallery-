package uk.co.traynor.privategallery.core.browser.hidden

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Offline deletion proof uses actual kernel locks and synthetic roots, no WebView/network. */
class HiddenVpnShutdown1762Test {
    @Test fun aRetiringProcessCannotClaimOrBindAnotherLease() {
        val admission=HiddenVpnProcessAdmission()
        assertTrue(admission.enter())
        repeat(10) {assertFalse("Process bootstrap is one-shot even after session close",admission.enter())}
        assertTrue("Only a new process has fresh admission",HiddenVpnProcessAdmission().enter())
    }
    @Test fun cleanupWaitsForBothOwnersThenErasesOnlyDedicatedRoots() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox=File(base.noBackupFilesDir,"vpn-shutdown-test-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base) {
            override fun getNoBackupFilesDir()=File(sandbox,"private").apply {mkdirs()}
            override fun getCacheDir()=File(sandbox,"cache").apply {mkdirs()}
        }
        val primary=File(sandbox,"primary-cookie").apply {parentFile!!.mkdirs();writeText("primary retained")}
        val parent=HiddenVpnStorageCleanup.prepare(context,UUID.randomUUID().toString())
        var child:HiddenVpnStorageCleanup.Lease?=null
        try {
            parent.data.mkdirs();File(parent.data,"hidden-cookie").writeText("retired hidden cookie")
            File(parent.cache,"worker-cache").writeText("retired worker cache")
            java.nio.file.Files.createSymbolicLink(File(parent.data,"outside-link").toPath(),primary.toPath())
            assertTrue(HiddenVpnStorageCleanup.clean(context))
            assertTrue(parent.data.exists())
            child=HiddenVpnStorageCleanup.holdChild(context,parent.id)
            parent.close()
            assertTrue("A live child prevents root deletion even after parent release",HiddenVpnStorageCleanup.clean(context))
            assertTrue(parent.data.exists())
            child.close();child=null
            assertFalse("No pending root remains after both owners die",HiddenVpnStorageCleanup.clean(context))
            assertFalse(parent.data.exists());assertFalse(parent.cache.exists())
            assertEquals("primary retained",primary.readText())
            assertFalse(HiddenVpnStorageCleanup.clean(context))
        } finally {parent.close();child?.close();sandbox.deleteRecursively()}
    }
    @Test fun noRetiredGenerationCanReopenDeletedRoots() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val sandbox=File(base.noBackupFilesDir,"vpn-retired-test-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base) {
            override fun getNoBackupFilesDir()=File(sandbox,"private").apply {mkdirs()}
            override fun getCacheDir()=File(sandbox,"cache").apply {mkdirs()}
        }
        val lease=HiddenVpnStorageCleanup.prepare(context,UUID.randomUUID().toString())
        try {
            lease.close();assertFalse(HiddenVpnStorageCleanup.clean(context))
            try {HiddenVpnStorageCleanup.holdChild(context,lease.id).close();fail("Retired root was recreated")} catch(expected:Exception) {}
        } finally {lease.close();sandbox.deleteRecursively()}
    }
}
