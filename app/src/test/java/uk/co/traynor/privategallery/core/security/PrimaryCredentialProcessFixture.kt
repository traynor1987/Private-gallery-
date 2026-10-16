package uk.co.traynor.privategallery.core.security

/** Synthetic process injection only. The production factory is fixed to its singleton;
 * no failed state, receipt, charge or Native/private platform state is reset here. */
internal fun syntheticPreparedPinWrite(operation: PrimaryOperation, original: ReleaseReservation,
    purpose: PrimaryCredentialPurpose, registry: PrimaryCredentialMutationRegistry): PreparedPrimaryPinWrite {
    val constructor = PreparedPrimaryPinWrite::class.java.getDeclaredConstructor(PrimaryOperation::class.java,
        ReleaseReservation::class.java, PrimaryCredentialPurpose::class.java, PrimaryCredentialMutationRegistry::class.java)
    constructor.isAccessible = true
    return try { constructor.newInstance(operation, original, purpose, registry) }
    catch (failure: java.lang.reflect.InvocationTargetException) { throw checkNotNull(failure.cause) }
}

internal fun syntheticPersistBiometricPreferences(registry: PrimaryBiometricPreferenceRegistry,
    operation: PrimaryOperation, prepare: () -> android.content.SharedPreferences.Editor) {
    val method = Class.forName("uk.co.traynor.privategallery.core.security.PrimaryBiometricPreferenceWriteKt")
        .getDeclaredMethod("persistPrimaryBiometricPreferencesIn", PrimaryOperation::class.java,
            Function0::class.java, PrimaryBiometricPreferenceRegistry::class.java)
    method.isAccessible = true
    try { method.invoke(null, operation, prepare, registry) }
    catch (failure: java.lang.reflect.InvocationTargetException) { throw checkNotNull(failure.cause) }
}
