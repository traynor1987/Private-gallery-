"""Fail-closed release-control checks; these do not manufacture Android evidence."""
import hashlib, importlib.util, json, os, subprocess, tempfile, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('gate77',ROOT/'scripts/verify_release1711_gate.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class Guardian77Controls(unittest.TestCase):
 def test_only_original76_baseline_is_accepted(self):
  for baseline,ok in [('76',True),('75',False),('77',False)]:
   env=dict(os.environ,GITHUB_REF='refs/heads/'+gate.BRANCH,PRIVATE_GALLERY_UPGRADE_BASELINE=baseline)
   result=subprocess.run(['bash','-ec','source scripts/release_configuration1711.sh; test "$release_code" = 77; test "$baseline_code" = 76'],cwd=ROOT,env=env,capture_output=True)
   self.assertEqual(ok,result.returncode==0)
 def test_historical_release_marker_is_rejected(self):
  marker=json.loads((ROOT/'.sanctuary-production-candidate').read_bytes())
  with self.assertRaisesRegex(ValueError,'Wrong upgrade identity'):
   gate.validate(marker,b'',{},{},{},'/nonexistent',b'{}',gate.controls(),b'')
 def test_missing_physical_acceptance_prevents_signer_gate(self):
  with tempfile.TemporaryDirectory() as folder:
   archive=Path(folder)/'untrusted.zip';archive.write_bytes(b'not runtime evidence')
   fixture=b'synthetic fixture';matrix=b'{}';source='1'*40;controls=gate.controls()
   lines=['Source: '+source,'Decision: APPROVED','Production signing: APPROVED','Upgrade fixture SHA-256: '+gate.sha(fixture),'Upgrade controls SHA-256: '+gate.controls_digest(controls),'Runtime matrix SHA-256: '+gate.sha(matrix),'Runtime archive SHA-256: '+gate.sha(archive.read_bytes())]
   marker=dict(source=source,baseline_source=gate.BASELINE,baseline_code=76,candidate_code=77,controls_sha256=controls,fixture_sha256=gate.sha(fixture),matrix_sha256=gate.sha(matrix),runtime_archive_sha256=gate.sha(archive.read_bytes()))
   for absent in ['Physical Fold Main encrypted capture: PASS','Physical Fold Friends separation: PASS']:
    review=('\n'.join(lines+[line for line in ['Physical Fold Main encrypted capture: PASS','Physical Fold Friends separation: PASS'] if line!=absent])+'\n').encode()
    marker['review_sha256']=gate.sha(review)
    with self.assertRaisesRegex(ValueError,'Missing exact independent approval line: '+absent):
     gate.validate(marker,review,{},{},{},archive,matrix,controls,fixture)
 def test_original_ci_metadata_failures_prevent_runtime_acceptance(self):
  import copy
  with tempfile.TemporaryDirectory() as folder:
   archive=Path(folder)/'untrusted.zip';archive.write_bytes(b'not runtime evidence')
   fixture=b'synthetic fixture';matrix=b'{}';source='1'*40;controls=gate.controls();digest=gate.sha(archive.read_bytes())
   review=('\n'.join(['Source: '+source,'Decision: APPROVED','Production signing: APPROVED','Physical Fold Main encrypted capture: PASS','Physical Fold Friends separation: PASS','Upgrade fixture SHA-256: '+gate.sha(fixture),'Upgrade controls SHA-256: '+gate.controls_digest(controls),'Runtime matrix SHA-256: '+gate.sha(matrix),'Runtime archive SHA-256: '+digest])+'\n').encode()
   marker=dict(source=source,baseline_source=gate.BASELINE,baseline_code=76,candidate_code=77,controls_sha256=controls,fixture_sha256=gate.sha(fixture),matrix_sha256=gate.sha(matrix),runtime_archive_sha256=digest,review_sha256=gate.sha(review),test_run=123,test_artifact=456)
   run=dict(id=123,event='push',head_sha=source,head_branch=gate.BRANCH,status='completed',conclusion='success',head_repository=dict(full_name='traynor1987/Private-gallery-'),path='.github/workflows/fast-development.yml')
   artifact=dict(id=456,expired=False,name='novaos-home2-integration-verification',digest='sha256:'+digest,workflow_run=dict(id=123,head_sha=source))
   jobs=dict(jobs=[dict(name='home2-integration',conclusion='success',steps=[dict(name=name,conclusion='success') for name in gate.REQUIRED_STEPS])])
   for target,field,value,error in [('run','conclusion','failure','Runtime workflow not successful'),('run','head_sha','2'*40,'Wrong runtime source'),('run','path','.github/workflows/untrusted.yml','Wrong runtime workflow'),('artifact','digest','sha256:'+'0'*64,'original artifact digest'),('artifact','expired',True,'Expired or wrong'),('jobs','jobs',[],'Missing successful original')]:
    r,a,j=copy.deepcopy(run),copy.deepcopy(artifact),copy.deepcopy(jobs)
    dict(run=r,artifact=a,jobs=j)[target][field]=value
    with self.subTest(target=target,field=field),self.assertRaisesRegex(ValueError,error):
     gate.validate(marker,review,r,j,a,archive,matrix,controls,fixture)
if __name__=='__main__':unittest.main()
