import os,pathlib,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
BRANCH='refs/heads/release/1.7.5.2-recents-browser-repair'
class Release1752ConfigurationTest(unittest.TestCase):
 def read(self,ref):
  return subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':ref},capture_output=True,text=True)
 def test_exact_installed39_to40_original_source(self):
  r=self.read(BRANCH)
  self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('36 1.7.5.2 40 1.7.5.1 39 00509aa729d0aba4283fa0d000186153984cf87f',r.stdout)
 def test_lookalike_branch_cannot_sign(self):
  self.assertNotEqual(0,self.read(BRANCH+'-untrusted').returncode)
if __name__=='__main__':unittest.main()
