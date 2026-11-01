"""Production context admission and clean-package query guards, without real device access."""
import os
import json
import sys
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

class InstrumentationSigning179(unittest.TestCase):
    def signing_block(self, signature=None, sign_status=0, verify_status=0):
        # Real runner command/checks, external signer mocked: no key or password exists here.
        source=(ROOT/'scripts/verify_synthetic_upgrade179.sh').read_text()
        start=source.index('"$sdk/apksigner" sign ')
        end=source.index('sha256sum "$instrumentation_apk"',start)
        block=source[start:end]
        temporary=tempfile.TemporaryDirectory();self.addCleanup(temporary.cleanup)
        root=Path(temporary.name);record=root/'sign-arguments.json';verification_record=root/'verify-arguments.json'
        apk=root/'private-copy/instrumentation.apk';keystore=root/'original-key-reference-only.jks'
        tool=root/'apksigner'
        tool.write_text('#!'+sys.executable+'\n'+'''import json,os,sys
from pathlib import Path
if sys.argv[1]=='sign':
    Path(os.environ['SIGN_ARGUMENTS']).write_text(json.dumps(sys.argv[1:]))
    raise SystemExit(int(os.environ['SIGN_STATUS']))
Path(os.environ['VERIFY_ARGUMENTS']).write_text(json.dumps(sys.argv[1:]))
if sys.argv[1:]!=['verify','--min-sdk-version','24','--verbose','--print-certs',os.environ['instrumentation_apk']]:
    raise SystemExit(91)
print(os.environ['SIGNATURE_FIXTURE'])
raise SystemExit(int(os.environ['VERIFY_STATUS']))
''')
        tool.chmod(0o755)
        good='Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17\n'
        env={**os.environ,'sdk':str(root),'artifact_dir':str(root),'instrumentation_apk':str(apk),
             'ORG_GRADLE_PROJECT_PRIVATE_GALLERY_STORE_FILE':str(keystore),'KEY_ALIAS':'original-alias-reference-only',
             'SIGN_ARGUMENTS':str(record),'VERIFY_ARGUMENTS':str(verification_record),'SIGNATURE_FIXTURE':good if signature is None else signature,
             'SIGN_STATUS':str(sign_status),'VERIFY_STATUS':str(verify_status)}
        result=subprocess.run(['bash','-c','set -euo pipefail\n'+block+"printf 'SIGNATURE_ADMITTED\\n'\n"],env=env,text=True,capture_output=True)
        self.assertFalse(keystore.exists());self.assertFalse(apk.exists())
        return result,json.loads(record.read_text()),str(keystore),str(apk),good,json.loads(verification_record.read_text()) if verification_record.exists() else None

    def test_actual_commands_enable_v2_verify_from24_and_keep_original_key_references(self):
        result,args,keystore,apk,_,verification_args=self.signing_block()
        self.assertEqual(0,result.returncode,result.stderr)
        self.assertEqual('SIGNATURE_ADMITTED\n',result.stdout)
        self.assertEqual(['sign','--v2-signing-enabled','true','--ks',keystore,'--ks-key-alias','original-alias-reference-only',
            '--ks-pass','env:STORE_PASSWORD','--key-pass','env:KEY_PASSWORD',apk],args)
        self.assertEqual(['verify','--min-sdk-version','24','--verbose','--print-certs',apk],verification_args)

    def test_signature_gate_still_rejects_missing_v2_foreign_certificate_multiple_signers_and_tool_errors(self):
        good='Verifies\nVerified using v2 scheme (APK Signature Scheme v2): true\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: 94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17\n'
        bad=[good.replace('): true','): false'),good.replace('94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17','0'*64),good.replace('Number of signers: 1','Number of signers: 2')]
        for signature in bad:
            with self.subTest(signature=signature):
                result,*_=self.signing_block(signature=signature)
                self.assertNotEqual(0,result.returncode);self.assertNotIn('SIGNATURE_ADMITTED',result.stdout)
        for statuses in [{'sign_status':7},{'verify_status':8}]:
            with self.subTest(statuses=statuses):
                result,*_=self.signing_block(**statuses)
                self.assertNotEqual(0,result.returncode);self.assertNotIn('SIGNATURE_ADMITTED',result.stdout)

if __name__=='__main__':unittest.main()
