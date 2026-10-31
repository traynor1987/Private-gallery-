"""Negative acceptance gate tests; synthetic unit data is never release evidence."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('scope178', ROOT/'scripts/verify_final_scope178.py')
GATE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GATE)


class FinalScope178Test(unittest.TestCase):
    def fixture(self):
        source = '1'*40
        decision = dict(source_sha=source, decision='APPROVED', critical_open=0, important_open=0)
        review = ('## Final machine-readable decision\n```json\n'+json.dumps(decision)+'\n```\n').encode()
        manifest = json.dumps([{'classname':'fixture.Class','name':'method'}]).encode()
        fixture = b'synthetic fixture unit data'
        validator = b'synthetic validator unit data'
        run = dict(source_sha=source,run_id=123,artifact_id=456,artifact_digest='sha256:'+'2'*64,
                   conclusion='success',workflow_path='.github/workflows/fast-development.yml',
                   head_branch='release/1.7.8-private-apps',event='push',verified_build_steps=True,
                   evidence_validation='PASS',original_exit_status=0,validated_case_count=1,network_evidence_revalidated=True)
        approval = dict(source_sha=source,decision='APPROVED',version='1.7.8',version_code=48,
                        package='uk.co.traynor.privategallery',certificate_sha256=GATE.CERTIFICATE,
                        required_upgrade_from=47,baseline_source_sha=GATE.BASELINE,
                        scope={name:'PASS' for name in GATE.REQUIRED},independent_review={**decision,'report_sha256':GATE.digest(review)},
                        case_manifest_sha256=GATE.digest(manifest),upgrade_fixture_sha256=GATE.digest(fixture),validator_sha256=GATE.digest(validator),
                        focused_run_id=123,focused_artifact_id=456,focused_artifact_digest=run['artifact_digest'],physical_acceptance='OWNER_REQUIRED')
        gates = dict(source_sha=source,decision='PASS',original_exit_status=0,network={name:'PASS' for name in GATE.NETWORK})
        return [approval,source,review,run,gates,manifest,fixture,validator]

    def test_complete_synthetic_contract(self):
        GATE.validate(*self.fixture())

    def test_wrong_source_review_identity_scope_controls_or_evidence_rejected(self):
        mutations = [
            (0,'decision','BLOCKED'), (0,'source_sha','3'*40), (0,'version_code',46),
            (0,'certificate_sha256','3'*64), (0,'baseline_source_sha','3'*40),
            (0,'required_upgrade_from',45), (0,'scope',{}), (0,'independent_review',{}),
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

    def test_workflow_fail_closed_and_signer_after_complete_gate(self):
        text=(ROOT/'.github/workflows/acceptance1762.yml').read_text()
        new=text.split('\n  acceptance178:\n',1)[1]
        self.assertIn('test "$FROZEN_SOURCE" != '+'0'*40,new)
        self.assertIn("needs.release-marker.outputs.new_marker == 'true'",new)
        self.assertIn("github.ref == 'refs/heads/release/1.7.8-private-apps'",new)
        self.assertLess(new.index('verify_final_scope178.py" "$RUNNER_TEMP/final-scope178.json"'),new.index('secrets.PRIVATE_GALLERY_SIGNING_KEYSTORE_BASE64'))
        self.assertLess(new.index('./gradlew lintRelease assembleRelease'),new.index('cp "$RUNNER_TEMP/UpgradeFixture178.kt" scripts/upgrade/UpgradeFixtureTest.kt'))
        self.assertIn('exact_archived_original_signed_1.7.8_code47_from_run41081513370_artifact12990222572',new)


if __name__ == '__main__': unittest.main()
