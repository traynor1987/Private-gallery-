#!/usr/bin/env python3
"""Bounded emulator-health gate. No install, permission grant or test assertion bypass."""
import argparse,json,pathlib,subprocess,time

def wait_for_android(probe,*,timeout=120,interval=5,stable_for=15,clock=time.monotonic,sleep=time.sleep):
 start=clock();since=None;pid=None;observations=[]
 while clock()-start<=timeout:
  result=probe();now=clock();observations.append(dict(result,elapsed=round(now-start,3)))
  if result.get('healthy'):
   if result.get('pid')!=pid:pid=result.get('pid');since=now
   if since is not None and now-since>=stable_for:
    return {'status':'ready','system_server_pid':pid,'observations':observations[-12:]}
  else:pid=None;since=None
  sleep(interval)
 return {'status':'emulator_unavailable','observations':observations[-12:]}

def probe_adb():
 result={}
 for name,args in [('boot',['getprop','sys.boot_completed']),('pid',['pidof','system_server']),('package',['cmd','package','path','android'])]:
  try:
   p=subprocess.run(['adb','shell']+args,capture_output=True,text=True,timeout=10)
   text=(p.stdout+p.stderr).strip();result[name]=text[:500]
   if p.returncode: return dict(result,healthy=False,reason=name+' service unavailable')
  except (OSError,subprocess.TimeoutExpired) as e:return dict(result,healthy=False,reason=name+': '+type(e).__name__)
 result['healthy']=result['boot']=='1' and result['pid'].isdigit() and result['package'].startswith('package:') and '.apk' in result['package']
 return result

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--output',required=True);args=parser.parse_args()
 report=wait_for_android(probe_adb);pathlib.Path(args.output).write_text(json.dumps(report,indent=2)+'\n')
 print('Android emulator preflight:',report['status'])
 return 0 if report['status']=='ready' else 70
if __name__=='__main__':raise SystemExit(main())
