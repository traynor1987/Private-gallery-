import os,pathlib,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release176ConfigurationTest(unittest.TestCase):
 def read(self,ref):
  return subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':ref},capture_output=True,text=True)
 def test_exact_installed40_to41_original_source(self):
  r=self.read('refs/heads/release/1.7.6-media-management');self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('36 1.7.6 41 1.7.5.2 40 ec05b23ec28a44730439bd1090c1e64afff8f140',r.stdout)
 def test_lookalike_branch_cannot_sign(self):self.assertNotEqual(0,self.read('refs/heads/release/1.7.6-media-management-untrusted').returncode)
if __name__=='__main__':unittest.main()
