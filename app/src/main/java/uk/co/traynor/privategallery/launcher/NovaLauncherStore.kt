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

    fun read(): NovaLauncherState = synchronized(lock) {
        val raw = prefs.getString("state", null) ?: return@synchronized NovaLauncherState()
        runCatching { LauncherLayoutCodec.decode(raw, portable = false) }.getOrDefault(NovaLauncherState())
    }

    fun save(state: NovaLauncherState): Boolean = synchronized(lock) {
        val encoded = runCatching { LauncherLayoutCodec.encode(state, portable = false) }.getOrNull()
            ?: return@synchronized false
        prefs.edit().putString("state", encoded).commit()
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
        save(decoded.copy(configured = current.configured, widgets = current.widgets, launchCounts = current.launchCounts))
    }

    private companion object {
        val lock = Any()
    }
}

private object LauncherLayoutCodec {
    private const val SCHEMA = 1
    private const val TYPE = "nova-launcher-layout"
    private val layoutKeys = setOf("type", "schema", "separateDisplays", "cover", "inner", "appearance", "drawer", "gesture", "hideBlocked")
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

        if (!portable) {
            require(state.widgets.size <= MAX_LAUNCHER_WIDGETS && state.widgets.distinctBy(LauncherWidget::id).size == state.widgets.size)
            val widgets = JSONArray()
            for (widget in state.widgets) {
                require(widget.id > 0 && isLauncherComponent(widget.provider))
                widgets.put(JSONObject().put("id", widget.id).put("provider", widget.provider).put("wide", widget.wide))
            }
            require(state.launchCounts.size <= MAX_LAUNCH_COUNTS)
            val counts = JSONObject()
            for ((component, count) in state.launchCounts) {
                require(isLauncherComponent(component) && count in 1..MAX_LAUNCH_COUNT)
                counts.put(component, count)
            }
            root.put("configured", state.configured).put("widgets", widgets).put("launchCounts", counts)
        }

        return root.toString().also { require(it.encodeToByteArray().size <= MAX_LAUNCHER_BACKUP_BYTES) }
    }

    fun decode(raw: String, portable: Boolean): NovaLauncherState {
        require(raw.length in 1..MAX_LAUNCHER_BACKUP_BYTES)
        checkDepth(raw)
        val reader = JSONTokener(raw)
        val root = reader.nextValue() as? JSONObject ?: error("Invalid launcher file")
        require(reader.nextClean() == '\u0000')
        root.requireKeys(if (portable) layoutKeys else layoutKeys + localKeys)
        require(root.string("type") == TYPE && root.number("schema") == SCHEMA)

        val appearance = root.getJSONObject("appearance")
        appearance.requireKeys(setOf("wallpaper", "theme", "accent", "iconShape", "iconSize", "labels", "grid", "dock"))
        val drawer = root.getJSONObject("drawer")
        drawer.requireKeys(setOf("orientation", "search", "sort", "folders", "frequent", "shortcutSearch"))

        val widgets = if (portable) emptyList() else root.getJSONArray("widgets").read(0..MAX_LAUNCHER_WIDGETS) { widget ->
            (widget as JSONObject).requireKeys(setOf("id", "provider", "wide"))
            LauncherWidget(
                widget.number("id").also { require(it > 0) },
                widget.string("provider").also { require(isLauncherComponent(it)) },
                widget.boolean("wide"),
            )
        }.also { require(it.distinctBy(LauncherWidget::id).size == it.size) }

        val counts = if (portable) emptyMap() else root.getJSONObject("launchCounts").let { values ->
            require(values.length() <= MAX_LAUNCH_COUNTS)
            values.keys().asSequence().associateWith { key ->
                require(isLauncherComponent(key))
                values.number(key).also { require(it in 1..MAX_LAUNCH_COUNT) }
            }
        }

        return NovaLauncherState(
            configured = !portable && root.boolean("configured"),
            separateDisplays = root.boolean("separateDisplays"),
            cover = parseLayout(root.getJSONObject("cover")),
            inner = parseLayout(root.getJSONObject("inner")),
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
        )
    }

    private fun layout(value: LauncherLayout): JSONObject {
        require(value.pages.size in 1..MAX_LAUNCHER_PAGES && value.dock.size <= MAX_LAUNCHER_DOCK_ITEMS)
        require(value.favourites.size <= MAX_LAUNCHER_FAVOURITES && value.favourites.all(::isLauncherComponent))
        val pages = JSONArray()
        for (page in value.pages) {
            require(page.size <= MAX_LAUNCHER_PAGE_ITEMS)
            pages.put(JSONArray(page.map(::item)))
        }
        return JSONObject()
            .put("pages", pages)
            .put("dock", JSONArray(value.dock.map(::item)))
            .put("favourites", JSONArray(value.favourites))
    }

    private fun item(value: LauncherItem): JSONObject = when (value) {
        is LauncherItem.App -> {
            require(isLauncherComponent(value.component))
            JSONObject().put("type", "app").put("component", value.component)
        }
        is LauncherItem.Folder -> {
            require(validFolder(value.name, value.apps))
            JSONObject().put("type", "folder").put("name", value.name).put("apps", JSONArray(value.apps))
        }
        LauncherItem.Workspace -> JSONObject().put("type", "workspace")
    }

    private fun parseLayout(value: JSONObject): LauncherLayout {
        value.requireKeys(setOf("pages", "dock", "favourites"))
        return LauncherLayout(
            pages = value.getJSONArray("pages").read(1..MAX_LAUNCHER_PAGES) { page ->
                (page as JSONArray).read(0..MAX_LAUNCHER_PAGE_ITEMS) { parseItem(it as JSONObject) }
            },
            dock = value.getJSONArray("dock").read(0..MAX_LAUNCHER_DOCK_ITEMS) { parseItem(it as JSONObject) },
            favourites = value.getJSONArray("favourites").read(0..MAX_LAUNCHER_FAVOURITES) {
                (it as String).also { component -> require(isLauncherComponent(component)) }
            }.toSet(),
        )
    }

    private fun parseItem(value: JSONObject): LauncherItem = when (value.string("type")) {
        "app" -> {
            value.requireKeys(setOf("type", "component"))
            LauncherItem.App(value.string("component").also { require(isLauncherComponent(it)) })
        }
        "folder" -> {
            value.requireKeys(setOf("type", "name", "apps"))
            val name = value.string("name")
            val apps = value.getJSONArray("apps").read(1..MAX_LAUNCHER_FOLDER_ITEMS) { it as String }
            require(validFolder(name, apps))
            LauncherItem.Folder(name, apps)
        }
        "workspace" -> {
            value.requireKeys(setOf("type"))
            LauncherItem.Workspace
        }
        else -> error("Invalid launcher item")
    }

    private fun validFolder(name: String, apps: List<String>): Boolean =
        name.length <= 64 && name.isNotBlank() && name.none(Char::isISOControl) &&
            apps.size in 1..MAX_LAUNCHER_FOLDER_ITEMS && apps.distinct().size == apps.size && apps.all(::isLauncherComponent)

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
