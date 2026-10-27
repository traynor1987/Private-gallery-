import os, pathlib, subprocess, unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release1762ConfigurationTest(unittest.TestCase):
 def test_1762_package_platform_and_upgrade_baseline(self):
  source=(ROOT/'app/build.gradle.kts').read_text()
  self.assertIn('applicationId = "uk.co.traynor.privategallery"',source)
  self.assertIn('versionCode = 43',source)
  self.assertIn('versionName = "1.7.6.2"',source)
  self.assertIn('minSdk = 37',source)
  r=subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.6.2-ui-browser-polish'},capture_output=True,text=True)
  self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('37 1.7.6.2 43 1.7.6 41 180a7ce7048f56371d1e0566b6333de65fd1aa75',r.stdout)
 def test_original_key_upgrade_can_cover_both_recorded_installed_candidates(self):
  command='source scripts/release_configuration.sh; printf "%s %s %s" "$baseline_version" "$baseline_code" "$baseline_sha"'
  base={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.6.2-ui-browser-polish'}
  r=subprocess.run(['bash','-c',command],cwd=ROOT,env={**base,'PRIVATE_GALLERY_UPGRADE_BASELINE':'42'},capture_output=True,text=True)
  self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('1.7.6.1 42 33e9669daeb3bf5a3d2cb0aae4d3480fe4b538c2',r.stdout)
  bad=subprocess.run(['bash','-c',command],cwd=ROOT,env={**base,'PRIVATE_GALLERY_UPGRADE_BASELINE':'unknown'},capture_output=True,text=True)
  self.assertNotEqual(0,bad.returncode)
if __name__=='__main__':unittest.main()
