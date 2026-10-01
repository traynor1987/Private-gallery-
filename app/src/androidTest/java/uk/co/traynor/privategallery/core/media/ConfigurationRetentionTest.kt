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
        var originalEpoch: uk.co.traynor.privategallery.core.security.SessionEpoch? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ProtectedSessionState::class.java].let {
                    it.authority.open(key)
                    it.session.unlock()
                    originalEpoch = checkNotNull(it.authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation -> operation.epoch }
                }
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ProtectedSessionState::class.java].let {
                    assertTrue(key.any { it != 0.toByte() }); assertTrue(it.session.isUnlocked)
                    checkNotNull(it.authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                        assertEquals(originalEpoch, operation.epoch)
                        operation.checkValid()
                    }
                }
            }
        }
        assertTrue(key.all { it == 0.toByte() })
    }
}
