package uk.co.traynor.privategallery.core.guardian

internal enum class GuardianCallMode(val label: String) {
    ALLOW("Allow normally"), SILENCE("Silence"), BLOCK("Block"),
}

internal data class GuardianPhoneRule(
    val number: String,
    val mode: GuardianCallMode,
    val mainWhatsApp: GuardianCallMode = GuardianCallMode.ALLOW,
    val friendsWhatsApp: GuardianCallMode = GuardianCallMode.ALLOW,
)

private val dialNumber = Regex("[+0-9 ()-]+")

internal fun guardianPhoneNumber(raw: String): String? {
    val input = raw.trim()
    if (!dialNumber.matches(input)) return null
    val number = input.filterNot { it in " ()-" }
    return number.takeIf { it.matches(Regex("\\+?[0-9]{7,15}")) }
}

internal fun guardianPhoneAction(
    enabled: Boolean,
    roleHeld: Boolean,
    number: String?,
    rules: List<GuardianPhoneRule>,
): GuardianCallMode {
    if (!enabled || !roleHeld) return GuardianCallMode.ALLOW
    val exact = number?.let(::guardianPhoneNumber) ?: return GuardianCallMode.ALLOW
    return rules.singleOrNull { it.number == exact }?.mode ?: GuardianCallMode.ALLOW
}

internal fun guardianWhatsAppCallAction(
    enabled: Boolean,
    account: ProbeAccount,
    incoming: Boolean,
    callerUri: String?,
    rules: List<GuardianPhoneRule>,
): GuardianCallMode {
    if (!enabled || !incoming) return GuardianCallMode.ALLOW
    val number = callerUri?.takeIf { it.startsWith("tel:") }?.removePrefix("tel:")
        ?.let(::guardianPhoneNumber) ?: return GuardianCallMode.ALLOW
    val rule = rules.singleOrNull { it.number == number } ?: return GuardianCallMode.ALLOW
    return when (account) {
        ProbeAccount.MAIN -> rule.mainWhatsApp
        ProbeAccount.FRIENDS -> rule.friendsWhatsApp
        ProbeAccount.UNRESOLVED -> GuardianCallMode.ALLOW
    }
}

internal fun strengthenGuardianCallMode(
    saved: GuardianCallMode,
    activeMode: String?,
    selectedModes: Set<String>,
): GuardianCallMode = if (saved != GuardianCallMode.ALLOW && activeMode in selectedModes)
    GuardianCallMode.BLOCK else saved
