"""Record and fail closed on disposable-emulator storage prerequisites. Never app code."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
GIB=1024**3

def parse_df(text):
    rows=[line.split() for line in text.splitlines() if line.strip().startswith('/dev/')]
    if len(rows)!=1 or len(rows[0])!=6 or rows[0][-1] not in ('/data','/data/user/0'):
        raise ValueError('Unrecognised /data df output')
    row=rows[0]
    try:total,used,available=map(int,row[1:4])
    except ValueError:raise ValueError('Non-numeric /data capacity')
    if min(total,used,available)<0 or total==0 or available>total or used>total:
        raise ValueError('Invalid /data capacity')
    return total*1024,available*1024

def require_budget(total,available,host_available):
    if total<6*GIB or available<3*GIB or host_available<4*GIB:
        raise ValueError('CI storage budget unmet: >=6GiB actual /data, >=3GiB guest and >=4GiB host available required')

def command(*args):
    r=subprocess.run(args,text=True,capture_output=True,timeout=10)
    return {'exit':r.returncode,'stdout':r.stdout,'stderr':r.stderr}

def adb(*args):return command('adb',*args)

def snapshot(output,avd,budget):
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('Disposable GitHub CI only')
    if adb('shell','getprop','ro.kernel.qemu')['stdout'].strip()!='1':raise ValueError('Emulator required')
    actual_avd=adb('emu','avd','name')['stdout'].splitlines()
    if not actual_avd or actual_avd[0].strip()!=avd:raise ValueError('Unexpected disposable AVD')
    sdk=adb('shell','getprop','ro.build.version.sdk')['stdout'].strip()
    if sdk not in ('36','37'):raise ValueError('Unsupported verification SDK')
    if not re.fullmatch(r'[A-Za-z0-9_-]+',avd):raise ValueError('Invalid CI AVD name')
    config=Path.home()/'.android/avd'/f'{avd}.avd/config.ini'
    data={'source':os.environ.get('GITHUB_SHA'),'timestamp':time.time(),'sdk':sdk,'avd':avd,'config':config.read_text(),
          'guest_df':adb('shell','df','-k','/data'),'guest_inodes':adb('shell','df','-i','/data'),
          'fixture_footprint':adb('shell','run-as','uk.co.traynor.privategallery.hiddenprototype','du','-sk','files','no_backup','cache'),
          'host_df':command('df','-k','.'),'budget_required':budget}
    host=os.statvfs('.');data['host_available_bytes']=host.f_bavail*host.f_frsize
    error=None
    try:
        if data['guest_df']['exit']!=0:raise ValueError('Guest capacity command failed')
        total,available=parse_df(data['guest_df']['stdout']);data.update(guest_total_bytes=total,guest_available_bytes=available)
        if budget:require_budget(total,available,data['host_available_bytes'])
    except ValueError as e:error=e;data['budget_error']=str(e)
    path=Path(output);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,indent=2)+'\n')
    if error:raise error
    print(f'CI storage snapshot: SDK{sdk}, total={total}, available={available}, budget={budget}')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('output');p.add_argument('--avd',required=True);p.add_argument('--require-budget',action='store_true')
    a=p.parse_args()
    try:snapshot(a.output,a.avd,a.require_budget)
    except Exception as error:
        target=Path(a.output)
        if not target.exists():
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(json.dumps({'source':os.environ.get('GITHUB_SHA'),'capture_failed':type(error).__name__,'reason':str(error)},indent=2)+'\n')
        raise
