import os,pathlib,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release1751ConfigurationTest(unittest.TestCase):
 def test_exact_installed38_to39_original_source(self):
  result=subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s" "$release_version" "$release_code" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':'refs/heads/release/1.7.5.1-privacy-viewer-repair'},capture_output=True,text=True)
  self.assertEqual(0,result.returncode,result.stderr)
  self.assertEqual('1.7.5.1 39 38 69bfade789df4a49d911091bb8cf8205c88a3a39',result.stdout)
if __name__=='__main__':unittest.main()
