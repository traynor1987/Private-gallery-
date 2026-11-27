package uk.co.traynor.privategallery.novaos2.desktop

import org.json.JSONArray
import org.json.JSONObject

internal const val DESKTOP_MIN_RATIO = 30
internal const val DESKTOP_MAX_RATIO = 70
internal const val DESKTOP_MAX_PINS = 8
internal const val DESKTOP_MAX_COMPONENT_LENGTH = 256

internal enum class DesktopPanel(val label: String) {
    NOVA_APPS("Nova apps"), PINNED("Pinned apps"), FILES("Files"), CALCULATOR("Calculator"),
}

internal enum class DesktopPreset(val label: String, val left: DesktopPanel, val right: DesktopPanel) {
    PRODUCTIVITY("Productivity", DesktopPanel.NOVA_APPS, DesktopPanel.PINNED),
    DEVELOPMENT("Development", DesktopPanel.FILES, DesktopPanel.CALCULATOR),
    GAMING("Gaming", DesktopPanel.PINNED, DesktopPanel.NOVA_APPS),
}

internal data class DesktopLayout(
    val left: DesktopPanel,
    val right: DesktopPanel,
    val splitPercent: Int = 50,
    val pins: List<String> = emptyList(),
) {
    init {
        require(left != right && splitPercent in DESKTOP_MIN_RATIO..DESKTOP_MAX_RATIO)
        require(pins.size <= DESKTOP_MAX_PINS && pins.distinct().size == pins.size)
        require(pins.all { it.length in 3..DESKTOP_MAX_COMPONENT_LENGTH && '/' in it && '#' !in it })
    }

    fun choosePanel(leftPane: Boolean, panel: DesktopPanel): DesktopLayout = when {
        leftPane && panel == right || !leftPane && panel == left -> copy(left = right, right = left)
        leftPane -> copy(left = panel)
        else -> copy(right = panel)
    }

    fun resize(percent: Int) = copy(splitPercent = percent.coerceIn(DESKTOP_MIN_RATIO, DESKTOP_MAX_RATIO))

    fun pin(component: String): DesktopLayout =
        if (component in pins || pins.size == DESKTOP_MAX_PINS) this else copy(pins = pins + component)

    fun reorder(component: String, offset: Int): DesktopLayout {
        val index = pins.indexOf(component)
        if (index < 0 || index + offset !in pins.indices) return this
        val ordered = pins.toMutableList().apply { add(index + offset, removeAt(index)) }
        return copy(pins = ordered)
    }
}

internal data class DesktopSettings(
    val preset: DesktopPreset = DesktopPreset.PRODUCTIVITY,
    val layouts: Map<DesktopPreset, DesktopLayout> = emptyMap(),
) {
    val current: DesktopLayout get() = layouts[preset] ?: DesktopLayout(preset.left, preset.right)

    fun update(layout: DesktopLayout): DesktopSettings = copy(layouts = layouts + (preset to layout))

    fun encode(): String = JSONObject().put("version", 1).put("preset", preset.name)
        .put("layouts", JSONObject().apply {
            layouts.forEach { (key, layout) ->
                put(key.name, JSONObject().put("left", layout.left.name).put("right", layout.right.name)
                    .put("split", layout.splitPercent).put("pins", JSONArray(layout.pins)))
            }
        }).toString()

    companion object {
        fun decode(text: String?): DesktopSettings {
            if (text == null) return DesktopSettings()
            val json = JSONObject(text)
            require(json.getInt("version") == 1)
            val all = json.getJSONObject("layouts")
            return DesktopSettings(DesktopPreset.valueOf(json.getString("preset")),
                DesktopPreset.entries.mapNotNull { preset ->
                    val saved = all.optJSONObject(preset.name) ?: return@mapNotNull null
                    val pins = saved.getJSONArray("pins")
                    require(pins.length() <= DESKTOP_MAX_PINS)
                    preset to DesktopLayout(
                        DesktopPanel.valueOf(saved.getString("left")), DesktopPanel.valueOf(saved.getString("right")),
                        saved.getInt("split"), List(pins.length()) { pins.getString(it) },
                    )
                }.toMap())
        }
    }
}
