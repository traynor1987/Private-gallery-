package uk.co.traynor.privategallery.core.hidden

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserLibrary

/** Compiled against the published source and used on both sides of the isolated APK replacement. */
class HiddenUpgrade94Test {
    @Test fun encryptedRecordsSurviveReplacement() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype")
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        require(phase in setOf("seed", "verify", "restart"))
        val seed = phase == "seed"
        val credentials = HiddenCredentialStore(context)
        assertEquals(!seed, credentials.isConfigured)
        val pin = "905184".toCharArray()
        val key = if (seed) credentials.create(pin) else credentials.unlock(pin)
        val session = HiddenSession(SystemClock::elapsedRealtime)
        try {
            assertTrue(session.complete(session.beginAuthentication(), key))
            val repo = HiddenRepository(context, session)
            val media = instrumentation.context.assets.open("browser-media/valid.mp4").use { it.readBytes() }
            if (seed) {
                assertTrue(repo.items().isEmpty())
                val item = repo.importMedia("upgrade-fixture.mp4", "video/mp4", media.inputStream())
                val collection = repo.createCollection("Build 94 fixture")
                repo.addItemsToCollection(collection.id, listOf(item.id))
                repo.setFavouriteCollection(collection.id)
            }
            val item = repo.items().single()
            assertArrayEquals(media, repo.read(item.id))
            val collection = checkNotNull(repo.favouriteCollection())
            assertEquals(item.id, repo.itemsInCollection(collection.id).single().id)
            for (vpn in listOf(true, false)) {
                val url = if (vpn) "https://vpn.invalid/fixture" else "https://tor.invalid/fixture"
                val browser = HiddenBrowserLibrary(context.noBackupFilesDir, session, session.authority(), vpn)
                if (seed) {
                    browser.saveHistory(vpn)
                    browser.bookmark(url, "Saved before update", 1000)
                    browser.visit(url, "Visited before update", 1000)
                }
                assertEquals(listOf(url), browser.bookmarks())
                assertEquals("Saved before update", browser.snapshot().bookmarks.single().title)
                assertEquals(if (vpn) listOf(url) else emptyList<String>(), browser.history())
            }
        } finally {
            session.lock()
            key.fill(0)
            pin.fill('0')
        }
    }
}
