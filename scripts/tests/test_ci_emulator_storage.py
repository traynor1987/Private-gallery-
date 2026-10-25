import importlib.util
from pathlib import Path
import unittest
import os,subprocess,tempfile,shutil,json
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('storage',Path(__file__).parents[1]/'ci_emulator_storage.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class StorageBudgetTest(unittest.TestCase):
 def test_toybox_df_uses_available_not_free_or_total(self):
  total,available=m.parse_df('Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-1 8388608 1048576 7340032 13% /data\n')
  self.assertEqual(total,8*1024**3);self.assertEqual(available,7*1024**3)
 def test_actual_android17_data_alias_capture(self):
  capture=Path(__file__).parents[2]/'docs/release1751/video-repair/capacity21/before.json'
  raw=json.loads(capture.read_text())
  total,available=m.parse_df(raw['guest_df']['stdout'])
  self.assertEqual(8107484*1024,total);self.assertEqual(7899500*1024,available)
  m.require_budget(total,available,raw['host_available_bytes'])
 def test_unrelated_mounts_do_not_count_as_data_capacity(self):
  for mount in ['/system','/data/user/10','/data/other','/storage/emulated/0']:
   with self.subTest(mount=mount),self.assertRaises(ValueError):m.parse_df('/dev/block/dm-6 8107484 60528 7899500 1% '+mount)
 def test_critically_low_space_fails(self):
  with self.assertRaises(ValueError):m.require_budget(8*1024**3,191*1024,8*1024**3)
 def test_small_actual_partition_fails_despite_requested_size(self):
  with self.assertRaises(ValueError):m.require_budget(4*1024**3,3*1024**3,8*1024**3)
 def test_insufficient_host_space_fails(self):
  with self.assertRaises(ValueError):m.require_budget(8*1024**3,4*1024**3,1024**3)
 def test_budget_boundary(self):m.require_budget(8*1024**3,3*1024**3,4*1024**3)
 def test_malformed_or_ambiguous_df_fails(self):
  for text in ['', '/dev/dm-1 1 2 invalid 99% /data', '/dev/dm-1 1 2 3 99% /data\n/dev/dm-2 1 2 3 99% /data']:
   with self.subTest(text=text),self.assertRaises(ValueError):m.parse_df(text)
class FocusedStorageDispatchTest(unittest.TestCase):
 def test_real_focused_helper_dispatches_and_archives_storage_label(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);bin=root/'bin';bin.mkdir()
   for name,body in {'git':'#!/bin/sh\necho verified-sha\n','adb':'#!/bin/sh\necho synthetic-device-evidence\n'}.items():
    p=bin/name;p.write_text(body);p.chmod(0o755)
   p=root/'gradlew';p.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > invocation.txt\nmkdir -p app/build/outputs/androidTest-results/connected/debug\necho raw-result > app/build/outputs/androidTest-results/connected/debug/test.xml\n');p.chmod(0o755)
   script=Path(__file__).parents[1]/'run_repair1751_focused.sh'
   result=subprocess.run(['bash',str(script),'storage-api37-cases','synthetic.Class#method'],cwd=root,env={**os.environ,'PATH':str(bin)+':'+os.environ['PATH']},capture_output=True,text=True)
   self.assertEqual(0,result.returncode,result.stderr)
   self.assertIn('class=synthetic.Class#method',(root/'invocation.txt').read_text())
   self.assertEqual('raw-result\n',(root/'app/build/refinement1751/storage-api37-cases/results/test.xml').read_text())
   self.assertTrue((root/'app/build/refinement1751/storage-api37-cases/logcat.txt').exists())
 def test_non_ci_invocation_fails_closed_and_retains_reason(self):
  with tempfile.TemporaryDirectory() as d:
   out=Path(d)/'failure.json';script=Path(__file__).parents[1]/'ci_emulator_storage.py'
   r=subprocess.run(['python3',str(script),str(out),'--avd','storage1751-37','--require-budget'],env={**os.environ,'GITHUB_ACTIONS':'false'},capture_output=True,text=True)
   self.assertNotEqual(0,r.returncode)
   self.assertIn('Disposable GitHub CI only',out.read_text())
 def test_unknown_platform_is_rejected_before_device_access(self):
  script=Path(__file__).parents[1]/'run_repair1751_focused.sh'
  r=subprocess.run(['bash',str(script),'unsupported','synthetic.Class'],capture_output=True,text=True)
  self.assertNotEqual(0,r.returncode)
if __name__=='__main__':unittest.main()

