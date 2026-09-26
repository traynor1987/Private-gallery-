package uk.co.traynor.privategallery.core.editor

import android.content.Context

enum class PromptKind { CREATE, EDIT, MASK }

/** Templates are configuration; an owner's actual prompt is never persisted here. */
object PromptEnhancement {
    const val VARIABLE = "{{PROMPT}}"
    fun default(kind: PromptKind): String = when (kind) {
        PromptKind.CREATE -> "Create a high-quality image that faithfully follows the user's request. Prioritize natural anatomy and proportions, coherent hands and limbs, realistic texture and detail, physically plausible lighting and shadows, consistent perspective and strong composition. Avoid unintended distortions, duplicate features, unwanted text, watermarks and artifacts.\n\nUser request:\n{{PROMPT}}"
        PromptKind.EDIT -> "Perform a precise edit of the supplied source image. Preserve the subject's identity, facial features, body proportions, pose, composition, camera perspective, lighting, background and unrelated details unless the user explicitly requests a change. Make only the requested modification and blend it naturally. Avoid unrelated regeneration, anatomical distortions, duplicate features and artifacts.\n\nUser edit:\n{{PROMPT}}"
        PromptKind.MASK -> "Modify only the selected region of the source image. Preserve unmasked regions as closely as possible and blend boundaries with surrounding lighting, texture, perspective and colour.\n\nUser edit:\n{{PROMPT}}"
    }
    fun validate(template: String) {
        require(template.length <= 3000 && template.windowed(VARIABLE.length).count { it == VARIABLE } == 1) {
            "Include {{PROMPT}} exactly once in a template of up to 3,000 characters."
        }
    }
    fun effective(prompt: String, kind: PromptKind, model: String, enabled: Boolean = true,
        template: String = default(kind), modelOverride: String? = null): String {
        if (!enabled) return prompt
        val chosen = modelOverride ?: template
        validate(chosen)
        // Short provider-specific framing follows the global owner template. Owner wording is untouched.
        val guidance = if (modelOverride != null) "" else when {
            model == "bytedance/seedream-5-lite" -> "Describe the intended result naturally and state what must remain unchanged.\n"
            model == "bytedance/seedream-5-pro" -> "Use supplied references to guide consistency while following the user's requested changes.\n"
            model == "black-forest-labs/flux-kontext-pro" -> "Keep the edit instruction specific and preserve unrelated scene details.\n"
            model == "black-forest-labs/flux-fill-pro" && kind == PromptKind.MASK -> "Treat the selection mask as the edit area.\n"
            model == "alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03" -> "Maintain coherent character proportions and visual detail.\n"
            else -> ""
        }
        val result = guidance + chosen.replace(VARIABLE, prompt)
        require(result.length <= 4000) { "The prompt and enhancement exceed this model's 4,000 character limit." }
        return result
    }
}

class PromptEnhancementStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai_prompt_enhancement", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    fun template(kind: PromptKind): String = prefs.getString("template_${kind.name}", null) ?: PromptEnhancement.default(kind)
    fun isCustom(kind: PromptKind) = prefs.contains("template_${kind.name}")
    fun save(kind: PromptKind, value: String) { PromptEnhancement.validate(value); prefs.edit().putString("template_${kind.name}", value).apply() }
    fun reset(kind: PromptKind) { prefs.edit().remove("template_${kind.name}").apply() }
    fun modelOverride(kind: PromptKind, model: String): String? = prefs.getString("override_${kind.name}_$model", null)
    fun saveOverride(kind: PromptKind, model: String, value: String?) {
        if (value != null) PromptEnhancement.validate(value)
        prefs.edit().apply { if (value == null) remove("override_${kind.name}_$model") else putString("override_${kind.name}_$model", value) }.apply()
    }
    fun effective(prompt: String, kind: PromptKind, model: String, requestEnabled: Boolean = true) =
        PromptEnhancement.effective(prompt, kind, model, enabled && requestEnabled, template(kind), modelOverride(kind, model))
}
