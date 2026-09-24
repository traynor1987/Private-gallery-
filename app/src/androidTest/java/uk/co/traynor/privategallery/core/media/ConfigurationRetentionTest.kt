package uk.co.traynor.privategallery.core.media

import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState

class ConfigurationRetentionTest {
    @Test fun explicitActivityRecreationRetainsOnlyInProcessUnlockedSession() {
        val key = ByteArray(32) { 9 }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ProtectedSessionState::class.java].let {
                    it.key = key; it.session.unlock()
                }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ProtectedSessionState::class.java].let {
                    assertSame(key, it.key); assertTrue(it.session.isUnlocked)
                }
            }
        }
        assertTrue(key.all { it == 0.toByte() })
    }
}
