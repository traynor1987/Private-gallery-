"""Execute the CI health script; emulator runner invokes each workflow script line separately."""
import os,subprocess,tempfile,unittest
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[2]
class Home2EmulatorPreflightTest(unittest.TestCase):
 def execute(self,mode='good',target='android-37.0'):
  with tempfile.TemporaryDirectory() as directory:
   p=Path(directory);(p/'test.ini').write_text('target='+target+'\n')
   adb=p/'adb';adb.write_text('''#!/usr/bin/env python3
import os,sys
from pathlib import Path
mode=os.environ['FAKE_MODE'];args=' '.join(sys.argv[1:])
if args=='shell getprop ro.build.version.sdk':print('36' if mode=='wrong-api' else '37')
elif args=='shell pm path com.android.systemui':print('' if mode=='no-systemui' else 'package:/system/SystemUI.apk')
elif args=='shell pidof surfaceflinger':
 p=Path(os.environ['FAKE_COUNTER']);n=int(p.read_text())+1 if p.exists() else 1;p.write_text(str(n));print('456' if mode=='restart' and n>2 else '123')
elif args=='logcat -d -b crash':print('hasReadColorBufferDma' if mode=='dma' else '')
else:sys.exit(9)
''');adb.chmod(0o755)
   sleep=p/'sleep';sleep.write_text('#!/bin/sh\nexit 0\n');sleep.chmod(0o755)
   env=os.environ.copy();env.update(GITHUB_ACTIONS='true',GITHUB_REF='refs/heads/integration/novaos-1.7-home2',ANDROID_AVD_HOME=str(p),FAKE_MODE=mode,FAKE_COUNTER=str(p/'counter'),PATH=str(p)+':'+env['PATH'])
   result=subprocess.run(['bash',str(ROOT/'scripts/home2_emulator_preflight.sh'),str(p/'out')],env=env,capture_output=True,text=True)
   if mode=='good' and target=='android-37.0':
    self.assertEqual(0,result.returncode,result.stderr);self.assertEqual(15,len((p/'out/emulator-health.txt').read_text().splitlines()))
   else:self.assertNotEqual(0,result.returncode,(mode,target,result.stdout,result.stderr))
 def test_single_command_preserves_shell_state_in_real_action_adapter(self):
  w=yaml.safe_load((ROOT/'.github/workflows/fast-development.yml').read_text());j=w['jobs']['home2-integration'];s=next(x for x in j['steps']if x.get('name')=='Focused Android 17 integration and privacy tests')['with']['script'];commands=[line.strip()for line in s.splitlines()if line.strip()]
  self.assertEqual('bash scripts/home2_emulator_preflight.sh',commands[0]);self.assertFalse(any('initial_surface=' in line or line.startswith('for ')for line in commands))
 def test_healthy_platform_records_all_observations(self):self.execute()
 def test_rejects_incorrect_api_systemui_metadata_restarts_and_dma(self):
  for mode in ['wrong-api','no-systemui','restart','dma']:
   with self.subTest(mode=mode):self.execute(mode)
  self.execute(target='android-0')
if __name__=='__main__':unittest.main()
