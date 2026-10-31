"""Real runner collection/status controls; fixtures are synthetic and never Android evidence."""
import json
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
class RunnerContinuation(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.work=Path(self.temp.name)
        self.root=self.work/'app/build/evidence179'
        manifest=json.loads((ROOT/'scripts/evidence179_cases.json').read_text())
        self.cases=[x for x in manifest if not x['classname'].endswith('.EvidenceOwnedRoute179Test')]
        self.route_cases=[x for x in manifest if x['classname'].endswith('.EvidenceOwnedRoute179Test')]
        scripts=self.work/'scripts';scripts.mkdir()
        (scripts/'evidence179_cases.json').write_text(json.dumps(manifest))
        source=(ROOT/'scripts/run_evidence179.sh').read_text()
        self.function=source[source.index('run_selection() {'):source.index('# BEGIN NONROUTE DIAGNOSTIC CONTINUATION')]
        self.begin=source.split('# BEGIN NONROUTE DIAGNOSTIC CONTINUATION\n',1)[1].split('# END NONROUTE DIAGNOSTIC CONTINUATION',1)[0]
        self.final=source.split('# BEGIN NONROUTE FAILURE BARRIER\n',1)[1].split('# END NONROUTE FAILURE BARRIER',1)[0]
        route_start=source.index('while IFS=')
        self.route=source[source.index('\n',route_start)+1:source.index('done < <(',route_start)]
        (scripts/'wait_evidence179_peer_close.py').write_text("print('PEER_CLOSE_REACHED')\n")
    def reports(self,cases=None,failed=False,skipped=False):
        directory=self.work/'synthetic-input';directory.mkdir(exist_ok=True)
        suite=ET.Element('testsuite')
        for i,x in enumerate(self.cases if cases is None else cases):
            c=ET.SubElement(suite,'testcase',**x)
            if i==0 and failed:ET.SubElement(c,'failure').text='synthetic control fixture, not Android evidence'
            if i==1 and skipped:ET.SubElement(c,'skipped')
        ET.ElementTree(suite).write(directory/'synthetic.xml')
    def run_control(self,status,archive_failure=False,route=False):
        shutil.rmtree(self.work/'app/build',ignore_errors=True)
        self.root.mkdir(parents=True)
        if archive_failure:
            # XML copy succeeds first; this real copytree collision then fails additional-output collection.
            (self.root/'android-output'/('vpn_revoke' if route else 'focused')).mkdir(parents=True)
        mock_timeout=r'''timeout(){
 if [[ "$1" == --signal=TERM ]]; then
  mkdir -p app/build/outputs/androidTest-results/connected/debug app/build/outputs/connected_android_test_additional_output
  if [[ -f "$TEST_INPUT_XML" ]]; then cp "$TEST_INPUT_XML" app/build/outputs/androidTest-results/connected/debug/synthetic.xml; fi
  printf 'synthetic additional output\n' > app/build/outputs/connected_android_test_additional_output/control.txt
  return "$TEST_STATUS"
 fi
 # Timeout diagnostics are device commands; no device is contacted by this host control.
 [[ "$1" == 15s ]]
}
'''
        prefix='set -euo pipefail\n'+mock_timeout+'root=app/build/evidence179\nselection=synthetic\n'+self.function
        if route:
            script=prefix+'case=vpn_revoke\nmethod=synthetic\n'+self.route+"printf 'ROUTE_SELECTION_PASSED\\n'\n"
        else:
            script=prefix+self.begin+"printf 'ROUTES_PERMITTED\\n'\n"+self.final+"printf 'STRICT_VALIDATOR_REACHED\\n'\n"
        env={**os.environ,'TEST_INPUT_XML':str(self.work/'synthetic-input/synthetic.xml'),'TEST_STATUS':str(status),'GITHUB_SHA':'a'*40,'EVIDENCE179_ONION_HTTPS_URL':'https://'+'a'*56+'.onion'}
        return subprocess.run(['bash','-c',script],cwd=self.work,env=env,text=True,capture_output=True)
    def assert_statuses(self,original,collection):
        self.assertEqual(str(original),(self.root/'nonroute-exit-status.txt').read_text().strip())
        self.assertEqual(str(collection),(self.root/'nonroute-collection-exit-status.txt').read_text().strip())
    def test_success_retains_strict_validator_path(self):
        self.reports();r=self.run_control(0)
        self.assertEqual(0,r.returncode,r.stderr);self.assertIn('ROUTES_PERMITTED',r.stdout);self.assertIn('STRICT_VALIDATOR_REACHED',r.stdout)
        self.assert_statuses(0,0)
    def test_complete_failed_tests_allow_routes_but_preserve_failure(self):
        self.reports(failed=True);r=self.run_control(1)
        self.assertEqual(1,r.returncode,r.stderr);self.assertIn('ROUTES_PERMITTED',r.stdout);self.assertNotIn('STRICT_VALIDATOR_REACHED',r.stdout)
        self.assert_statuses(1,0)
    def test_infrastructure_status_never_permits_routes(self):
        self.reports(failed=True)
        for status in (2,124,137):
            with self.subTest(status=status):
                r=self.run_control(status);self.assertEqual(status,r.returncode,r.stderr);self.assertNotIn('ROUTES_PERMITTED',r.stdout)
                self.assert_statuses(status,0)
    def test_timeout_status_survives_archive_failure_after_complete_xml(self):
        self.reports(failed=True)
        for status in (124,137):
            with self.subTest(status=status):self.archive_failure_control(status,status)
    def test_ordinary_failure_and_archive_failure_stop_before_routes(self):
        self.reports(failed=True);self.archive_failure_control(1,1)
    def test_success_and_archive_failure_stop_before_routes(self):
        self.reports();self.archive_failure_control(0,1)
    def archive_failure_control(self,status,expected_exit):
        r=self.run_control(status,archive_failure=True)
        copied=list((self.root/'android-results/focused').rglob('*.xml'))
        self.assertEqual(1,len(copied));self.assertEqual(149,len(list(ET.parse(copied[0]).iter('testcase'))))
        self.assertIn('FileExistsError',r.stderr)
        self.assertEqual(expected_exit,r.returncode,r.stderr)
        self.assertNotIn('ROUTES_PERMITTED',r.stdout);self.assertNotIn('STRICT_VALIDATOR_REACHED',r.stdout)
        self.assert_statuses(status,1)
    def test_route_archive_failure_keeps_true_original_status_and_stops_peer_close(self):
        for status in (0,1,124,137):
            with self.subTest(status=status):
                self.reports(self.route_cases[:1],failed=status!=0)
                r=self.run_control(status,archive_failure=True,route=True)
                self.assertIn('FileExistsError',r.stderr)
                self.assertEqual(status or 1,r.returncode,r.stderr)
                phases=[json.loads(line) for line in (self.root/'host-phases.jsonl').read_text().splitlines()]
                self.assertEqual(['start','end'],[x['kind'] for x in phases])
                self.assertEqual(status,phases[-1]['original_exit_status'])
                self.assertEqual(1,phases[-1]['collection_exit_status'])
                self.assertNotIn('PEER_CLOSE_REACHED',r.stdout);self.assertNotIn('ROUTE_SELECTION_PASSED',r.stdout)
    def test_route_success_retains_peer_close_check_after_collection(self):
        self.reports(self.route_cases[:1]);r=self.run_control(0,route=True)
        self.assertEqual(0,r.returncode,r.stderr)
        self.assertIn('PEER_CLOSE_REACHED',r.stdout);self.assertIn('ROUTE_SELECTION_PASSED',r.stdout)
        phases=[json.loads(line) for line in (self.root/'host-phases.jsonl').read_text().splitlines()]
        self.assertEqual(0,phases[-1]['original_exit_status']);self.assertEqual(0,phases[-1]['collection_exit_status']);self.assertEqual(0,phases[-1]['peer_close_exit_status'])
    def test_missing_results_never_permit_routes(self):
        r=self.run_control(1);self.assertNotEqual(0,r.returncode);self.assertNotIn('ROUTES_PERMITTED',r.stdout)
    def test_partial_duplicate_foreign_or_skipped_results_never_permit_routes(self):
        for kind in ('partial','duplicate','foreign','skipped'):
            with self.subTest(kind=kind):
                cases=list(self.cases)
                if kind=='partial':cases=cases[:-1]
                if kind=='duplicate':cases[-1]=cases[0]
                if kind=='foreign':cases[-1]={'classname':'synthetic.Foreign','name':'wrongScope'}
                self.reports(cases,failed=True,skipped=kind=='skipped')
                r=self.run_control(1);self.assertNotEqual(0,r.returncode);self.assertNotIn('ROUTES_PERMITTED',r.stdout)
    def test_unexplained_exit_one_with_successful_xml_never_permits_routes(self):
        self.reports();r=self.run_control(1);self.assertNotEqual(0,r.returncode);self.assertNotIn('ROUTES_PERMITTED',r.stdout)
if __name__=='__main__':unittest.main()
