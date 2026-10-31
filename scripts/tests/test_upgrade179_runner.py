"""Production upgrade admission guards must reject before any adb operation."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
class Runner179(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.path=Path(self.tmp.name);self.marker=self.path/'adb-called'
        tool=self.path/'adb';tool.write_text('#!/bin/sh\nprintf invoked > "$UPGRADE179_TEST_MARKER"\nexit 99\n');tool.chmod(0o755)
        self.apk=self.path/'wrong.apk';self.apk.write_bytes(b'synthetic incorrect baseline')
        self.env=dict(os.environ,GITHUB_ACTIONS='true',GITHUB_EVENT_NAME='push',GITHUB_REPOSITORY='traynor1987/Private-gallery-',GITHUB_REF='refs/heads/release/1.7.9-evidence-locker',PRODUCTION_UPGRADE='1',PRIVATE_GALLERY_UPGRADE_BASELINE='50',PRIVATE_GALLERY_179_BASELINE_APK=str(self.apk),UPGRADE179_TEST_MARKER=str(self.marker),PATH=str(self.path)+':'+os.environ['PATH'])
    def run_guard(self,changes,expected):
        result=subprocess.run(['bash','scripts/verify_synthetic_upgrade179.sh'],cwd=ROOT,env={**self.env,**changes},text=True,capture_output=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn(expected,result.stderr)
        self.assertFalse(self.marker.exists(),'Rejected invocation must not reach adb')
    def test_untrusted_event_repository_branch_and_nonproduction_mode(self):
        for changes in [{'GITHUB_EVENT_NAME':'pull_request'},{'GITHUB_REPOSITORY':'other/repo'},{'GITHUB_REF':'refs/heads/main'},{'GITHUB_ACTIONS':'false'},{'PRODUCTION_UPGRADE':'0'}]:
            with self.subTest(changes=changes):self.run_guard(changes,'Unsupported upgrade invocation')
    def test_wrong_baseline_and_bytes_fail_before_device_access(self):
        self.run_guard({'PRIVATE_GALLERY_UPGRADE_BASELINE':'49'},'Unsupported upgrade baseline')
        self.run_guard({},'Archived baseline APK hash mismatch')
    def test_missing_archived_baseline_is_explicitly_refused(self):
        env=self.env.copy();env.pop('PRIVATE_GALLERY_179_BASELINE_APK')
        self.env=env
        self.run_guard({},'Exact archived signed 1.7.8 code50 APK required')
if __name__=='__main__':unittest.main()
