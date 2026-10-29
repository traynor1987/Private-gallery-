import hashlib,importlib.util,json,pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class Scope177Test(unittest.TestCase):
 def test_exact_source_review_and_successful_run_are_required(self):
  path=ROOT/'scripts/verify_final_scope177.py'
  self.assertTrue(path.exists(),'1.7.7 must have its own strict pre-sign evidence validator')
  spec=importlib.util.spec_from_file_location('scope177',path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
  sha='a'*40
  decision=dict(source_sha=sha,decision='APPROVED_FOR_SIGNING',critical_open=0,important_open=0)
  review=('## Final machine-readable decision\n\n```json\n'+json.dumps(decision)+'\n```\n').encode()
  approval=dict(source_sha=sha,version='1.7.7',version_code=46,package='uk.co.traynor.privategallery',certificate_sha256=m.CERTIFICATE,scope={name:'PASS' for name in m.REQUIRED},independent_review=dict(source_sha=sha,decision='APPROVED_FOR_SIGNING',critical_open=0,important_open=0,report_sha256=hashlib.sha256(review).hexdigest()),physical_acceptance='OWNER_REQUIRED',required_upgrade_from=45,focused_run_id=123,focused_artifact_id=456,focused_artifact_digest='sha256:'+'b'*64)
  run=dict(run_id=123,artifact_id=456,source_sha=sha,conclusion='success',verified_build_steps=True,evidence_validation='PASS',original_exit_status=0,artifact_digest='sha256:'+'b'*64,validated_case_count=23)
  m.validate(approval,sha,review,run)
  for field,value in [('source_sha','c'*40),('conclusion','failure'),('original_exit_status',1),('evidence_validation','PENDING'),('validated_case_count',20),('run_id',124),('artifact_id',457),('artifact_digest','sha256:'+'c'*64)]:
   with self.assertRaises((ValueError,AssertionError)):m.validate(approval,sha,review,{**run,field:value})
  with self.assertRaises((ValueError,AssertionError)):m.validate(approval,sha,b'changed',run)
  for invalid in [b'no decision',review+review,review.replace(b'APPROVED_FOR_SIGNING',b'NOT_APPROVED_FOR_SIGNING'),review.replace(sha.encode(),b'c'*40)]:
   changed={**approval,'independent_review':{**approval['independent_review'],'report_sha256':hashlib.sha256(invalid).hexdigest()}}
   with self.assertRaises((ValueError,AssertionError)):m.validate(changed,sha,invalid,run)
  approval['independent_review']['important_open']=1
  with self.assertRaises((ValueError,AssertionError)):m.validate(approval,sha,review,run)
if __name__=='__main__':unittest.main()
