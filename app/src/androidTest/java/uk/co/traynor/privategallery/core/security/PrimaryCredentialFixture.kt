package uk.co.traynor.privategallery.core.security

import android.content.Context
import uk.co.traynor.privategallery.BuildConfig

/** Explicit cleanup of synthetic credentials. Production rollback APIs retain ownership checks. */
internal object PrimaryCredentialFixture {
    fun clearPin(context: Context) {
        check(BuildConfig.HIDDEN_PROTOTYPE)
        // These tests create a fresh disposable Primary Vault. Its ciphertext must not
        // outlive the fixture credentials and masquerade as an owner Vault in the next test.
        for (name in listOf("vault", "vault-restore-staging")) {
            val root = java.io.File(context.filesDir, name)
            check(!root.exists() || root.deleteRecursively())
        }
        clear(context, "vault-key-envelope")
    }
    fun clearRecovery(context: Context) = clear(context, "vault-recovery-envelope")

    private fun clear(context: Context, name: String) {
        check(BuildConfig.HIDDEN_PROTOTYPE) { "Credential fixture cleanup requires the isolated test application" }
        check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
    }
}
