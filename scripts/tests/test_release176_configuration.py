import os,pathlib,subprocess,unittest,re
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Release176ConfigurationTest(unittest.TestCase):
 def read(self,ref):
  return subprocess.run(['bash','-c','source scripts/release_configuration.sh; printf "%s %s %s %s %s %s" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env={**os.environ,'GITHUB_REF':ref},capture_output=True,text=True)
 def test_exact_installed40_to41_original_source(self):
  r=self.read('refs/heads/release/1.7.6-media-management');self.assertEqual(0,r.returncode,r.stderr)
  self.assertEqual('36 1.7.6 41 1.7.5.2 40 ec05b23ec28a44730439bd1090c1e64afff8f140',r.stdout)
 def test_lookalike_branch_cannot_sign(self):self.assertNotEqual(0,self.read('refs/heads/release/1.7.6-media-management-untrusted').returncode)

 def test_upgrade_fixture_allows_only_reviewed_exact_version_pairs(self):
  source=(ROOT/'scripts/upgrade/UpgradeFixtureTest.kt').read_text()
  pairs={(int(a),int(b)) for a,b in re.findall(r'baselineCode == (\d+) && candidateCode == (\d+)',source)}
  self.assertEqual({(a,a+1) for a in range(32,42)},pairs)
  self.assertIn('check(context.packageName == "uk.co.traynor.privategallery.hiddenprototype")',source)
  runner=(ROOT/'scripts/verify_synthetic_upgrade.sh').read_text()
  self.assertIn('ro.kernel.qemu',runner)
  self.assertIn('run_fixture seed',runner);self.assertIn('run_fixture verify after-restart',runner)

if __name__=='__main__':unittest.main()
