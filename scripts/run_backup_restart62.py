#!/usr/bin/env python3
"""One disposable API37 target, real force-stop boundaries, original raw transcripts."""
from pathlib import Path
import json,os,re,subprocess,sys,time,uuid
ROOT=Path(__file__).resolve().parents[1]
PACKAGE='uk.co.traynor.privategallery.hiddenprototype'
TEST=PACKAGE+'.test/androidx.test.runner.AndroidJUnitRunner'
CLASS='uk.co.traynor.privategallery.core.backup.BackupProcessRestartTest'
out=ROOT/'app/build/home2-evidence/backup-restart62';out.mkdir(parents=True,exist_ok=False)
def capture_failure(kind,value,traceback):
 # Diagnostics are outside the strict seventeen-file restart evidence folder.
 for label,command in (
  ('logcat',['adb','logcat','-d','-t','2500']),
  ('provider-package',['adb','shell','dumpsys','package',PACKAGE+'.test']),
 ):
  result=subprocess.run(command,capture_output=True,text=True,timeout=15)
  (out.parent/('backup-restart62-failure-'+label+'.txt')).write_text(result.stdout+result.stderr)
 sys.__excepthook__(kind,value,traceback)
sys.excepthook=capture_failure
token=uuid.uuid4().hex;source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();phases=[]
assert subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()=='37'
for owner in ('primary','hidden'):
 for boundary in ('staging','uncommitted','committed','verified'):
  name=owner+'-'+boundary
  common=['adb','shell','am','instrument','-w','-r','-e','nova62Token',token,'-e','nova62Owner',owner,'-e','nova62Boundary',boundary]
  prepare=out/(name+'-prepare.txt')
  with prepare.open('w') as log:
   child=subprocess.Popen(common+['-e','class',CLASS+'#prepareBoundaryAndAwaitHostTermination',TEST],stdout=log,stderr=subprocess.STDOUT)
   try:
    deadline=time.monotonic()+40;ready=None
    while time.monotonic()<deadline:
     text=prepare.read_text();match=re.search(r'^INSTRUMENTATION_STATUS: nova62_ready=(.+)$',text,re.M)
     if match:ready=json.loads(match[1]);break
     if child.poll() is not None:raise RuntimeError('Preparation exited before boundary: '+name+'\n'+text)
     time.sleep(.1)
    if ready is None:raise RuntimeError('Preparation boundary timed out: '+name)
    assert ready['token']==token and ready['owner']==owner and ready['boundary']==boundary
    pids=subprocess.check_output(['adb','shell','pidof',PACKAGE],text=True).strip().split();assert str(ready['pid']) in pids
    kill=subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True)
    child.wait(timeout=10)
    remaining=subprocess.run(['adb','shell','pidof',PACKAGE],capture_output=True,text=True);assert str(ready['pid']) not in remaining.stdout.split()
   finally:
    if child.poll() is None:subprocess.run(['adb','shell','am','force-stop',PACKAGE],check=True);child.wait(timeout=10)
  verify=subprocess.run(common+['-e','class',CLASS+'#verifyRecoveryInNewProcess',TEST],capture_output=True,text=True,timeout=45)
  (out/(name+'-verify.txt')).write_text(verify.stdout+verify.stderr)
  if verify.returncode:raise RuntimeError('Recovery instrumentation command failed: '+name)
  phases.append(dict(name=name,old_pid=ready['pid'],kill_exit=kill.returncode,absent_after_kill=True))
(out/'receipt.json').write_text(json.dumps(dict(schema=1,source=source,token=token,phases=phases),indent=2)+'\n')
from verify_release1711_gate import restart_cases
assert restart_cases({p.name:p.read_bytes() for p in out.iterdir()},source)==8
print('PASS: eight real process-stop/restart boundaries; original transcripts retained')
