import copy,importlib.util,json,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('gate',ROOT/'scripts/verify_release1711_gate.py');gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
def evidence():
 token='a'*32;source='1'*40;data={};phases=[]
 for owner in ('primary','hidden'):
  for boundary in ('staging','uncommitted','committed','verified'):
   name=owner+'-'+boundary;pid=100+len(phases)*2
   ready=dict(token=token,owner=owner,boundary=boundary,pid=pid)
   verified=dict(ready,oldPid=pid,pid=pid+1)
   data[name+'-prepare.txt']='INSTRUMENTATION_STATUS: nova62_ready='+json.dumps(ready)+'\nINSTRUMENTATION_RESULT: shortMsg=Process crashed.\n'
   data[name+'-verify.txt']='INSTRUMENTATION_STATUS: nova62_verified='+json.dumps(verified)+'\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n'
   phases.append(dict(name=name,old_pid=pid,kill_exit=0,absent_after_kill=True))
 data['receipt.json']=json.dumps(dict(schema=1,source=source,token=token,phases=phases))
 return {k:v.encode() for k,v in data.items()}
class RestartControls(unittest.TestCase):
 def test_android_junit_class_prefix_on_status_line_is_real_output(self):
  data=evidence();prefix=b'uk.co.traynor.privategallery.core.backup.BackupProcessRestartTest:'
  for name in data:
   if name.endswith(('-prepare.txt','-verify.txt')):data[name]=prefix+data[name]
  self.assertEqual(8,gate.restart_cases(data,'1'*40))
  # Retain exact-one-observation validation for class-prefixed output too.
  name='primary-staging-prepare.txt';data[name]+=data[name]
  with self.assertRaises(ValueError):gate.restart_cases(data,'1'*40)
  data=evidence();name='primary-staging-prepare.txt';data[name]+=prefix+data[name]
  with self.assertRaises(ValueError):gate.restart_cases(data,'1'*40)
  data=evidence();data[name]=b'arbitrary-prefix:'+data[name]
  with self.assertRaises(ValueError):gate.restart_cases(data,'1'*40)
 def test_complete_distinct_actual_process_phases_required(self):
  self.assertEqual(8,gate.restart_cases(evidence(),'1'*40))
  for mutation in ('missing','duplicate','failure','token','same-pid','not-killed','seed-passed','source'):
   data=evidence();receipt=json.loads(data['receipt.json']);name='primary-staging'
   if mutation=='missing':del data[name+'-verify.txt']
   if mutation=='duplicate':receipt['phases'].append(receipt['phases'][0])
   if mutation=='failure':data[name+'-verify.txt']+=b'FAILURES!!!'
   if mutation=='token':data[name+'-verify.txt']=data[name+'-verify.txt'].replace(b'a'*32,b'b'*32)
   if mutation=='same-pid':data[name+'-verify.txt']=data[name+'-verify.txt'].replace(b'"pid": 101',b'"pid": 100')
   if mutation=='not-killed':receipt['phases'][0]['absent_after_kill']=False
   if mutation=='seed-passed':data[name+'-prepare.txt']+=b'OK (1 test)'
   if mutation=='source':receipt['source']='2'*40
   data['receipt.json']=json.dumps(receipt).encode()
   with self.subTest(mutation=mutation),self.assertRaises((ValueError,KeyError)):gate.restart_cases(data,'1'*40)
if __name__=='__main__':unittest.main()
