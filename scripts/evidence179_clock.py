#!/usr/bin/env python3
"""Test-only exact-action ADB clock observations and strict raw replay. No PASS producer."""
import argparse
import base64
from fractions import Fraction
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import threading
import time
import uuid

PACKAGE='uk.co.traynor.privategallery.hiddenprototype'
CLASS='uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test'
RUNNER='androidx.test.runner.AndroidJUnitRunner'
PHASES={'vpn_revoke':['cancel','revoke'],'vpn_loss':['loss'],'tor_revoke':['cancel','revoke'],'tor_death':['death']}
METHODS=dict(zip(PHASES,('ownedVpnPostCancellationAndAuthorityRevocationNeverFallback','ownedVpnLossCancelsAdmittedPostAndRejectsOldRoute','privateUnixTorPostCancellationAndAuthorityRevocationNeverFallback','torProcessDeathCancelsAdmittedPostAndRejectsOldRoute')))
MARGIN=5_000_000
MAX_WIDTH=100_000_000
UUID=r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}'
BASE_KEYS={'schema','kind','source_sha','run_nonce','case','method','classname','nonce','package','pid','uid','user','boot_id','instrumentation'}
READY_KEYS=BASE_KEYS|{'phase','arm_token','arm_sequence'}
SAMPLE_KEYS=READY_KEYS|{'request_nonce','sample_elapsed_ns'}


def require(ok,reason):
    if not ok:raise ValueError(reason)


def strict_int(value,minimum=1,maximum=2**63-1):
    require(type(value) is int and minimum<=value<=maximum,'Invalid integer clock/identity field')
    return value


def unique(pairs):
    result={}
    for key,value in pairs:
        require(key not in result,'Duplicate clock JSON key');result[key]=value
    return result


def decode(raw):
    return json.loads(raw,object_pairs_hook=unique,parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite clock JSON')))


def read(path):return decode(Path(path).read_bytes())


def boot_uuid(raw):
    require(type(raw) is bytes and re.fullmatch((UUID+r'\r?\n?').encode(),raw) is not None,'Missing/invalid independently read boot UUID')
    return raw.decode().strip()


def host_ns(value):
    require(type(value) in (int,float) and math.isfinite(value) and value>0,'Invalid host timestamp')
    return Fraction(value)*1_000_000_000


def check_interval(action,arm,probe,terminal):
    sent=strict_int(action['arm']['sent_elapsed_ns']);received=strict_int(action['arm']['received_elapsed_ns']);at=strict_int(action['action_elapsed_ns'])
    require(sent<=received<=at,'Invalid device clock order')
    require(strict_int(probe['sample_elapsed_ns'])==at,'Clock sample is not the exact action read')
    hs=strict_int(probe['host_send_ns']);hr=strict_int(probe['host_receive_ns'])
    require(0<=hr-hs<=MAX_WIDTH,'Wide/reversed clock request bracket')
    ha=host_ns(arm['monotonic'])
    low=max(ha+at-received-MARGIN,hs-MARGIN);high=min(ha+at-sent+MARGIN,hr+MARGIN)
    require(low<=high,'Clock intervals are disjoint')
    if terminal is not None:
        # Fractions preserve the exact recorded float. Never ceil C into passing.
        close=host_ns(terminal['monotonic'])
        require(close>=high,'Host close precedes arm/probe intersection upper')
        require(close>=hr+MARGIN,'Host close precedes independent probe upper')
    return low,high


def parse_reply(stdout,stderr,status):
    require(type(status) is int and status==0 and stderr==b'','Failed/ambiguous clock command')
    require(len(stdout)<=16384,'Oversize clock reply')
    match=re.fullmatch(rb'Broadcasting: Intent \{[^\r\n]*\}\r?\nBroadcast completed: result=179, data="([A-Za-z0-9_-]+)"\r?\n',stdout)
    require(match is not None,'Missing/ambiguous clock broadcast framing')
    encoded=match[1].decode();raw=base64.urlsafe_b64decode(encoded+'='*((-len(encoded))%4))
    require(base64.urlsafe_b64encode(raw).decode().rstrip('=')==encoded,'Noncanonical clock encoding')
    value=decode(raw);require(type(value) is dict,'Nonobject clock reply')
    return encoded,value


def no_receiver(stdout,stderr,status):
    return status==0 and stderr==b'' and re.fullmatch(rb'Broadcasting: Intent \{[^\r\n]*\}\r?\nBroadcast completed: result=0\r?\n',stdout) is not None


def broadcast(identity,phase,ready=None,request_nonce=None):
    extras=dict(op='STATE' if ready is None else 'SAMPLE',run_nonce=identity['run_nonce'],source_sha=identity['source_sha'],case=identity['case'],method=METHODS[identity['case']],phase=phase)
    if ready is not None:extras.update(nonce=ready['nonce'],arm_token=ready['arm_token'],arm_sequence=str(ready['arm_sequence']),request_nonce=request_nonce)
    argv=['adb','-s',identity['serial'],'shell','am','broadcast','--user',str(identity['user']),'--receiver-foreground','-a','uk.co.traynor.privategallery.EVIDENCE179_CLOCK_'+identity['run_nonce'],'-p',PACKAGE]
    for key,value in extras.items():
        require(type(value) is str and re.fullmatch(r'[A-Za-z0-9_.-]+',value) is not None,'Unsafe clock broadcast argument')
        argv+=['--es',key,value]
    return argv


def validate_payload(payload,identity,kind):
    expected_keys=BASE_KEYS if kind=='not_ready' else READY_KEYS if kind=='ready' else SAMPLE_KEYS
    require(set(payload)==expected_keys and type(payload['schema']) is int and payload['schema']==1 and payload['kind']==kind,'Invalid clock payload schema')
    for key in ('source_sha','run_nonce','case','package','user','boot_id'):
        require(payload[key]==identity[key],'Foreign clock '+key)
    require(payload['method']==METHODS[identity['case']] and payload['classname']==CLASS and payload['instrumentation']==RUNNER,'Foreign instrumentation identity')
    require(re.fullmatch(r'[a-f0-9]{32}',payload['nonce']) is not None,'Invalid route nonce')
    strict_int(payload['pid']);strict_int(payload['uid']);strict_int(payload['user'],0)
    require(payload['uid']//100000==payload['user'],'Wrong Android user/UID')
    if kind!='not_ready':
        require(payload['phase'] in PHASES[identity['case']] and re.fullmatch(r'[a-f0-9]{32}',payload['arm_token']) is not None,'Invalid phase/arm token')
        strict_int(payload['arm_sequence'])
    if kind=='sample':
        require(re.fullmatch(r'[a-f0-9]{32}',payload['request_nonce']) is not None,'Invalid clock challenge')
        strict_int(payload['sample_elapsed_ns'])


def process_identity(pid):
    root=Path('/proc/self') if pid==os.getpid() else Path('/proc')/str(pid)
    stat=(root/'stat').read_text();require(int(stat.split(' ',1)[0])==pid,'Unattested process/PID namespace')
    tail=stat[stat.rindex(')')+2:].split();require(tail[0]!='Z','Owned process already exited')
    argv=[os.fsdecode(x) for x in (root/'cmdline').read_bytes().split(b'\0') if x]
    require(bool(argv),'Missing owned process argv')
    uid=int(next(x for x in (root/'status').read_text().splitlines() if x.startswith('Uid:')).split()[1])
    return dict(pid=pid,boot_id=boot_uuid(Path('/proc/sys/kernel/random/boot_id').read_bytes()),start_ticks=int(tail[19]),uid=uid,executable=os.readlink(root/'exe'),argv=argv)


class Recorder:
    def __init__(self,root):self.root=Path(root);self.sequence=0
    def command(self,kind,argv,timeout=2):
        self.sequence+=1;prefix=f'{self.sequence:06d}';timed_out=False
        hs=time.monotonic_ns()
        try:
            result=subprocess.run(argv,capture_output=True,timeout=timeout)
            hr=time.monotonic_ns();stdout,stderr,status=result.stdout,result.stderr,result.returncode
        except subprocess.TimeoutExpired as failure:
            hr=time.monotonic_ns();stdout,stderr,status=failure.stdout or b'',failure.stderr or b'',124;timed_out=True
        (self.root/(prefix+'.stdout')).write_bytes(stdout);(self.root/(prefix+'.stderr')).write_bytes(stderr)
        record=dict(sequence=self.sequence,kind=kind,argv=argv,host_send_ns=hs,host_receive_ns=hr,exit_status=status,timed_out=timed_out)
        with (self.root/'commands.jsonl').open('a') as out:out.write(json.dumps(record,sort_keys=True)+'\n')
        return record,stdout,stderr


def scalar(record,stdout,stderr):
    require(record['exit_status']==0 and not record['timed_out'] and not stderr,'Independent identity read failed')
    return stdout


def clock_arguments(identity):
    return [f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockRunNonce={identity["run_nonce"]}',f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockUser={identity["user"]}',f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockSerial={identity["serial"]}']


def run(args):
    root=Path(args.output);root.mkdir(parents=True,exist_ok=False)
    recorder=Recorder(root);original=2;clock_status=1;child=None;worker=None;stop=threading.Event();errors=[]
    lifecycle=dict(source_sha=args.source_sha,case=args.case,host_boot_start=boot_uuid(Path('/proc/sys/kernel/random/boot_id').read_bytes()),host_start_ns=time.monotonic_ns(),helper=process_identity(os.getpid()))
    try:
        require(re.fullmatch(r'[a-f0-9]{40}',args.source_sha) is not None,'Invalid source')
        cmd,out,err=recorder.command('select',['adb','get-serialno'])
        serial=scalar(cmd,out,err).decode().strip();require(re.fullmatch(r'[A-Za-z0-9._:-]+',serial) is not None and serial not in ('unknown','offline'),'No unique selected transport')
        def adb(kind,tail):return recorder.command(kind,['adb','-s',serial]+tail)
        cmd,out,err=adb('serial_before',['get-serialno']);require(scalar(cmd,out,err).decode().strip()==serial,'Selected transport changed')
        cmd,out,err=adb('user_before',['shell','am','get-current-user']);raw=scalar(cmd,out,err);require(re.fullmatch(rb'(?:0|[1-9][0-9]*)\r?\n',raw) is not None,'Invalid explicit Android user');user=int(raw)
        cmd,out,err=adb('boot_before',['shell','cat','/proc/sys/kernel/random/boot_id']);boot=boot_uuid(scalar(cmd,out,err))
        clock=time.get_clock_info('monotonic');require(clock.monotonic and not clock.adjustable and clock.resolution<=.001,'Unsupported host monotonic clock')
        identity=dict(schema=1,source_sha=args.source_sha,run_nonce=uuid.uuid4().hex,case=args.case,serial=serial,user=user,package=PACKAGE,boot_id=boot,host_clock_implementation=clock.implementation,host_clock_resolution_ns=math.ceil(clock.resolution*1e9))
        (root/'invocation.json').write_text(json.dumps(identity,sort_keys=True)+'\n')
        command=args.command
        if command and command[0]=='--':command=command[1:]
        require(bool(command),'No Android invocation')
        command=command+clock_arguments(identity)
        child=subprocess.Popen(command,env={**os.environ,'ANDROID_SERIAL':serial});lifecycle['child']=process_identity(child.pid)
        def sample():
            try:
                seen_process=None
                for phase in PHASES[args.case]:
                    while not stop.is_set():
                        rec,out,err=recorder.command('state',broadcast(identity,phase))
                        if no_receiver(out,err,rec['exit_status']) and seen_process is None:
                            stop.wait(.05);continue
                        _,reply=parse_reply(out,err,rec['exit_status']);validate_payload(reply,identity,reply.get('kind'))
                        process=(reply['pid'],reply['uid'],reply['nonce'])
                        if seen_process is None:seen_process=process
                        require(process==seen_process,'Instrumentation process/nonce changed')
                        if reply['kind']=='not_ready':stop.wait(.05);continue
                        require(reply['kind']=='ready' and reply['phase']==phase,'Unexpected ready phase')
                        rec,out,err=recorder.command('sample',broadcast(identity,phase,reply,uuid.uuid4().hex),timeout=1)
                        _,result=parse_reply(out,err,rec['exit_status']);validate_payload(result,identity,'sample')
                        require(all(result[k]==v for k,v in reply.items() if k!='kind'),'Sample changed ready identity')
                        require(0<=rec['host_receive_ns']-rec['host_send_ns']<=MAX_WIDTH,'Wide clock sample')
                        break
                    else:raise ValueError('Invocation ended before required clock sample')
            except Exception as failure:errors.append(str(failure))
        worker=threading.Thread(target=sample,name='route179-clock');worker.start()
        original=child.wait();stop.set();worker.join(3);require(not worker.is_alive(),'Clock helper did not stop')
        for kind,tail in [('serial_after',['get-serialno']),('user_after',['shell','am','get-current-user']),('boot_after',['shell','cat','/proc/sys/kernel/random/boot_id'])]:
            rec,out,err=adb(kind,tail);raw=scalar(rec,out,err)
            require(raw.strip()=={'serial_after':serial.encode(),'user_after':str(user).encode(),'boot_after':boot.encode()}[kind],'Device identity changed after invocation')
        require(not errors,'; '.join(errors));clock_status=0
    except Exception as failure:errors.append(str(failure))
    finally:
        stop.set()
        if worker is not None:worker.join(3)
        if child is not None:
            if child.poll() is None:
                # Popen owns this unreaped child; do not signal an unverified PID file.
                child.terminate()
                try:child.wait(timeout=5)
                except subprocess.TimeoutExpired:child.kill()
            # Even failed ownership capture can follow an already-exited child.
            # Always reap/read its real result; sentinel 2 is only for not-started.
            original=child.wait()
        lifecycle.update(original_exit_status=original,clock_exit_status=clock_status,errors=errors,host_end_ns=time.monotonic_ns(),host_boot_end=boot_uuid(Path('/proc/sys/kernel/random/boot_id').read_bytes()),command_count=recorder.sequence)
        (root/'lifecycle.json').write_text(json.dumps(lifecycle,sort_keys=True)+'\n')
        (root/'original-exit-status.txt').write_text(str(original)+'\n');(root/'clock-exit-status.txt').write_text(str(clock_status)+'\n')
    return original or clock_status


def load_case(root,value,source):
    """Require the complete raw inventory; derive samples without trusting helper verdicts."""
    root=Path(root);identity=read(root/'invocation.json');life=read(root/'lifecycle.json')
    require(set(identity)=={'schema','source_sha','run_nonce','case','serial','user','package','boot_id','host_clock_implementation','host_clock_resolution_ns'},'Invalid clock invocation schema')
    require(type(identity['schema']) is int and identity['schema']==1 and identity['source_sha']==source and identity['case']==value['case'] and identity['package']==PACKAGE,'Foreign clock invocation')
    require(re.fullmatch(r'[a-f0-9]{32}',identity['run_nonce']) is not None and re.fullmatch(r'[A-Za-z0-9._:-]+',identity['serial']) is not None,'Invalid clock transport/run nonce')
    strict_int(identity['user'],0);strict_int(identity['host_clock_resolution_ns'],1,1_000_000);boot_uuid(identity['boot_id'].encode())
    require(type(identity['host_clock_implementation']) is str and bool(identity['host_clock_implementation']),'Missing host clock implementation')
    require(life['source_sha']==source and life['case']==value['case'] and type(life['original_exit_status']) is int and type(life['clock_exit_status']) is int and life['original_exit_status']==life['clock_exit_status']==0 and life['errors']==[],'Original clock/invocation failed')
    require((root/'original-exit-status.txt').read_bytes()==b'0\n' and (root/'clock-exit-status.txt').read_bytes()==b'0\n','Missing original clock statuses')
    require(boot_uuid(life['host_boot_start'].encode())==boot_uuid(life['host_boot_end'].encode()),'Host boot changed')
    require(strict_int(life['host_start_ns'])<=strict_int(life['host_end_ns']),'Invalid helper lifecycle')
    for key in ('helper','child'):
        proc=life[key];require(set(proc)=={'pid','boot_id','start_ticks','uid','executable','argv'} and proc['boot_id']==life['host_boot_start'] and bool(proc['executable']) and type(proc['argv']) is list and bool(proc['argv']),'Invalid owned helper/child identity')
        strict_int(proc['pid']);strict_int(proc['start_ticks']);strict_int(proc['uid'],0)
    require(life['helper']['pid']!=life['child']['pid'] and 'scripts/evidence179_clock.py' in life['helper']['argv'] and ('app/build/evidence179/clock/'+value['case']) in life['helper']['argv'],'Wrong helper process/output ownership')
    helper_argv=life['helper']['argv']
    require(helper_argv.count('--')==1 and helper_argv[1:helper_argv.index('--')]==['scripts/evidence179_clock.py','--case',value['case'],'--source-sha',source,'--output','app/build/evidence179/clock/'+value['case']],'Unbound helper invocation')
    original_command=helper_argv[helper_argv.index('--')+1:]
    child_argv=life['child']['argv']
    require(all(type(arg) is str for arg in original_command+child_argv),'Invalid invocation argument type')
    require(child_argv==original_command+clock_arguments(identity),'Helper original command and recorded child disagree')
    property_keys=[arg.split('=',1)[0] for arg in child_argv if arg.startswith('-P')]
    require(len(property_keys)==len(set(property_keys)),'Duplicate/conflicting Gradle property keys')
    require(len(original_command)==11 and original_command[:6]==['timeout','--signal=TERM','--kill-after=30s','15m','./gradlew',':app:connectedDebugAndroidTest'],'Unbound original Android runner command')
    require(original_command[6]==f'-Pandroid.testInstrumentationRunnerArguments.class={CLASS}#{METHODS[value["case"]]}' and original_command[9]==f'-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha={source}','Original Android selection/source not bound')
    require(original_command[7]=='-Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf' and re.fullmatch(r'-Pandroid\.testInstrumentationRunnerArguments\.evidence179OnionHttpsUrl=https://[a-z2-7]{56}\.onion',original_command[8]) is not None and original_command[10]=='--stacktrace','Original Android fixture/runner contract changed')
    rows=[decode(line) for line in (root/'commands.jsonl').read_bytes().splitlines()]
    require(len(rows)==strict_int(life['command_count']) and [r['sequence'] for r in rows]==list(range(1,len(rows)+1)),'Missing/reordered clock commands')
    files={'invocation.json','lifecycle.json','commands.jsonl','original-exit-status.txt','clock-exit-status.txt'}|{f'{i:06d}.{ext}' for i in range(1,len(rows)+1) for ext in ('stdout','stderr')}
    require({p.name for p in root.iterdir()}==files and all((root/name).is_file() and not (root/name).is_symlink() for name in files),'Missing/foreign clock raw artifact inventory: '+repr(sorted(files^{p.name for p in root.iterdir()})))
    adb=['adb','-s',identity['serial']]
    fixed=[('select',['adb','get-serialno'],identity['serial'].encode()),('serial_before',adb+['get-serialno'],identity['serial'].encode()),('user_before',adb+['shell','am','get-current-user'],str(identity['user']).encode()),('boot_before',adb+['shell','cat','/proc/sys/kernel/random/boot_id'],identity['boot_id'].encode())]
    tail=[('serial_after',adb+['get-serialno'],identity['serial'].encode()),('user_after',adb+['shell','am','get-current-user'],str(identity['user']).encode()),('boot_after',adb+['shell','cat','/proc/sys/kernel/random/boot_id'],identity['boot_id'].encode())]
    require(len(rows)>=len(fixed)+len(tail)+2*len(PHASES[value['case']]),'Incomplete clock command transcript')
    for row in rows:
        strict_int(row['sequence'])
        require(set(row)=={'sequence','kind','argv','host_send_ns','host_receive_ns','exit_status','timed_out'} and type(row['exit_status']) is int and row['exit_status']==0 and row['timed_out'] is False,'Failed/malformed raw clock command')
        require(life['host_start_ns']<=strict_int(row['host_send_ns'])<=strict_int(row['host_receive_ns'])<=life['host_end_ns'],'Out-of-lifecycle clock command')
    require(all(a['host_receive_ns']<=b['host_send_ns'] for a,b in zip(rows,rows[1:])),'Reordered/overlapping host clock commands')
    def raw(row):
        prefix=f'{row["sequence"]:06d}';return (root/(prefix+'.stdout')).read_bytes(),(root/(prefix+'.stderr')).read_bytes()
    for row,(kind,argv,expected) in list(zip(rows[:4],fixed))+list(zip(rows[-3:],tail)):
        out,err=raw(row);require(row['kind']==kind and row['argv']==argv and not err and out in (expected+b'\n',expected+b'\r\n'),'Independent device identity command mismatch')
    android=value['clock'];require(set(android)=={'run_nonce','serial','user','boot_id','pid','uid','package','instrumentation','samples','closed'} and android['closed'] is True,'Unclosed Android clock receiver')
    for key in ('run_nonce','serial','user','boot_id','package'):require(android[key]==identity[key],'Android/host clock identity mismatch')
    require(android['uid']==value['uid'] and android['instrumentation']==RUNNER,'Wrong clock owner/instrumentation')
    strict_int(android['pid']);strict_int(android['uid']);strict_int(android['user'],0)
    phases=PHASES[value['case']];samples={};ready=None;seen=False;challenge=set()
    for row in rows[4:-3]:
        require(len(samples)<len(phases),'Extra/retried clock observations')
        phase=phases[len(samples)];out,err=raw(row)
        if row['kind']=='state':
            require(ready is None and row['argv']==broadcast(identity,phase),'Foreign/reordered STATE command')
            if no_receiver(out,err,row['exit_status']):require(not seen,'Receiver disappeared');continue
            encoded,payload=parse_reply(out,err,row['exit_status']);kind=payload.get('kind');require(kind in ('ready','not_ready'),'Unexpected state reply')
            validate_payload(payload,identity,kind);seen=True
            if kind=='ready':require(payload['phase']==phase,'Wrong ready phase');ready=payload
        else:
            require(row['kind']=='sample' and ready is not None,'Sample without exact readiness')
            encoded,payload=parse_reply(out,err,row['exit_status']);validate_payload(payload,identity,'sample')
            require(payload['request_nonce'] not in challenge,'Reused challenge');challenge.add(payload['request_nonce'])
            require(row['argv']==broadcast(identity,phase,ready,payload['request_nonce']),'Foreign clock SAMPLE command')
            require(all(payload[k]==v for k,v in ready.items() if k!='kind'),'Changed clock readiness identity')
            samples[phase]=dict(payload,host_send_ns=row['host_send_ns'],host_receive_ns=row['host_receive_ns'],payload_b64=encoded);ready=None
        require(payload['nonce']==value['nonce'] and payload['pid']==android['pid'] and payload['uid']==android['uid'],'Foreign clock process/route nonce')
    require(list(samples)==phases and ready is None,'Missing required phase calibration')
    require(type(android['samples']) is list and android['samples']==[samples[p]['payload_b64'] for p in phases],'Android/host payload bytes differ')
    for phase in phases:
        action=value['cancel'] if phase=='cancel' else value['retirement'];probe=samples[phase]
        require(probe['arm_token']==action['arm']['token'] and probe['arm_sequence']==action['arm']['sequence'],'Foreign arm calibration')
        require(probe['sample_elapsed_ns']==action['action_elapsed_ns'],'Clock did not sample exact action')
        require(0<=probe['host_receive_ns']-probe['host_send_ns']<=MAX_WIDTH,'Wide/reversed raw sample')
    return samples,[root/name for name in sorted(files)]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--case',choices=PHASES,required=True);parser.add_argument('--source-sha',required=True);parser.add_argument('--output',required=True);parser.add_argument('command',nargs=argparse.REMAINDER)
    raise SystemExit(run(parser.parse_args()))

if __name__=='__main__':main()
