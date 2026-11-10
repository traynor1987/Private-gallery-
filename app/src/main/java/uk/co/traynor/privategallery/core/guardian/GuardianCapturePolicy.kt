package uk.co.traynor.privategallery.core.guardian

internal const val GUARDIAN_WHATSAPP = "com.whatsapp"

internal fun canCaptureGuardianNotification(
    enabled: Boolean,
    packageName: String,
    account: ProbeAccount,
    summary: Boolean,
    call: Boolean = false,
): Boolean = enabled && packageName == GUARDIAN_WHATSAPP && account == ProbeAccount.MAIN && !summary && !call

internal enum class GuardianCallSource(val label: String) {
    PHONE("Default phone app"),
    MAIN("Main WhatsApp"),
    FRIENDS("Friends WhatsApp"),
    UNRESOLVED("Unresolved WhatsApp"),
}

internal fun guardianCallSource(
    packageName: String, defaultDialer: String?, personal: Boolean, account: ProbeAccount,
): GuardianCallSource? = when {
    packageName == GUARDIAN_WHATSAPP -> when (account) {
        ProbeAccount.MAIN -> GuardianCallSource.MAIN
        ProbeAccount.FRIENDS -> GuardianCallSource.FRIENDS
        ProbeAccount.UNRESOLVED -> GuardianCallSource.UNRESOLVED
    }
    personal && packageName == defaultDialer -> GuardianCallSource.PHONE
    else -> null
}

internal fun guardianMayDismissMessage(
    enabled: Boolean, packageName: String, account: ProbeAccount, summary: Boolean,
    call: Boolean, personalUidMatches: Boolean, clearable: Boolean, category: String?,
): Boolean = canCaptureGuardianNotification(enabled, packageName, account, summary, call) &&
    personalUidMatches && clearable && category == "msg"
