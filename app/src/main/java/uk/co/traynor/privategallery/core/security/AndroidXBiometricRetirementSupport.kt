package uk.co.traynor.privategallery.core.security

/** Fixed security admission, not an owner-configurable feature flag.
 * AndroidX biometric1.1.0 has queued Fragment/ViewModel/CryptoObject references and
 * callback ingress without a supported public close/quiescence acknowledgement.
 * Activity destruction, ViewModelStore.clear and elapsed time do not discharge
 * those app-owned obligations. Keep acquisition denied until an audited adapter
 * actually controls and acknowledges them. This is NOT the Keystore exception.
 */
internal object AndroidXBiometricRetirementSupport {
    val available: Boolean get() = false
    fun requireAdmission() {
        check(available) { "Biometric app-owned retirement unavailable" }
    }
}
