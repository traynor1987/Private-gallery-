import importlib.util,pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parent
class PreflightChecks(unittest.TestCase):
 def test_health_gate_precedes_install(self):
  script=(ROOT/'run_android.sh').read_text()
  self.assertLess(script.index('wait_for_android.py'),script.index('adb install'))
 def module(self):
  spec=importlib.util.spec_from_file_location('preflight',ROOT/'wait_for_android.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
 def run_guard(self,values,timeout=20):
  m=self.module();ticks=[0.0];calls=[0]
  def probe():
   i=min(calls[0],len(values)-1);calls[0]+=1;return values[i]
  report=m.wait_for_android(probe,timeout=timeout,interval=1,stable_for=3,clock=lambda:ticks[0],sleep=lambda n:ticks.__setitem__(0,ticks[0]+n))
  return report,calls[0]
 def test_broken_package_manager_cannot_pass(self):
  report,calls=self.run_guard([{'healthy':False,'reason':'package: Broken pipe (32)','pid':'11'}],timeout=5)
  self.assertEqual('emulator_unavailable',report['status']);self.assertGreater(calls,1)
 def test_recovery_needs_stable_healthy_interval(self):
  report,calls=self.run_guard([{'healthy':False,'pid':'11'},{'healthy':True,'pid':'12'}])
  self.assertEqual('ready',report['status']);self.assertGreaterEqual(calls,5)
 def test_server_restart_resets_health_interval(self):
  report,calls=self.run_guard([{'healthy':True,'pid':'11'},{'healthy':True,'pid':'12'}])
  self.assertEqual('ready',report['status']);self.assertGreaterEqual(calls,5)
 def test_probe_handles_long_installed_package_list(self):
  import subprocess,unittest.mock
  m=self.module()
  def adb(args,**kwargs):
   if 'getprop' in args: value='1\n'
   elif 'pidof' in args:value='123\n'
   elif 'path' in args:value='package:/system/framework/framework-res.apk\n'
   else:value=('package:com.android.sample\n'*100)+'package:android\n'
   return subprocess.CompletedProcess(args,0,value,'')
  with unittest.mock.patch.object(m.subprocess,'run',side_effect=adb):self.assertTrue(m.probe_adb()['healthy'])
 def test_probe_rejects_broken_package_service(self):
  import subprocess,unittest.mock
  m=self.module()
  results=[subprocess.CompletedProcess([],0,'1\n',''),subprocess.CompletedProcess([],0,'123\n',''),subprocess.CompletedProcess([],1,'','cmd: Failure calling service package: Broken pipe (32)')]
  with unittest.mock.patch.object(m.subprocess,'run',side_effect=results):
   result=m.probe_adb();self.assertFalse(result['healthy']);self.assertIn('Broken pipe (32)',result['package'])
if __name__=='__main__':unittest.main()
