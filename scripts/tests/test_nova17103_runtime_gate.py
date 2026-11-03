import tempfile,json,zipfile,unittest,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from verify_nova17103_runtime import validate
from verify_hidden_continuity_packets17103 import RECEIPTS,evaluate
from scripts.tests.test_hidden_continuity_packets17103 import fixture

class ContinuityRuntimeGate(unittest.TestCase):
 def archive(self,root,change=None,omit=None,tamper=None):
  matrix=Path('docs/release17103/continuity-release-cases.json');selected=json.loads(matrix.read_text());archive=root/'runtime.zip'
  events,receipts,packets,pcap=fixture()
  original={name:json.dumps(data).encode() for name,data in receipts.items()}
  event_bytes=('\n'.join(map(json.dumps,events))+'\n').encode()
  verdict=evaluate(event_bytes,original,packets.encode(),pcap)
  if change:receipts[change[0]][change[1]]=False
  cases=''.join('<testcase classname="'+cls+'" name="'+method+'"/>' for cls,methods in selected.items()for method in methods)
  files={'outputs/androidTest-results/TEST.xml':'<testsuite>'+cases+'</testsuite>','nova17102/source-sha.txt':'1'*40}
  for name in ['nova17102/runtime-result.txt','nova17102/ui-accounting.txt','nova17102/route-accounting.txt','nova17102/tor-route-result.txt','hidden-vpn1762/result.txt','nova17102/baseline-route-accounting.txt','nova17102/baseline-vpn/result.txt']:files[name]='PASS: synthetic UNIT TEST receipt only'
  prefix='hidden-vpn1762/continuity-final/'
  for name,data in receipts.items():
   files['outputs/connected_android_test_additional_output/'+name]=json.dumps(data)
   files[prefix+name]=json.dumps(data)
  files.update({prefix+'events.jsonl':event_bytes,prefix+'packets.txt':packets,prefix+'traffic.pcap':pcap,prefix+'continuity-transition-result.json':json.dumps(verdict)})
  if omit:files.pop(omit)
  if tamper:files[tamper[0]]=tamper[1]
  with zipfile.ZipFile(archive,'w')as z:
   for name,data in files.items():z.writestr(name,data)
  return archive,matrix
 def test_empty_case_matrix_never_approves(self):
  with tempfile.TemporaryDirectory() as folder:
   root=Path(folder);matrix=root/'matrix.json';matrix.write_text('{}');archive,_=self.archive(root)
   with self.assertRaises((ValueError,AssertionError)):validate(archive,matrix)
 def test_exact_cases_all_eight_proofs_and_recomputed_packet_verdict(self):
  with tempfile.TemporaryDirectory()as folder:
   archive,matrix=self.archive(Path(folder));self.assertEqual(sum(map(len,json.loads(matrix.read_text()).values())),validate(archive,matrix,'1'*40))
   with self.assertRaises(ValueError):validate(archive,matrix,'2'*40)
 def test_each_required_security_fact_denies_when_false(self):
  with tempfile.TemporaryDirectory()as folder:
   for name,flags in RECEIPTS.items():
    for flag in flags:
     archive,matrix=self.archive(Path(folder),(name,flag))
     with self.subTest(receipt=name,flag=flag),self.assertRaises((ValueError,AssertionError)):validate(archive,matrix,'1'*40)
 def test_missing_receipt_raw_packets_or_verdict_denies(self):
  with tempfile.TemporaryDirectory()as folder:
   for name in [*RECEIPTS,'events.jsonl','packets.txt','traffic.pcap','continuity-transition-result.json']:
    archive,matrix=self.archive(Path(folder),omit='hidden-vpn1762/continuity-final/'+name)
    with self.subTest(missing=name),self.assertRaises(ValueError):validate(archive,matrix,'1'*40)
 def test_forged_pass_and_changed_raw_observations_are_recomputed(self):
  with tempfile.TemporaryDirectory()as folder:
   for name,data in [('continuity-transition-result.json','{"verdict":"PASS"}'),('packets.txt','no packet observations'),('events.jsonl','{}'),('traffic.pcap',b'')]:
    archive,matrix=self.archive(Path(folder),tamper=('hidden-vpn1762/continuity-final/'+name,data))
    with self.subTest(tamper=name),self.assertRaises((ValueError,AssertionError)):validate(archive,matrix,'1'*40)
