package uk.co.traynor.privategallery.core.editor

import org.junit.Assert.*
import org.junit.Test

class PromptEnhancementTest {
    @Test fun defaultsKeepFullOwnerInstructionAndChooseSeparateStrategies() {
        val owner = "Keep the inscription: Do not change the face."
        val create = PromptEnhancement.effective(owner, PromptKind.CREATE, "bytedance/seedream-5-pro")
        val edit = PromptEnhancement.effective(owner, PromptKind.EDIT, "bytedance/seedream-5-pro")
        val mask = PromptEnhancement.effective(owner, PromptKind.MASK, "black-forest-labs/flux-fill-pro")
        assertTrue(create.contains(owner)); assertTrue(create.contains("composition"))
        assertTrue(edit.contains(owner)); assertTrue(edit.contains("identity"))
        assertTrue(mask.contains(owner)); assertTrue(mask.contains("unmasked"))
        assertNotEquals(create, edit)
        assertEquals(owner, PromptEnhancement.effective(owner, PromptKind.EDIT, "bytedance/seedream-5-pro", enabled = false))
    }

    @Test fun customTemplateAndModelOverridePreserveOwnerAndResetToCurrentBundledDefault() {
        val custom = "Precise adjustment. User edit: {{PROMPT}}"
        assertTrue(PromptEnhancement.effective("blue", PromptKind.EDIT,
            "bytedance/seedream-5-lite", template = custom).endsWith("Precise adjustment. User edit: blue"))
        assertEquals("Special blue", PromptEnhancement.effective("blue", PromptKind.EDIT,
            "bytedance/seedream-5-lite", template = custom, modelOverride = "Special {{PROMPT}}"))
        assertTrue(runCatching { PromptEnhancement.validate("Discard owner request") }.isFailure)
        assertTrue(runCatching { PromptEnhancement.validate("{{PROMPT}} {{PROMPT}}") }.isFailure)
    }
}
