import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


class EvidenceResetTest(unittest.TestCase):
    def run_fixture(self,present,fail=False):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            adb=root/'adb'
            adb.write_text('''#!/usr/bin/python3
import json,os,sys
with open(os.environ['RESET_FIXTURE_LOG'],'a') as output:output.write(json.dumps(sys.argv[1:])+'\\n')
if sys.argv[1:4]==['shell','pm','path']:
 if os.environ['RESET_FIXTURE_PRESENT']=='1':print('package:/data/app/synthetic/base.apk')
else:
 if os.environ['RESET_FIXTURE_PRESENT']!='1' or os.environ['RESET_FIXTURE_FAIL']=='1':sys.exit(1)
''')
            adb.chmod(0o755)
            env={**os.environ,'PATH':str(root),'RESET_FIXTURE_LOG':str(root/'log'),'RESET_FIXTURE_PRESENT':str(int(present)),'RESET_FIXTURE_FAIL':str(int(fail))}
            result=subprocess.run(['/bin/bash','scripts/reset_native_browser_evidence1762.sh'],env=env,capture_output=True,text=True)
            return result,[json.loads(line) for line in (root/'log').read_text().splitlines()]
    def test_fresh_emulator_needs_no_package_private_operation(self):
        result,calls=self.run_fixture(False)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(calls,[['shell','pm','path','uk.co.traynor.privategallery']])
    def test_installed_package_only_removes_named_synthetic_results(self):
        result,calls=self.run_fixture(True)
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(calls[1],['shell','run-as','uk.co.traynor.privategallery','rm','-f','files/native-tor1762-result.json','files/vpn-native-revocation1762-result.json','files/vpn-network-loss1762-result.json'])
    def test_real_reset_failure_is_not_silently_ignored(self):
        result,_=self.run_fixture(True,True)
        self.assertNotEqual(result.returncode,0)
