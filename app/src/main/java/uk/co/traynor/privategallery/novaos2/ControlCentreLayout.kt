package uk.co.traynor.privategallery.novaos2

import uk.co.traynor.privategallery.launcher.LauncherAppIdentity

internal enum class ControlTile {
    INTERNET, VOLUME, FLASHLIGHT, ROTATION, SANCTUARY, WORKING, THEME, PRIVACY,
}

internal data class ControlPlacement(
    val order: List<ControlTile> = ControlTile.entries.toList(),
    val hidden: Set<ControlTile> = emptySet(),
    val pinnedApps: List<String> = emptyList(),
) {
    fun visible(available: Set<ControlTile>): List<ControlTile> = order.filter { it in available && it !in hidden }

    fun move(tile: ControlTile, offset: Int, available: Set<ControlTile> = ControlTile.entries.toSet()): ControlPlacement {
        val selectable = order.filter { it in available }
        val index = selectable.indexOf(tile)
        if (index < 0 || offset !in -1..1 || offset == 0) return this
        val neighbor = selectable.getOrNull(index + offset) ?: return this
        return copy(order = order.map {
            when (it) {
                tile -> neighbor
                neighbor -> tile
                else -> it
            }
        })
    }

    fun toggle(tile: ControlTile): ControlPlacement =
        copy(hidden = if (tile in hidden) hidden - tile else hidden + tile)

    fun toggleApp(component: String): ControlPlacement = when {
        component in pinnedApps -> copy(pinnedApps = pinnedApps - component)
        pinnedApps.size >= PIN_LIMIT || !validApp(component) -> this
        else -> copy(pinnedApps = pinnedApps + component)
    }

    companion object {
        const val PIN_LIMIT = 3

        fun validApp(component: String): Boolean = LauncherAppIdentity.isValidKey(component)
    }
}

internal data class ControlCentreLayout(
    val cover: ControlPlacement = ControlPlacement(),
    val inner: ControlPlacement = ControlPlacement(),
    val mediaEnabled: Boolean = false,
) {
    fun placement(wide: Boolean): ControlPlacement = if (wide) inner else cover

    fun changePlacement(wide: Boolean, change: (ControlPlacement) -> ControlPlacement): ControlCentreLayout =
        if (wide) copy(inner = change(inner)) else copy(cover = change(cover))

    fun encode(): String = listOf(
        SCHEMA, mediaEnabled.toString(), encodePlacement(cover), encodePlacement(inner),
    ).joinToString("\n")

    companion object {
        private const val SCHEMA = "control-centre-v1"
        private const val MAX_BYTES = 4096

        fun decode(value: String?): ControlCentreLayout {
            if (value == null) return ControlCentreLayout()
            require(value.length <= MAX_BYTES)
            val lines = value.split('\n')
            require(lines.size == 4 && lines[0] == SCHEMA)
            val mediaEnabled = requireNotNull(lines[1].toBooleanStrictOrNull())
            return ControlCentreLayout(decodePlacement(lines[2]), decodePlacement(lines[3]), mediaEnabled)
        }

        private fun encodePlacement(placement: ControlPlacement): String = listOf(
            placement.order.joinToString(",") { it.name },
            placement.hidden.joinToString(",") { it.name },
            placement.pinnedApps.joinToString(","),
        ).joinToString("|")

        private fun decodePlacement(value: String): ControlPlacement {
            val pieces = value.split('|')
            require(pieces.size == 3)
            val order = decodeTiles(pieces[0])
            val hidden = decodeTiles(pieces[1])
            val apps = if (pieces[2].isEmpty()) emptyList() else pieces[2].split(',')
            require(apps.size <= ControlPlacement.PIN_LIMIT && apps.distinct().size == apps.size &&
                apps.all { ControlPlacement.validApp(it) })
            return ControlPlacement(order + ControlTile.entries.filter { it !in order }, hidden.toSet(), apps)
        }

        private fun decodeTiles(value: String): List<ControlTile> {
            if (value.isEmpty()) return emptyList()
            val tiles = value.split(',').map(ControlTile::valueOf)
            require(tiles.distinct().size == tiles.size)
            return tiles
        }
    }
}
