package uk.co.traynor.privategallery.home2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession

class SanctuaryNavigationTest {
    @Test fun allModesApproveExistingAndNewInternalAppsButExcludeWhatsApp() {
        SanctuaryMode.entries.forEach { mode ->
            val session = session(mode)
            for (id in listOf("vault", "gallery", "arcade", "arcade-sudoku", "mind:home", "health",
                "notes", "files", "ai", "nova-people", "calculator", "browser-v1", "web:youtube-music",
                "web:installed-after-start", "private-apps", "resume-internal:mind", "store:youtube",
                "sanctuary", "native-settings", "panic", "lock-now")) {
                assertTrue("${mode.name}/$id", sanctuaryAllowsNovaRoute(session, id))
            }
            for (id in listOf("chat", "chat-inspector", "chat-settings", "web:whatsapp",
                "store:whatsapp", "resume-internal:chat")) {
                assertFalse("${mode.name}/$id", sanctuaryAllowsNovaRoute(session, id))
            }
            assertFalse(sanctuaryAllowsNovaRoute(session, "web:renamed-app", "https://web.whatsapp.com/"))
            assertFalse(sanctuaryAllowsNovaRoute(session, "browser-v1", "https://wa.me/123"))
        }
    }

    @Test fun explicitModeDenialsAndWhatsAppOptInRemainSeparate() {
        val session = session(SanctuaryMode.WORK).copy(
            blockedNovaApps = setOf("arcade", "store", "web:work-distraction", "lock-now"),
            allowWhatsApp = true,
        )
        assertFalse(sanctuaryAllowsNovaRoute(session, "arcade-sudoku"))
        assertFalse(sanctuaryAllowsNovaRoute(session, "store:youtube"))
        assertFalse(sanctuaryAllowsNovaRoute(session, "resume-internal:web:work-distraction"))
        assertTrue(sanctuaryAllowsNovaRoute(session, "lock-now"))
        assertTrue(sanctuaryAllowsNovaRoute(session, "web:renamed-app", "https://web.whatsapp.com/"))
        assertFalse(sanctuaryAllowsNovaRoute(session.copy(blockedNovaApps = setOf("web:renamed-app")),
            "web:renamed-app", "https://web.whatsapp.com/"))
    }

    private fun session(mode: SanctuaryMode) = SanctuarySession(
        "fixture", mode, 0, 300_000L, emptySet(), emptySet(), 0,
    )
}
