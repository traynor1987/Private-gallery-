package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class AiProviderMigrationTest {
    @Test fun configuredUpgradeKeepsCloudAndExistingOwnerChoiceWins() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("migration-fixture", Context.MODE_PRIVATE)
        try {
            preferences.edit().clear().putString("unrelated", "preserve").commit()
            migrateProviderChoice(preferences, true)
            assertEquals("REPLICATE", preferences.getString("provider_choice", null))
            preferences.edit().putString("provider_choice", "LIGHTWEIGHT").commit()
            migrateProviderChoice(preferences, true)
            assertEquals("LIGHTWEIGHT", preferences.getString("provider_choice", null))
            assertEquals("preserve", preferences.getString("unrelated", null))
            preferences.edit().remove("provider_choice").commit()
            migrateProviderChoice(preferences, false)
            assertEquals("AUTO", preferences.getString("provider_choice", null))
        } finally { preferences.edit().clear().commit() }
    }
}
