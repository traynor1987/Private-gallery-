"""Configuration identity only; this never activates a signer or approves release."""
from pathlib import Path
import os
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[2]
class Configuration179(unittest.TestCase):
    def run_config(self, ref='refs/heads/release/1.7.9-evidence-locker', baseline='50'):
        env=dict(os.environ,GITHUB_REF=ref,PRIVATE_GALLERY_UPGRADE_BASELINE=baseline)
        return subprocess.run(['bash','-c','source scripts/release_configuration.sh\nprintf "%s\\n" "$release_min_sdk" "$release_version" "$release_code" "$baseline_version" "$baseline_code" "$baseline_sha"'],cwd=ROOT,env=env,text=True,capture_output=True)
    def test_exact_179_from_archived_50_only(self):
        result=self.run_config()
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout.splitlines(),['37','1.7.9','51','1.7.8','50','4820e176dcb60a3aa2f1bfd7b36e2de19fafee11'])
    def test_unsupported_baseline_and_untrusted_ref_refused(self):
        for baseline in ['49','51','0','50\n']:
            self.assertNotEqual(self.run_config(baseline=baseline).returncode,0)
        self.assertNotEqual(self.run_config(ref='refs/pull/1/head').returncode,0)
    def test_existing178_identity_unchanged(self):
        result=self.run_config(ref='refs/heads/release/1.7.8-private-apps',baseline='49')
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(result.stdout.splitlines(),['37','1.7.8','50','1.7.8','49','60f089d6e003fd13f8b7649feb6f44167e5028a2'])
if __name__=='__main__': unittest.main()
