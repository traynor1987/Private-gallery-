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

    def test_primary_biometric_success_checks_original_crypto_before_provider_use(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        body = source.split('private fun newBiometricPrompt(', 1)[1].split('private val sourceDeletionLauncher', 1)[0]
        self.assertIn('biometricAttempts.begin(expectedCipher)', body)
        self.assertLess(body.index('biometricAttempts.claim(attempt, cipher)'), body.index('biometrics.unwrapAuthenticated(cipher)'))

    def test_settings_pin_comparison_keeps_original_owner_and_cleanup_barrier(self):
        source = (ROOT / 'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
        self.assertIn('onVerifySecretPin = { pin -> verifySecretPin(pin, uiOwner) }', source)
        body = source.split('private fun verifySecretPin(pin: CharArray, owner:', 1)[1].split('private fun verifySecretPin(pin: CharArray):', 1)[0]
        self.assertIn('owner?.operation(', body)
        self.assertNotIn('retained.authority.operationOrNull', body)
        self.assertIn('temporary.withProduced({ keys.unlock(pin) })', body)
        self.assertIn('temporary.retirement.await(', body)
