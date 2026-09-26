package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class AiProviderMigrationTest {
    @Test fun openAiOptionsPersistWithoutChangingReplicateSelectionOrConsent() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val options = context.getSharedPreferences("openai_image_options", Context.MODE_PRIVATE)
        val selection = context.getSharedPreferences("ai_provider_selection", Context.MODE_PRIVATE)
        try {
            options.edit().clear().commit()
            selection.edit().putString("provider_choice", "REPLICATE").commit()
            val values = OpenAiImagePreferences(context)
            assertEquals(OpenAiImageModel.FLARE, values.model)
            assertEquals(OpenAiImageQuality.AUTO, values.quality)
            assertEquals(OpenAiImageModeration.STANDARD, values.moderation)
            values.model = OpenAiImageModel.SUNBURST
            values.quality = OpenAiImageQuality.XHIGH
            values.moderation = OpenAiImageModeration.LOWER
            assertEquals(OpenAiImageModel.SUNBURST, OpenAiImagePreferences(context).model)
            assertEquals(OpenAiImageQuality.XHIGH, OpenAiImagePreferences(context).quality)
            assertEquals(OpenAiImageModeration.LOWER, OpenAiImagePreferences(context).moderation)
            assertEquals("REPLICATE", selection.getString("provider_choice", null))
        } finally { options.edit().clear().commit(); selection.edit().clear().commit() }
    }
    @Test fun retiredChoicesMigrateToCloudWithoutTouchingOtherPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("migration-fixture", Context.MODE_PRIVATE)
        try {
            preferences.edit().clear().putString("unrelated", "preserve").commit()
            migrateProviderChoice(preferences, true)
            assertEquals("REPLICATE", preferences.getString("provider_choice", null))
            preferences.edit().putString("provider_choice", "LIGHTWEIGHT").commit()
            migrateProviderChoice(preferences, true)
            assertEquals("REPLICATE", preferences.getString("provider_choice", null))
            assertEquals("preserve", preferences.getString("unrelated", null))
            preferences.edit().putString("provider_choice", "OPENAI").commit()
            migrateProviderChoice(preferences, true)
            assertEquals("OPENAI", preferences.getString("provider_choice", null))
            preferences.edit().remove("provider_choice").commit()
            migrateProviderChoice(preferences, false)
            assertEquals("REPLICATE", preferences.getString("provider_choice", null))
        } finally { preferences.edit().clear().commit() }
    }
}
