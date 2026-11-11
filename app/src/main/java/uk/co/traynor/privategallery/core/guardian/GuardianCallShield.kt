package uk.co.traynor.privategallery.core.guardian

internal enum class GuardianCallMode(val label: String) {
    ALLOW("Allow normally"), SILENCE("Silence"), BLOCK("Block"),
}

internal data class GuardianPhoneRule(val number: String, val mode: GuardianCallMode)

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
