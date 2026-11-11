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

internal data class GuardianConversationKey(val app: String, val profile: Long, val conversation: String)
internal data class GuardianContact(
    val key: GuardianConversationKey, val name: String,
    val enabled: Boolean = true, val retain: Boolean = true, val sensitivity: GuardianSensitivity = GuardianSensitivity.STANDARD,
    val holdUncertain: Boolean = false,
    val quietCollection: Boolean = false,
)
internal enum class GuardianSensitivity(val label: String, val threshold: Double) {
    CAUTIOUS("Fewer flags", .75), STANDARD("Standard", .62), SENSITIVE("More flags", .58),
}

internal fun guardianConversationId(shortcut: String?, senderKey: String?, senderUri: String?, group: Boolean): String? {
    val shortcutId = shortcut?.takeIf { it.isNotBlank() && it.length <= 480 }
    if (shortcutId != null) return "shortcut:$shortcutId"
    if (group) return null
    val key = senderKey?.takeIf { it.isNotBlank() && it.length <= 480 }
    val uri = senderUri?.takeIf { it.isNotBlank() && it.length <= 480 }
    return when {
        key != null -> "person-key:$key"
        uri != null -> "person-uri:$uri"
        else -> null
    }
}

private val possibleSmsCode = Regex("""(?i)\b(?:otp|passcode|verification|security code|authentication|sign.in code)\b|(?<!\d)\d{4,8}(?!\d)""")
internal fun guardianMayKeepSms(text: String) = text.isNotBlank() && !possibleSmsCode.containsMatchIn(text)

internal fun guardianCaptureAllowed(includeMain: Boolean, key: GuardianConversationKey, rule: GuardianContact?): Boolean =
    if (rule != null) rule.key == key && rule.enabled && rule.retain
    else includeMain && key.app == GUARDIAN_WHATSAPP

internal enum class GuardianCaptureStage(val label: String) {
    OFF("Main notification observed · capture off"),
    SOURCE("Main notification skipped · sender app or notification type could not be verified"),
    NO_MESSAGE("Main notification skipped · Android did not supply supported incoming message text"),
    CONSENT("Main notification skipped · capture scope or contact rule excluded it"),
    DUPLICATE("Main notification already retained, removed or outside the retention window"),
    PARTIAL("Main notification had unsupported or out-of-window message entries"),
    STORED("Main notification retained in the encrypted Inbox"),
    STORAGE("Encrypted archive write or read could not be verified"),
    QUEUE("Capture queue full"),
}
