import os,pathlib,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release177ConfigurationTest(unittest.TestCase):
 def test_current_identity_and_original_code45_baseline(self):
  source=(ROOT/'app/build.gradle.kts').read_text()
  for text in ['versionName = "1.7.7"','versionCode = 46','minSdk = 37','targetSdk = 37','applicationId = "uk.co.traynor.privategallery"']:self.assertIn(text,source)
  r=subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.7-recently-deleted'},capture_output=True,text=True)
  self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('1.7.7 46 1.7.6.2 45 97e94452ceb9c5262521d7552f0c25eeb07374eb',r.stdout)
if __name__=='__main__':unittest.main()
