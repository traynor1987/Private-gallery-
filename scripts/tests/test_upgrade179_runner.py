"""Production context admission and clean-package query guards, without real device access."""
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
    def query_guard(self,output,status):
        # Execute the real narrow admission block. No Git, signer, install or device is invoked.
        source=(ROOT/'scripts/verify_synthetic_upgrade179.sh').read_text()
        block=source[source.index('fixture_package='):source.index('project_root=$(pwd)')]
        tool=self.path/'adb'
        tool.write_text("#!/bin/sh\n[ \"$*\" = 'shell pm list packages uk.co.traynor.privategallery' ] || exit 95\nprintf '%s' \"$QUERY_OUTPUT\"\nexit \"$QUERY_STATUS\"\n")
        tool.chmod(0o755)
        return subprocess.run(['bash','-c','set -euo pipefail\n'+block+"printf 'ADMITTED\\n'\n"],
            env={**self.env,'QUERY_OUTPUT':output,'QUERY_STATUS':str(status)},text=True,capture_output=True)
    def test_absent_production_package_admits_successful_empty_query(self):
        for output in ['', 'package:uk.co.traynor.privategallery.test\r\npackage:uk.co.traynor.privategallery.hiddenprototype\r\n']:
            with self.subTest(output=output):
                result=self.query_guard(output,0)
                self.assertEqual(0,result.returncode,result.stderr)
                self.assertEqual('ADMITTED\n',result.stdout)
    def test_exact_installed_production_package_is_rejected(self):
        result=self.query_guard('package:uk.co.traynor.privategallery.test\r\npackage:uk.co.traynor.privategallery\r\n',0)
        self.assertNotEqual(0,result.returncode)
        self.assertIn('Production package must be initially absent',result.stderr)
        self.assertNotIn('ADMITTED',result.stdout)
    def test_package_query_failure_never_admits_empty_or_partial_output(self):
        for output in ['', 'package:uk.co.traynor.privategallery.test\n']:
            for status in [1,42]:
                with self.subTest(output=output,status=status):
                    result=self.query_guard(output,status)
                    self.assertNotEqual(0,result.returncode)
                    self.assertIn('Package presence query failed',result.stderr)
                    self.assertNotIn('ADMITTED',result.stdout)
if __name__=='__main__':unittest.main()
