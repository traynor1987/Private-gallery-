package uk.co.traynor.privategallery.launcher

import android.content.Context
import java.util.UUID

internal enum class WorkspaceLayoutMode { SEPARATE, MIRROR }
internal enum class WorkspaceLayoutRequest { USE_PRIMARY, COPY_TO_BOTH }

internal data class WorkspaceLayoutStatus(
    val mode: WorkspaceLayoutMode,
    val pending: WorkspaceLayoutRequest?,
    val requestId: String?,
)

internal data class WorkspacePrimaryCopy(val id: String, val layout: NovaLauncherState)

/**
 * The launcher remains the source of the mirrored layout. An encrypted Primary
 * layout is never read by this store. Mode changes and launcher snapshots share
 * the existing launcher preference transaction.
 */
internal class WorkspaceLayoutStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(LAUNCHER_LAYOUT_PREFS, Context.MODE_PRIVATE)

    fun read(): WorkspaceLayoutStatus = synchronized(NovaLauncherStore.lock) {
        val mode = if (prefs.getBoolean(MIRROR, false)) WorkspaceLayoutMode.MIRROR else WorkspaceLayoutMode.SEPARATE
        val pending = prefs.getString(REQUEST, null)?.let { value ->
            WorkspaceLayoutRequest.entries.find { it.name == value }
        }
        WorkspaceLayoutStatus(mode, pending, prefs.getString(REQUEST_ID, null))
    }

    fun useLauncher(): Boolean = synchronized(NovaLauncherStore.lock) {
        if (read().mode != WorkspaceLayoutMode.SEPARATE || primaryCopy() != null) return@synchronized false
        val state = loadLauncher() ?: return@synchronized false
        prefs.edit().putBoolean(MIRROR, true)
            .putString(PREVIOUS, encodeLayout(state))
            .remove(REQUEST).remove(REQUEST_ID).commit()
    }

    fun requestPrimary(): Boolean = request(WorkspaceLayoutMode.SEPARATE, WorkspaceLayoutRequest.USE_PRIMARY)
    fun requestCopyToBoth(): Boolean = request(WorkspaceLayoutMode.MIRROR, WorkspaceLayoutRequest.COPY_TO_BOTH)

    private fun request(mode: WorkspaceLayoutMode, request: WorkspaceLayoutRequest): Boolean =
        synchronized(NovaLauncherStore.lock) {
            if (read().mode != mode || primaryCopy() != null) return@synchronized false
            prefs.edit().putString(REQUEST, request.name)
                .putString(REQUEST_ID, UUID.randomUUID().toString()).commit()
        }

    fun cancelPending(): Boolean = synchronized(NovaLauncherStore.lock) {
        prefs.edit().remove(REQUEST).remove(REQUEST_ID).commit()
    }

    /** Called only from the currently authenticated Primary workspace. */
    fun usePrimary(requestId: String, layout: WorkspacePublicProjection, applicationId: String): Boolean =
        synchronized(NovaLauncherStore.lock) {
            if (read() != WorkspaceLayoutStatus(WorkspaceLayoutMode.SEPARATE, WorkspaceLayoutRequest.USE_PRIMARY, requestId)) {
                return@synchronized false
            }
            val before = loadLauncher() ?: return@synchronized false
            val after = before.mergePrimaryProjection(layout, applicationId)
            val encoded = runCatching { LauncherLayoutCodec.encode(after, portable = false) }.getOrNull()
                ?: return@synchronized false
            prefs.edit().putString(LAUNCHER_STATE_KEY, encoded).putBoolean(MIRROR, true)
                .putString(PREVIOUS, encodeLayout(before)).remove(REQUEST).remove(REQUEST_ID).commit()
        }

    /** Publish a frozen public layout for Primary to materialize inside its encrypted preferences. */
    fun copyToBoth(requestId: String): Boolean = synchronized(NovaLauncherStore.lock) {
        if (read() != WorkspaceLayoutStatus(WorkspaceLayoutMode.MIRROR, WorkspaceLayoutRequest.COPY_TO_BOTH, requestId)) {
            return@synchronized false
        }
        val state = loadLauncher() ?: return@synchronized false
        prefs.edit().putBoolean(MIRROR, false)
            .putString(PRIMARY_COPY, encodeLayout(state)).putString(PRIMARY_COPY_ID, requestId)
            .remove(REQUEST).remove(REQUEST_ID).commit()
    }

    fun primaryCopy(): WorkspacePrimaryCopy? = synchronized(NovaLauncherStore.lock) {
        val id = prefs.getString(PRIMARY_COPY_ID, null) ?: return@synchronized null
        val copy = decodeLayout(prefs.getString(PRIMARY_COPY, null)) ?: return@synchronized null
        WorkspacePrimaryCopy(id, copy)
    }

    fun acknowledgePrimaryCopy(id: String): Boolean = synchronized(NovaLauncherStore.lock) {
        if (prefs.getString(PRIMARY_COPY_ID, null) != id) return@synchronized false
        prefs.edit().remove(PRIMARY_COPY).remove(PRIMARY_COPY_ID).commit()
    }

    fun hasEarlierLauncherLayout(): Boolean = synchronized(NovaLauncherStore.lock) {
        read().mode == WorkspaceLayoutMode.SEPARATE && prefs.contains(PREVIOUS) && primaryCopy() == null
    }

    fun restorePrevious(): Boolean = synchronized(NovaLauncherStore.lock) {
        val status = read()
        if (status.mode != WorkspaceLayoutMode.MIRROR && !hasEarlierLauncherLayout()) return@synchronized false
        val previous = decodeLayout(prefs.getString(PREVIOUS, null)) ?: return@synchronized false
        val current = loadLauncher() ?: return@synchronized false
        val restored = current.copy(
            cover = previous.cover.withWidgetPages(current.widgets.filterNot(LauncherWidget::wide)),
            inner = previous.inner.withWidgetPages(current.widgets.filter(LauncherWidget::wide)),
            separateDisplays = previous.separateDisplays,
            coverCleanupUndo = current.coverCleanupUndo.takeIf { current.cover.pages == previous.cover.pages },
            innerCleanupUndo = current.innerCleanupUndo.takeIf { current.inner.pages == previous.inner.pages },
            appearance = current.appearance.copy(
                wallpaper = previous.appearance.wallpaper,
                grid = previous.appearance.grid,
                iconSize = previous.appearance.iconSize,
            ),
        )
        val encoded = runCatching { LauncherLayoutCodec.encode(restored, portable = false) }.getOrNull()
            ?: return@synchronized false
        prefs.edit().putBoolean(MIRROR, false).putString(LAUNCHER_STATE_KEY, encoded)
            .remove(PREVIOUS).remove(REQUEST).remove(REQUEST_ID).commit()
    }

    private fun loadLauncher(): NovaLauncherState? {
        val raw = prefs.getString(LAUNCHER_STATE_KEY, null) ?: return NovaLauncherState()
        return runCatching { LauncherLayoutCodec.decode(raw, portable = false) }.getOrNull()
    }

    private fun encodeLayout(state: NovaLauncherState): String = LauncherLayoutCodec.encode(
        NovaLauncherState(cover = state.cover, inner = state.inner, separateDisplays = state.separateDisplays,
            appearance = LauncherAppearance(wallpaper = state.appearance.wallpaper,
                grid = state.appearance.grid, iconSize = state.appearance.iconSize)),
        portable = true,
    )

    private fun decodeLayout(raw: String?): NovaLauncherState? = raw?.let {
        runCatching { LauncherLayoutCodec.decode(it, portable = true) }.getOrNull()
    }

    private companion object {
        const val MIRROR = "workspace_layout_mirror"
        const val REQUEST = "workspace_layout_request"
        const val REQUEST_ID = "workspace_layout_request_id"
        const val PREVIOUS = "workspace_layout_previous"
        const val PRIMARY_COPY = "workspace_layout_primary_copy"
        const val PRIMARY_COPY_ID = "workspace_layout_primary_copy_id"
    }
}
