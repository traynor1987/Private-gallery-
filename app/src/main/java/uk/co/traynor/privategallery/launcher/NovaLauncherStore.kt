package uk.co.traynor.privategallery.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal const val MAX_LAUNCHER_BACKUP_BYTES = 768 * 1024

/** Owner-controlled Android Home settings, independent of NovaOS workspace stores and keys. */
internal class NovaLauncherStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_android_home_v1", Context.MODE_PRIVATE)

    fun askForDefaultHome(): Boolean = prefs.getBoolean("ask_default_home", true)

    fun setAskForDefaultHome(ask: Boolean): Boolean = prefs.edit().putBoolean("ask_default_home", ask).commit()

    fun read(): NovaLauncherState = synchronized(lock) {
        val raw = prefs.getString("state", null) ?: return@synchronized NovaLauncherState()
        runCatching { LauncherLayoutCodec.decode(raw, portable = false) }.getOrDefault(NovaLauncherState())
    }

    fun save(state: NovaLauncherState): Boolean = synchronized(lock) {
        val encoded = runCatching { LauncherLayoutCodec.encode(state, portable = false) }.getOrNull()
            ?: return@synchronized false
        val update = prefs.edit().putString("state", encoded)
        if (state.newAppPlacement == LauncherNewAppPlacement.HOME) {
            if (!prefs.contains(NEW_APPS_OBSERVED_AT)) {
                update.putLong(NEW_APPS_OBSERVED_AT, System.currentTimeMillis())
            }
        } else {
            update.remove(NEW_APPS_OBSERVED_AT)
        }
        update.commit()
    }

    /** Use install timestamps from the current profile or exact LauncherApps package-added events. */
    fun observeInstalledApps(
        installed: List<LauncherApp>,
        observedAt: Long,
        addedComponents: Set<String> = emptySet(),
    ): NovaLauncherState? = synchronized(lock) {
        val current = read()
        if (!current.configured || current.newAppPlacement != LauncherNewAppPlacement.HOME) {
            return@synchronized null
        }
        val previous = prefs.getLong(NEW_APPS_OBSERVED_AT, 0L)
        val newApps = installed.filter { app ->
            val time = app.firstInstallTime
            app.component in addedComponents ||
                (previous > 0 && time != null && time > previous && time <= observedAt)
        }.distinctBy { it.packageName to it.profileSerial }.map(LauncherApp::component)
        val updated = current.withNewlyInstalledApps(newApps)
        val update = prefs.edit().putLong(NEW_APPS_OBSERVED_AT, maxOf(previous, observedAt))
        if (current != updated) {
            val encoded = runCatching { LauncherLayoutCodec.encode(updated, portable = false) }.getOrNull()
                ?: return@synchronized null
            update.putString("state", encoded)
        }
        if (update.commit() && current != updated) updated else null
    }

    fun exportLayout(): ByteArray = LauncherLayoutCodec.encode(read(), portable = true).encodeToByteArray()

    fun restoreLayout(bytes: ByteArray): Boolean = synchronized(lock) {
        if (bytes.size !in 1..MAX_LAUNCHER_BACKUP_BYTES) return@synchronized false
        val decoded = runCatching {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            LauncherLayoutCodec.decode(text.toString(), portable = true)
        }.getOrNull() ?: return@synchronized false

        val current = read()
        val widgets = current.widgets.map { widget ->
            val layout = if (widget.wide) decoded.inner else decoded.cover
            widget.copy(page = widget.page.coerceIn(layout.pages.indices))
        }
        save(decoded.copy(configured = current.configured, widgets = widgets, launchCounts = current.launchCounts))
    }

    private companion object {
        val lock = Any()
        const val NEW_APPS_OBSERVED_AT = "new_apps_observed_at"
    }
}

private object LauncherLayoutCodec {
    private const val SCHEMA = 1
    private const val TYPE = "nova-launcher-layout"
    private val layoutKeys = setOf("type", "schema", "separateDisplays", "cover", "inner", "appearance", "drawer", "gesture", "hideBlocked")
    private val optionalLayoutKeys = setOf("dockOptions", "newAppPlacement")
    private val localKeys = setOf("configured", "widgets", "launchCounts")

    fun encode(state: NovaLauncherState, portable: Boolean): String {
        val appearance = state.appearance
        val drawer = state.drawer
        val root = JSONObject()
            .put("type", TYPE)
            .put("schema", SCHEMA)
            .put("separateDisplays", state.separateDisplays)
            .put("cover", layout(state.cover))
            .put("inner", layout(state.inner))
            .put("appearance", JSONObject()
                .put("wallpaper", appearance.wallpaper.name)
                .put("theme", appearance.theme.name)
                .put("accent", appearance.accent.name)
                .put("iconShape", appearance.iconShape.name)
                .put("iconSize", appearance.iconSize.name)
                .put("labels", appearance.labels)
                .put("grid", appearance.grid.name)
                .put("dock", appearance.dock.name))
            .put("drawer", JSONObject()
                .put("orientation", drawer.orientation.name)
                .put("search", drawer.search.name)
                .put("sort", drawer.sort.name)
                .put("folders", drawer.folders)
                .put("frequent", drawer.frequent)
                .put("shortcutSearch", drawer.shortcutSearch))
            .put("gesture", state.workspaceGesture.name)
            .put("hideBlocked", state.hideBlocked)
            .put("dockOptions", JSONObject()
                .put("locked", state.dockOptions.locked)
                .put("showInLibrary", state.dockOptions.showInLibrary)
                .put("labels", state.dockOptions.labels))
            .put("newAppPlacement", state.newAppPlacement.name)

        if (!portable) {
            require(state.widgets.size <= MAX_LAUNCHER_WIDGETS && state.widgets.distinctBy(LauncherWidget::id).size == state.widgets.size)
            val widgets = JSONArray()
            for (widget in state.widgets) {
                val pageRange = if (widget.wide) state.inner.pages.indices else state.cover.pages.indices
                require(widget.id > 0 && isLauncherComponent(widget.provider) && widget.page in pageRange)
                widgets.put(JSONObject().put("id", widget.id).put("provider", widget.provider)
                    .put("wide", widget.wide).put("page", widget.page))
            }
            require(state.launchCounts.size <= MAX_LAUNCH_COUNTS)
            val counts = JSONObject()
            for ((component, count) in state.launchCounts) {
                require(LauncherAppIdentity.isValidKey(component) && count in 1..MAX_LAUNCH_COUNT)
                counts.put(component, count)
            }
            root.put("configured", state.configured).put("widgets", widgets).put("launchCounts", counts)
            state.coverCleanupUndo?.let { root.put("coverCleanupUndo", pages(it)) }
            state.innerCleanupUndo?.let { root.put("innerCleanupUndo", pages(it)) }
        }

        return root.toString().also { require(it.encodeToByteArray().size <= MAX_LAUNCHER_BACKUP_BYTES) }
    }

    fun decode(raw: String, portable: Boolean): NovaLauncherState {
        require(raw.length in 1..MAX_LAUNCHER_BACKUP_BYTES)
        checkDepth(raw)
        val reader = JSONTokener(raw)
        val root = reader.nextValue() as? JSONObject ?: error("Invalid launcher file")
        require(reader.nextClean() == '\u0000')
        val optionalLocalKeys = setOf("coverCleanupUndo", "innerCleanupUndo").filter(root::has)
        val knownLayoutKeys = layoutKeys + optionalLayoutKeys.filter(root::has)
        root.requireKeys(if (portable) knownLayoutKeys else knownLayoutKeys + localKeys + optionalLocalKeys)
        require(root.string("type") == TYPE && root.number("schema") == SCHEMA)

        val appearance = root.getJSONObject("appearance")
        appearance.requireKeys(setOf("wallpaper", "theme", "accent", "iconShape", "iconSize", "labels", "grid", "dock"))
        val drawer = root.getJSONObject("drawer")
        drawer.requireKeys(setOf("orientation", "search", "sort", "folders", "frequent", "shortcutSearch"))

        val cover = parseLayout(root.getJSONObject("cover"))
        val inner = parseLayout(root.getJSONObject("inner"))
        val widgets = if (portable) emptyList() else root.getJSONArray("widgets").read(0..MAX_LAUNCHER_WIDGETS) { entry ->
            val widget = entry as JSONObject
            val legacyKeys = setOf("id", "provider", "wide")
            widget.requireKeys(if (widget.has("page")) legacyKeys + "page" else legacyKeys)
            val wide = widget.boolean("wide")
            val pageRange = if (wide) inner.pages.indices else cover.pages.indices
            LauncherWidget(
                widget.number("id").also { require(it > 0) },
                widget.string("provider").also { require(isLauncherComponent(it)) },
                wide,
                (if (widget.has("page")) widget.number("page") else 0).also { require(it in pageRange) },
            )
        }.also { require(it.distinctBy(LauncherWidget::id).size == it.size) }

        val counts = if (portable) emptyMap() else root.getJSONObject("launchCounts").let { values ->
            require(values.length() <= MAX_LAUNCH_COUNTS)
            values.keys().asSequence().associateWith { key ->
                require(LauncherAppIdentity.isValidKey(key))
                values.number(key).also { require(it in 1..MAX_LAUNCH_COUNT) }
            }
        }

        val state = NovaLauncherState(
            configured = !portable && root.boolean("configured"),
            separateDisplays = root.boolean("separateDisplays"),
            cover = cover,
            inner = inner,
            appearance = LauncherAppearance(
                wallpaper = LauncherWallpaper.valueOf(appearance.string("wallpaper")),
                theme = LauncherTheme.valueOf(appearance.string("theme")),
                accent = LauncherAccent.valueOf(appearance.string("accent")),
                iconShape = LauncherIconShape.valueOf(appearance.string("iconShape")),
                iconSize = LauncherIconSize.valueOf(appearance.string("iconSize")),
                labels = appearance.boolean("labels"),
                grid = LauncherGrid.valueOf(appearance.string("grid")),
                dock = LauncherDock.valueOf(appearance.string("dock")),
            ),
            drawer = LauncherDrawerOptions(
                orientation = LauncherDrawerOrientation.valueOf(drawer.string("orientation")),
                search = LauncherSearchPlacement.valueOf(drawer.string("search")),
                sort = LauncherSort.valueOf(drawer.string("sort")),
                folders = drawer.boolean("folders"),
                frequent = drawer.boolean("frequent"),
                shortcutSearch = drawer.boolean("shortcutSearch"),
            ),
            workspaceGesture = LauncherGesture.valueOf(root.string("gesture")),
            hideBlocked = root.boolean("hideBlocked"),
            widgets = widgets,
            launchCounts = counts,
            coverCleanupUndo = if (root.has("coverCleanupUndo")) parsePages(root.getJSONArray("coverCleanupUndo")) else null,
            innerCleanupUndo = if (root.has("innerCleanupUndo")) parsePages(root.getJSONArray("innerCleanupUndo")) else null,
            dockOptions = if (root.has("dockOptions")) root.getJSONObject("dockOptions").let { dock ->
                dock.requireKeys(setOf("locked", "showInLibrary", "labels"))
                LauncherDockOptions(dock.boolean("locked"), dock.boolean("showInLibrary"), dock.boolean("labels"))
            } else LauncherDockOptions(),
            newAppPlacement = if (root.has("newAppPlacement")) {
                LauncherNewAppPlacement.valueOf(root.string("newAppPlacement"))
            } else LauncherNewAppPlacement.LIBRARY_ONLY,
        )
        // Local Code 73 state predates the dock options. Preserve intentional duplicates in
        // portable imports and in layouts saved after this upgrade.
        return if (!portable && !root.has("dockOptions")) state.withoutRedundantSmartFolderPages() else state
    }

    private fun layout(value: LauncherLayout): JSONObject {
        require(value.pages.size in 1..MAX_LAUNCHER_PAGES && value.dock.size <= MAX_LAUNCHER_DOCK_ITEMS)
        require(value.favourites.size <= MAX_LAUNCHER_FAVOURITES && value.favourites.all(LauncherAppIdentity::isValidKey))
        return JSONObject()
            .put("pages", pages(value.pages))
            .put("dock", JSONArray(value.dock.map(::item)))
            .put("favourites", JSONArray(value.favourites))
    }

    private fun pages(value: List<List<LauncherItem>>): JSONArray {
        require(value.size in 1..MAX_LAUNCHER_PAGES)
        val pages = JSONArray()
        for (page in value) {
            require(page.size <= MAX_LAUNCHER_PAGE_ITEMS)
            pages.put(JSONArray(page.map(::item)))
        }
        return pages
    }

    private fun item(value: LauncherItem): JSONObject = when (value) {
        is LauncherItem.App -> {
            require(LauncherAppIdentity.isValidKey(value.component))
            JSONObject().put("type", "app").put("component", value.component)
        }
        is LauncherItem.Folder -> {
            require(validFolderName(value.name) && validFolderApps(value.apps, minimum = 1))
            JSONObject().put("type", "folder").put("name", value.name).put("apps", JSONArray(value.apps))
        }
        is LauncherItem.SmartFolder -> {
            require(value.customName == null || validFolderName(value.customName))
            require(validFolderApps(value.included, minimum = 0))
            JSONObject().put("type", "smart-folder")
                .put("category", value.category.name)
                .put("included", JSONArray(value.included))
                .apply { value.customName?.let { put("name", it) } }
        }
        LauncherItem.Workspace -> JSONObject().put("type", "workspace")
    }

    private fun parseLayout(value: JSONObject): LauncherLayout {
        value.requireKeys(setOf("pages", "dock", "favourites"))
        return LauncherLayout(
            pages = parsePages(value.getJSONArray("pages")),
            dock = value.getJSONArray("dock").read(0..MAX_LAUNCHER_DOCK_ITEMS) { parseItem(it as JSONObject) },
            favourites = value.getJSONArray("favourites").read(0..MAX_LAUNCHER_FAVOURITES) {
                (it as String).also { component -> require(LauncherAppIdentity.isValidKey(component)) }
            }.toSet(),
        )
    }

    private fun parsePages(pages: JSONArray): List<List<LauncherItem>> = pages.read(1..MAX_LAUNCHER_PAGES) { page ->
        (page as JSONArray).read(0..MAX_LAUNCHER_PAGE_ITEMS) { parseItem(it as JSONObject) }
    }

    private fun parseItem(value: JSONObject): LauncherItem = when (value.string("type")) {
        "app" -> {
            value.requireKeys(setOf("type", "component"))
            LauncherItem.App(value.string("component").also { require(LauncherAppIdentity.isValidKey(it)) })
        }
        "folder" -> {
            value.requireKeys(setOf("type", "name", "apps"))
            val name = value.string("name")
            val apps = value.getJSONArray("apps").read(1..MAX_LAUNCHER_FOLDER_ITEMS) { it as String }
            require(validFolderName(name) && validFolderApps(apps, minimum = 1))
            LauncherItem.Folder(name, apps)
        }
        "smart-folder" -> {
            val keys = setOf("type", "category", "included")
            val hasName = value.has("name")
            value.requireKeys(if (hasName) keys + "name" else keys)
            val name = if (hasName) value.string("name").also { require(validFolderName(it)) } else null
            val included = value.getJSONArray("included").read(0..MAX_LAUNCHER_FOLDER_ITEMS) { it as String }
            require(validFolderApps(included, minimum = 0))
            LauncherItem.SmartFolder(LauncherCategory.valueOf(value.string("category")), name, included)
        }
        "workspace" -> {
            value.requireKeys(setOf("type"))
            LauncherItem.Workspace
        }
        else -> error("Invalid launcher item")
    }

    private fun validFolderName(name: String): Boolean =
        name.length <= 64 && name.isNotBlank() && name.none(Char::isISOControl)

    private fun validFolderApps(apps: List<String>, minimum: Int): Boolean =
        apps.size in minimum..MAX_LAUNCHER_FOLDER_ITEMS &&
            apps.distinct().size == apps.size && apps.all(LauncherAppIdentity::isValidKey)

    private inline fun <T> JSONArray.read(lengths: IntRange, convert: (Any) -> T): List<T> {
        require(length() in lengths)
        return List(length()) { convert(get(it)) }
    }

    private fun JSONObject.requireKeys(expected: Set<String>) {
        require(keys().asSequence().toSet() == expected)
    }

    private fun JSONObject.string(key: String): String = get(key) as String
    private fun JSONObject.boolean(key: String): Boolean = get(key) as Boolean
    private fun JSONObject.number(key: String): Int = get(key) as Int

    /** Bound nesting before asking the platform JSON reader to descend into an imported document. */
    private fun checkDepth(raw: String) {
        var inString = false
        var escaped = false
        var depth = 0
        for (character in raw) {
            if (inString) {
                require(character >= ' ')
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
            } else when (character) {
                '"' -> inString = true
                '{', '[' -> require(++depth <= 8)
                '}', ']' -> require(--depth >= 0)
                else -> require(character in " \t\r\n:,0123456789truefalsn")
            }
        }
        require(!inString && depth == 0)
    }
}
