import os,pathlib,subprocess,unittest,re
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release1761ConfigurationTest(unittest.TestCase):
 def config(self,ref):
  return subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':ref},capture_output=True,text=True)
 def test_exact_original_key_41_to42_profile(self):
  r=self.config('refs/heads/release/1.7.6.1-editing-settings-polish');self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('37 1.7.6.1 42 1.7.6 41 180a7ce7048f56371d1e0566b6333de65fd1aa75',r.stdout)
 def test_lookalike_branch_never_signs(self):self.assertNotEqual(0,self.config('refs/heads/release/1.7.6.1-editing-settings-polish-untrusted').returncode)
 def test_single_controlled_trigger_and_original_security_upgrade_gates(self):
  s=(ROOT/'.github/workflows/acceptance1761.yml').read_text()
  self.assertIn("paths: ['.release1761-candidate']",s)
  self.assertIn('Verify original permanent signing identity is available',s)
  self.assertIn('Require original-key production upgrade and restart proofs on Android 17',s)
  self.assertIn('Verify original-key production upgrade and restart on Android 17',s)
  self.assertIn('Remove restored signing material',s)
  self.assertNotIn('continue-on-error: true',s)
 def test_real_update_version_and_package_identity_not_decoy(self):
  main=(ROOT/'app/src/main/java/uk/co/traynor/privategallery/MainActivity.kt').read_text()
  self.assertIn('GithubReleaseUpdateService().check(BuildConfig.VERSION_NAME)',main)
  gradle=(ROOT/'app/build.gradle.kts').read_text();self.assertIn('applicationId = "uk.co.traynor.privategallery"',gradle)
  self.assertIn('versionName = "1.7.6.1"',gradle);self.assertIn('versionCode = 42',gradle)

 def test_owner_policy_runs_only_android17_and_preserves_security(self):
  s=(ROOT/'.github/workflows/acceptance1761.yml').read_text()
  self.assertNotIn('api-level: 36',s)
  self.assertNotIn('Full Android 16',s)
  self.assertIn('HiddenRuntimeFailureTest',s)
  self.assertIn('verify_android17_sdk.py',s)
  gradle=(ROOT/'app/build.gradle.kts').read_text()
  self.assertIn('minSdk = 37',gradle);self.assertIn('targetSdk = 37',gradle)

 def test_configuration_script_has_a_working_unittest_entrypoint(self):
  r=subprocess.run(['python3',str(ROOT/'scripts/tests/test_release1761_configuration.py'),'--help'],capture_output=True,text=True)
  self.assertEqual(0,r.returncode,r.stderr);self.assertIn('usage:',r.stdout)

if __name__=='__main__':unittest.main()
