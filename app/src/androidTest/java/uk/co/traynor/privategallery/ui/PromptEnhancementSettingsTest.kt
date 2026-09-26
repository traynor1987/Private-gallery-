package uk.co.traynor.privategallery.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class PromptEnhancementSettingsTest {
    @Test fun customPersistsAcrossStoreRecreationAndResetAdoptsBundledDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val first = PromptEnhancementStore(context)
        val originalEnabled = first.enabled
        val original = PromptKind.entries.associateWith { if (first.isCustom(it)) first.template(it) else null }
        try {
            first.reset(PromptKind.EDIT)
            assertFalse(first.isCustom(PromptKind.EDIT))
            assertEquals(PromptEnhancement.default(PromptKind.EDIT), PromptEnhancementStore(context).template(PromptKind.EDIT))
            first.save(PromptKind.EDIT, "Make the requested change precisely. User edit: {{PROMPT}}")
            first.enabled = true
            val restarted = PromptEnhancementStore(context)
            assertTrue(restarted.isCustom(PromptKind.EDIT))
            assertTrue(restarted.effective("blue", PromptKind.EDIT, "bytedance/seedream-5-pro").contains("User edit: blue"))
            assertEquals("blue", restarted.effective("blue", PromptKind.EDIT, "bytedance/seedream-5-pro", false))
            restarted.saveOverride(PromptKind.EDIT, "bytedance/seedream-5-pro", "Reference-aware {{PROMPT}}")
            assertEquals("Reference-aware blue", PromptEnhancementStore(context).effective("blue", PromptKind.EDIT, "bytedance/seedream-5-pro"))
            restarted.saveOverride(PromptKind.EDIT, "bytedance/seedream-5-pro", null)
            restarted.reset(PromptKind.EDIT)
            assertEquals(PromptEnhancement.default(PromptKind.EDIT), PromptEnhancementStore(context).template(PromptKind.EDIT))
        } finally {
            first.enabled = originalEnabled
            first.saveOverride(PromptKind.EDIT, "bytedance/seedream-5-pro", null)
            original.forEach { (kind, custom) -> if (custom == null) first.reset(kind) else first.save(kind, custom) }
        }
    }
}
