package uk.co.traynor.privategallery.core.home2

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.function.UnaryOperator
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.privateapps.PrivateAppStore

class NativeAppBridgeTest {
    private class Store : PrivateAppStore {
        var catalog = PrivateAppCatalog(false, emptyList())
        override fun owner() = PrivateAppStore.Owner.PRIMARY
        override fun read() = catalog
        override fun update(change: UnaryOperator<PrivateAppCatalog>) = change.apply(catalog).also { catalog = it }
    }
    @Test fun reinstall_canonical_url_preserves_production_id_order_and_all_settings() {
        val store = Store()
        val first = PrivateAppCatalog.App(UUID.randomUUID().toString(), "Original", "https://EXAMPLE.test:443/app",
            "https://example.test/", "standalone", "aWNvbg==", true, false, true, false, "Saved report")
        store.catalog = store.catalog.upsert(first)
        val bridge = NativeAppBridge(store, { true }, {}, {}, {})
        assertEquals(first, bridge.install("Replacement name", "https://example.test/app"))
        assertEquals(listOf(first), bridge.list())
        val second = bridge.install("Second", "https://example.test/other?q=a%2Fb")
        assertEquals("https://example.test/other?q=a%2Fb", second.startUrl())
        assertFalse(second.addressBar())
        assertTrue(second.appStyle())
        assertEquals(listOf(first, second), bridge.list())
    }
    @Test fun operations_cannot_inherit_revoked_authority_or_launch_removed_ids() {
        val store = Store()
        var current = true
        val launches = mutableListOf<String>()
        val closes = mutableListOf<String>()
        val browses = mutableListOf<String>()
        val bridge = NativeAppBridge(store, { current }, { launches.add(it) }, { closes.add(it) }, { browses.add(it) })
        val app = bridge.install("Example", "https://example.test")
        bridge.launch(app.id())
        assertEquals(listOf(app.id()), launches)
        bridge.browse("https://EXAMPLE.test:443/results?q=hello%20world")
        assertEquals(listOf("https://example.test/results?q=hello%20world"), browses)
        bridge.uninstall(app.id())
        assertEquals(listOf(app.id()), closes)
        assertThrows(IllegalStateException::class.java) { bridge.launch(app.id()) }
        assertEquals(1, launches.size)
        current = false
        assertThrows(IllegalStateException::class.java) { bridge.list() }
        assertThrows(IllegalStateException::class.java) { bridge.install("New", "https://example.test") }
        assertThrows(IllegalStateException::class.java) { bridge.uninstall(app.id()) }
        assertThrows(IllegalStateException::class.java) { bridge.launch(app.id()) }
        assertThrows(IllegalStateException::class.java) { bridge.close(app.id()) }
        assertThrows(IllegalStateException::class.java) { bridge.browse("https://example.test") }
    }
    @Test fun install_rejects_unsupported_addresses_and_unbounded_names_without_mutation() {
        val store = Store()
        val bridge = NativeAppBridge(store, { true }, {}, {}, {})
        listOf("http://example.test", "javascript:alert(1)", "https://user:secret@example.test", "https://example.test\\path").forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { bridge.install("Example", url) }
        }
        assertThrows(IllegalArgumentException::class.java) { bridge.install("x".repeat(121), "https://example.test") }
        assertThrows(IllegalArgumentException::class.java) { bridge.install("Bad\nname", "https://example.test") }
        assertTrue(store.catalog.apps.isEmpty())
    }
}
