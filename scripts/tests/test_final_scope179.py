"""Negative acceptance gate tests; synthetic unit data is never release evidence."""
import copy
import hashlib
import importlib.util
import json
import re
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('scope179', ROOT/'scripts/verify_final_scope179.py')
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class FinalScope179Test(unittest.TestCase):
    def fixture(self):
        source = '1'*40
        decision = dict(source_sha=source, decision='APPROVED', critical_open=0, important_open=0)
        review = ('## Final machine-readable decision\n```json\n'+json.dumps(decision)+'\n```\n').encode()
        manifest = json.dumps([{'classname':'fixture.Class','name':'method'}]).encode()
        fixture = b'synthetic fixture unit data'
        validator = b'synthetic validator unit data'
        run = dict(source_sha=source,run_id=123,artifact_id=456,artifact_digest='sha256:'+'2'*64,
                   conclusion='success',workflow_path='.github/workflows/fast-development.yml',
                   head_branch='release/1.7.9-evidence-locker',event='push',verified_build_steps=True,
                   evidence_validation='PASS',original_exit_status=0,validated_case_count=1,network_evidence_revalidated=True)
        approval = dict(source_sha=source,decision='APPROVED',version='1.7.9',version_code=51,
                        package='uk.co.traynor.privategallery',certificate_sha256=GATE.CERTIFICATE,
                        required_upgrade_from=50,baseline_source_sha=GATE.BASELINE,
                        scope={name:'PASS' for name in GATE.REQUIRED},independent_review={**decision,'report_sha256':GATE.digest(review)},
                        case_manifest_sha256=GATE.digest(manifest),upgrade_fixture_sha256=GATE.digest(fixture),validator_sha256=GATE.digest(validator),
                        focused_run_id=123,focused_artifact_id=456,focused_artifact_digest=run['artifact_digest'],physical_acceptance='OWNER_REQUIRED')
        hashes={name:'4'*64 for name in GATE.EVIDENCE_HASHES}
        controls={name:'5'*64 for name in GATE.CONTROL_PATHS}
        controls.update({'scripts/verify_final_scope179.py':GATE.digest(validator),'docs/release179/UpgradeFixtureTest.kt':GATE.digest(fixture),'scripts/evidence179_cases.json':GATE.digest(manifest)})
        approval.update(evidence_hashes=hashes,controls=controls)
        run.update(evidence_hashes=hashes.copy(),control_hashes=controls.copy(),release_control_sha='6'*40)
        gates = dict(source_sha=source,decision='PASS',original_exit_status=0,network={name:'PASS' for name in GATE.NETWORK})
        gates.update({name:hashes[name] for name in ('route_proof_sha256','ocr_distribution_sha256','apk_native_alignment_sha256')})
        return [approval,source,review,run,gates,manifest,fixture,validator]

    def test_complete_synthetic_contract(self):
        GATE.validate(*self.fixture())

    def test_wrong_source_review_identity_scope_controls_or_evidence_rejected(self):
        mutations = [
            (0,'evidence_hashes',{}), (0,'controls',{}), (3,'control_hashes',{}), (3,'release_control_sha','0'*40), (3,'evidence_hashes',{}),
            (0,'decision','BLOCKED'), (0,'source_sha','3'*40), (0,'version_code',49),
            (0,'certificate_sha256','3'*64), (0,'baseline_source_sha','3'*40),
            (0,'required_upgrade_from',48), (0,'scope',{}), (0,'independent_review',{}),
            (0,'case_manifest_sha256','3'*64), (0,'upgrade_fixture_sha256','3'*64), (0,'validator_sha256','3'*64),
            (0,'focused_run_id',999), (0,'focused_artifact_id',999), (0,'focused_artifact_digest','sha256:'+'3'*64),
            (3,'source_sha','3'*40), (3,'run_id',True), (3,'artifact_id',0),
            (3,'artifact_digest','sha256:'+'0'*64), (3,'conclusion','failure'),
            (3,'workflow_path','.github/workflows/focused1762.yml'), (3,'event','pull_request'),
            (3,'head_branch','release/1.7.7-recently-deleted'), (3,'verified_build_steps',False),
            (3,'evidence_validation','PENDING'), (3,'original_exit_status',1), (3,'original_exit_status',False),
            (3,'validated_case_count',0), (3,'network_evidence_revalidated',False),
            (4,'source_sha','3'*40), (4,'decision','PENDING'), (4,'original_exit_status',1), (4,'network',{}),
        ]
        for index,key,value in mutations:
            with self.subTest(index=index,key=key,value=value):
                args=self.fixture();args[index][key]=value
                with self.assertRaises(ValueError): GATE.validate(*args)

    def test_zero_source_and_tampered_review_control_bytes_rejected(self):
        for index,value in ((1,'0'*40),(2,b'not a review'),(5,b'[]'),(6,b'tampered fixture'),(7,b'tampered validator')):
            with self.subTest(index=index):
                args=self.fixture();args[index]=value
                with self.assertRaises(ValueError): GATE.validate(*args)
        for field,value in (('critical_open',1),('important_open',1),('critical_open',False)):
            args=self.fixture();args[0]['independent_review'][field]=value
            with self.assertRaises(ValueError): GATE.validate(*args)

    def test_xml_exact_identity_no_extra_duplicate_failure_or_skip(self):
        expected={('fixture.Class','method')}
        good='<testcase classname="fixture.Class" name="method"/>'
        with tempfile.TemporaryDirectory() as directory:
            xml=Path(directory)/'results.xml'
            xml.write_text('<testsuite>'+good+'</testsuite>')
            self.assertEqual(1,GATE.verify_xml(directory,expected))
            for body in ('',good+good,good+'<testcase classname="extra" name="extra"/>',
                         good.replace('/>','><skipped/></testcase>'),good.replace('/>','><failure/></testcase>'),
                         good.replace('/>','><error/></testcase>')):
                xml.write_text('<testsuite>'+body+'</testsuite>')
                with self.assertRaises(ValueError):GATE.verify_xml(directory,expected)

    def test_duplicate_or_empty_manifest_rejected(self):
        for raw in (b'[]',b'{}',b'[{"classname":"x","name":"y"},{"classname":"x","name":"y"}]'):
            with self.assertRaises(ValueError):GATE.cases_from_manifest(raw)



    def test_cli_uses_frozen_aggregate_android_results_layout(self):
        import contextlib,io,sys
        from unittest.mock import patch
        approval,source,review,run,gates,manifest,fixture,_=self.fixture()
        approval['validator_sha256']=GATE.digest((ROOT/'scripts/verify_final_scope179.py').read_bytes())
        approval['controls']['scripts/verify_final_scope179.py']=approval['validator_sha256']
        run['control_hashes']['scripts/verify_final_scope179.py']=approval['validator_sha256']
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);evidence=root/'artifact';proof=evidence/'evidence179'
            results=proof/'android-results/focused';results.mkdir(parents=True)
            (results/'results.xml').write_text('<testsuite><testcase classname="fixture.Class" name="method"/></testsuite>')
            (proof/'source-sha.txt').write_text(source)
            (proof/'final-gates.json').write_text(json.dumps(gates))
            for name,value in [('approval.json',json.dumps(approval).encode()),('review.md',review),('run.json',json.dumps(run).encode()),('cases.json',manifest),('fixture.kt',fixture)]:
                (root/name).write_bytes(value)
            arguments=['verify_final_scope179.py',str(root/'approval.json'),source,str(root/'review.md'),str(root/'run.json'),str(evidence),str(root/'cases.json'),str(root/'fixture.kt')]
            with patch.object(sys,'argv',arguments),contextlib.redirect_stdout(io.StringIO()):GATE.main()

if __name__ == '__main__': unittest.main()
