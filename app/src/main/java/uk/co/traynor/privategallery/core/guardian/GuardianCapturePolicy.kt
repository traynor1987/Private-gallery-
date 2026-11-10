package uk.co.traynor.privategallery.core.guardian

internal const val GUARDIAN_WHATSAPP = "com.whatsapp"

internal fun canCaptureGuardianNotification(
    enabled: Boolean,
    packageName: String,
    account: ProbeAccount,
    summary: Boolean,
    call: Boolean = false,
): Boolean = enabled && packageName == GUARDIAN_WHATSAPP && account == ProbeAccount.MAIN && !summary && !call
