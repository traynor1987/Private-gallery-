package uk.co.traynor.privategallery.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

internal fun readDefaultHome(context: Context): Boolean? = runCatching {
    context.getSystemService(RoleManager::class.java)
        ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
        ?.isRoleHeld(RoleManager.ROLE_HOME)
}.getOrNull() ?: runCatching {
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val flags = PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
    context.packageManager.resolveActivity(homeIntent, flags)?.activityInfo?.packageName
        ?.takeUnless { it == "android" }
        ?.let { it == context.packageName }
}.getOrNull()

internal fun requestDefaultHomeRole(context: Context, launch: (Intent) -> Unit): Boolean = runCatching {
    val roles = checkNotNull(context.getSystemService(RoleManager::class.java))
    check(roles.isRoleAvailable(RoleManager.ROLE_HOME))
    launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
}.isSuccess
