package uk.co.traynor.privategallery.core.security

import android.content.Context
import java.io.File

/** Fixed compatibility root. Identity is checked before context, slots or files are consulted. */
internal class LegacyPrimaryContainer(context: Context, containerId: ContainerId = ContainerId.PRIMARY) {
    init { check(containerId === ContainerId.PRIMARY) { "Only Primary storage exists" } }
    val containerId = ContainerId.PRIMARY
    val root = File(context.filesDir, "vault")
    val restoreStaging = File(context.filesDir, "vault-restore-staging")
}

enum class PrimaryVaultState { EMPTY, LOCKED, UNAVAILABLE, CORRUPT, READY }
