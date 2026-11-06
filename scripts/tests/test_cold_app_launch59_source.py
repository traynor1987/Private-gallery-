"""Host guard for cold saved-app admission wiring; API37 tests execute the real Activity."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class ColdAppLaunchWiringTest(unittest.TestCase):
 def test_authenticated_launch_records_intent_before_network_readiness(self):
  text=(ROOT/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
  launch=text.split('}, onLaunchApp={app ->',1)[1].split('}, authenticate=',1)[0]
  self.assertNotIn('check(browserV2Session.mediaNetworkingAllowed())',launch)
  self.assertIn('queueNovaPrivateApp(app,current)',launch)
 def test_pending_launch_rechecks_original_authority_and_saved_definition(self):
  text=(ROOT/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
  self.assertTrue('internal fun retryPendingNovaPrivateApp()' in text,'Missing pending launch retry')
  retry=text.split('internal fun retryPendingNovaPrivateApp()',1)[1].split('\n    internal fun',1)[0]
  self.assertIn('pending.current()',retry)
  self.assertIn('mediaNetworkingAllowed()',retry)
  self.assertIn('latest!=pending.app',retry)
 def test_os_consent_keeps_only_original_admission_and_preserves_transfer_cleanup(self):
  text=(ROOT/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
  self.assertTrue('novaPrivateAppLaunch!==primaryAppVpnConsentOwner' in text)
  self.assertTrue('PendingNovaPrivateApp(app,pendingNovaAppAuthority(),true)' in text)
  capsule=text.split('private fun pendingNovaAppAuthority()',1)[1].split('private fun queueNovaPrivateApp',1)[0]
  self.assertTrue('captureAuthenticationEpoch()' in capsule and 'browserPresentationGeneration==presentation' in capsule)
  self.assertFalse('phoneTransferGeneration' in capsule)
  self.assertTrue('private fun revokePhoneTransfers() {synchronized(phoneTransferGate) {phoneTransferGeneration++}}' in text)
if __name__=='__main__':unittest.main()
