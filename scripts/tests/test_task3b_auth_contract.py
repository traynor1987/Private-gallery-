"""Admission guards supplement actual lifecycle/ownership JVM regressions."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]

class AuthenticationContractTest(unittest.TestCase):
    def test_primary_pin_entrypoints_keep_owned_key_handoff(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        self.assertFalse(bool(re.search(r'installAuthenticatedKey\(keys\.(create|unlock)\(', source)),
                         'Primary PIN entrypoints must keep the original result ownership through admission')

    def test_primary_biometric_success_keeps_borrow_retirement_and_final_owned_admission(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        body = source.split('private fun unlockWithBiometrics(', 1)[1].split('private fun enrollBiometrics(', 1)[0]
        steps = ('prepareThreadAuthentication(', 'prompt.runAuthenticated', 'biometrics.unwrapAuthenticated',
                 'awaitBiometricRetirement(prompt)', 'prompt.takeSuccessReceipt()', 'authority.completeAuthentication(attempt, handoff)')
        positions = [body.index(step) for step in steps]
        self.assertEqual(sorted(positions), positions,
                         'Primary biometric admission must follow the exact borrowed worker and real UI retirement')
        self.assertNotIn('installAuthenticatedKey(', body, 'Biometric admission cannot bypass the original owned handoff')
        core = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/core/security/OriginalBiometricLifecycle.kt').read_text()
        self.assertIn('crypto === expected', core, 'The SDK result must match the original exact Cipher')
        self.assertIn('callbackReturned', core, 'Callback dispatch is not callback return')

    def test_androidx_prompt_is_not_shared_with_main_activity(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        self.assertNotRegex(source, r'BiometricPrompt\s*\(\s*this\b',
                            'AndroidX1.1 uses an Activity ViewModel: Main must not share request callbacks')
        self.assertNotIn('prompt.cancelAuthentication()', source,
                         'Main cancellation must retire the exact owned lifecycle, never an ambient AndroidX prompt')
        host = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/OriginalBiometricActivity.kt').read_text()
        self.assertIn('BiometricPrompt(fragment,', host)
        callback = host.split('private class Callback(', 1)[1].split('companion object', 1)[0]
        for native in ('doFinal(', 'unwrapAuthenticated(', 'completeAuthentication(', '.join('):
            self.assertNotIn(native, callback, 'SDK callbacks must only signal the original detachable bridge')

    def test_settings_pin_comparison_keeps_original_owner_and_cleanup_barrier(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        self.assertIn('onVerifySecretPin = { pin -> verifySecretPin(pin, uiOwner) }', source)
        body = source.split('private fun verifySecretPin(pin: CharArray, owner:', 1)[1].split('private fun verifySecretPin(pin: CharArray):', 1)[0]
        self.assertIn('owner?.operation(', body)
        self.assertNotIn('retained.authority.operationOrNull', body)
        self.assertIn('temporary.withProduced({ keys.unlock(pin) })', body)
        self.assertIn('temporary.retirement.await(', body)

    def test_unaccepted_androidx_graph_is_denied_before_native_acquisition(self):
        base = ROOT / 'app/src/main/java/uk/co/traynor/privategallery'
        support = (base / 'core/security/AndroidXBiometricRetirementSupport.kt').read_text()
        self.assertIn('val available: Boolean get() = false', support,
                      'No configuration or hardware result can waive app-owned retirement')
        core = (base / 'core/security/OriginalBiometricLifecycle.kt').read_text()
        entry = core.split('fun begin(owner:', 1)[1].split('return OriginalBiometricLifecycle', 1)[0]
        self.assertLess(entry.index('check(backend.supportsAcknowledgedRetirement)'), entry.index('OriginalBiometricState('))
        provider = (base / 'core/security/BiometricVaultKeyStore.kt').read_text()
        for start, native in [('fun newEncryptCipher()', 'cipher(Cipher.ENCRYPT_MODE'),
                              ('fun newDecryptCipher()', 'cipher(Cipher.DECRYPT_MODE')]:
            body = provider.split(start, 1)[1].split('\n    }', 1)[0]
            self.assertLess(body.index('requireAdmission()'), body.index(native))
        hidden = (base / 'core/domain/SecondaryBiometricSlot.kt').read_text().split('internal class AndroidSecondaryBiometricKeys', 1)[1]
        for start in ('override fun create(', 'override fun decrypt('):
            body = hidden.split(start, 1)[1]
            self.assertLess(body.index('requireAdmission()'), body.index('store()'))
