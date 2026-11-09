package uk.co.traynor.privategallery.launcher

import android.content.Context

/** Launcher-only labels and template decisions stay on this device. */
internal class LauncherIdentitySettings(context: Context) {
    private val prefs = context.getSharedPreferences("nova_launcher_identity_settings", Context.MODE_PRIVATE)

    fun label(app: LauncherApp): LauncherApp =
        prefs.getString("label:${app.component}", null)?.let { app.copy(label = it) } ?: app

    fun saveLabel(component: String, label: String): Boolean {
        if (!LauncherAppIdentity.isValidKey(component)) return false
        val value = label.trim()
        if (value.length > MAX_LABEL || value.any(Char::isISOControl)) return false
        val key = "label:$component"
        val update = prefs.edit()
        if (value.isEmpty()) update.remove(key) else update.putString(key, value)
        return update.commit()
    }

    fun smartFolderPresentation(): LauncherSmartFolderPresentation =
        LauncherSmartFolderPresentation.entries.firstOrNull {
            it.name == prefs.getString("smart-folder-presentation", null)
        } ?: LauncherSmartFolderPresentation.AUTOMATIC

    fun setSmartFolderPresentation(mode: LauncherSmartFolderPresentation): Boolean =
        prefs.edit().putString("smart-folder-presentation", mode.name).commit()

    fun libraryPins(): Set<LauncherCategory> {
        val names = prefs.getStringSet("library-category-pins", emptySet()).orEmpty()
        return LauncherCategory.entries.filterTo(mutableSetOf()) { it.name in names }
    }

    fun setLibraryPins(categories: Set<LauncherCategory>): Boolean = prefs.edit()
        .putStringSet("library-category-pins", categories.mapTo(mutableSetOf()) { it.name }).commit()

    fun categoryOverrides(): Map<String, LauncherCategory> = prefs.all.entries.mapNotNull { (key, value) ->
        if (!key.startsWith(CATEGORY_PREFIX) || value !is String) return@mapNotNull null
        val component = key.removePrefix(CATEGORY_PREFIX)
        if (!LauncherAppIdentity.isValidKey(component)) return@mapNotNull null
        LauncherCategory.entries.firstOrNull { it.name == value && it.isAssignable }
            ?.let { component to it }
    }.toMap()

    fun saveCategoryOverride(component: String, category: LauncherCategory?): Boolean {
        if (!LauncherAppIdentity.isValidKey(component) || category?.isAssignable == false) return false
        val key = CATEGORY_PREFIX + component
        val update = prefs.edit()
        if (category == null) update.remove(key) else update.putString(key, category.name)
        return update.commit()
    }

    fun templateHandled(inner: Boolean): Boolean = prefs.getBoolean("smart-home-template-handled:$inner", false)

    fun markTemplateHandled(inner: Boolean) {
        prefs.edit().putBoolean("smart-home-template-handled:$inner", true).apply()
    }

    companion object {
        const val MAX_LABEL = 48
        private const val CATEGORY_PREFIX = "category:"
    }
}
