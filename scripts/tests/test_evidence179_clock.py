"""Synthetic host controls; never Android runtime evidence."""
import base64
import copy
import importlib
import json
from pathlib import Path
import tempfile
import unittest
from scripts import verify_evidence179_routes as routes


def api():
    # A missing implementation is an intentional RED assertion, not an import error.
    assert Path('scripts/evidence179_clock.py').is_file(), 'clock implementation missing'
    return importlib.import_module('scripts.evidence179_clock')


class ClockArithmetic(unittest.TestCase):
    def test_independent_upper_dominates_narrower_arm_intersection(self):
        c=api()
        arm={'monotonic':10.0}; action={'arm':{'sent_elapsed_ns':1000000000,'received_elapsed_ns':1010000000},'action_elapsed_ns':1020000000}
        probe={'host_send_ns':10000000000,'host_receive_ns':10090000000,'sample_elapsed_ns':1020000000}
        with self.assertRaisesRegex(ValueError,'independent probe'):
            c.check_interval(action,arm,probe,{'monotonic':10.030})
        c.check_interval(action,arm,probe,{'monotonic':10.096})
    def test_zero_age_exact_read_and_width_boundary(self):
        c=api();action={'arm':{'sent_elapsed_ns':1000000000,'received_elapsed_ns':2000000000},'action_elapsed_ns':2000000001}
        arm={'monotonic':10.0};probe={'host_send_ns':10500000000,'host_receive_ns':10600000000,'sample_elapsed_ns':2000000001}
        c.check_interval(action,arm,probe,{'monotonic':10.606})
        for key,value in [('host_receive_ns',10600000001),('sample_elapsed_ns',2000000000),('host_send_ns',10600000001),('host_send_ns',True),('host_receive_ns',float('nan')),('sample_elapsed_ns',2**63)]:
            p=dict(probe);p[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(ValueError):c.check_interval(action,arm,p,None)
    def test_disjoint_even_without_remote_close_fails(self):
        c=api()
        with self.assertRaisesRegex(ValueError,'disjoint'):
            c.check_interval({'arm':{'sent_elapsed_ns':1,'received_elapsed_ns':2},'action_elapsed_ns':3},{'monotonic':1.0},{'host_send_ns':2000000000,'host_receive_ns':2000000001,'sample_elapsed_ns':3},None)
    def test_original_close_float_is_never_rounded_up(self):
        c=api();a={'arm':{'sent_elapsed_ns':1,'received_elapsed_ns':2},'action_elapsed_ns':3};arm={'monotonic':1.0}
        p={'host_send_ns':1000000000,'host_receive_ns':1000000001,'sample_elapsed_ns':3}
        from fractions import Fraction
        upper=Fraction(1005000001,1000000000)
        close=float(upper)
        expected=Fraction.from_float(close)>=upper
        if expected:c.check_interval(a,arm,p,{'monotonic':close})
        else:
            with self.assertRaises(ValueError):c.check_interval(a,arm,p,{'monotonic':close})
        for x in (True,float('nan'),float('inf'),-1):
            with self.assertRaises(ValueError):c.check_interval(a,arm,p,{'monotonic':x})


class Firewall(unittest.TestCase):
    def fixture(self,protocol='0'):
        return 'Chain INPUT (policy ACCEPT 352K packets, 4120M bytes)\n pkts bytes target prot opt in out source destination\n 0 0 REJECT '+protocol+' -- !pg1762 * 0.0.0.0/0 203.0.113.10 reject-with icmp-port-unreachable\n'
    def test_exact_numeric_or_text_all(self):
        self.assertTrue(hasattr(routes,'check_firewall'),'strict firewall parser missing')
        for protocol in ('0','all'):routes.check_firewall(self.fixture(protocol))
    def test_whole_observation_rejects_ambiguous_or_narrow_rules(self):
        self.assertTrue(hasattr(routes,'check_firewall'),'strict firewall parser missing')
        raw=self.fixture()
        for bad in [self.fixture(p) for p in ('6','17','tcp','udp','00','allx')]+[raw.replace(a,b) for a,b in [('!pg1762','pg1762'),('!pg1762','!other'),('0.0.0.0/0','10.0.0.0/8'),('203.0.113.10','203.0.113.11'),('REJECT','ACCEPT'),(' 0 0',' -1 0'),(' opt ',' unknown '),('reject-with','garbage reject-with')]]+[raw+raw.splitlines()[-1]+'\n',raw+'Chain OTHER (policy ACCEPT)\n',raw+'garbage\n','',raw.replace(' -- ',' ! '),raw.replace(' * ',' eth0 ')]:
            with self.subTest(raw=bad),self.assertRaises(ValueError):routes.check_firewall(bad)


class BroadcastFraming(unittest.TestCase):
    def test_exact_result_and_payload_no_duplicate_or_extra_framing(self):
        c=api();payload=base64.urlsafe_b64encode(b'{"kind":"not_ready"}').decode().rstrip('=')
        raw='Broadcasting: Intent { act=clock }\nBroadcast completed: result=179, data="'+payload+'"\n'
        self.assertEqual(c.parse_reply(raw.encode(),b'',0)[1],{'kind':'not_ready'})
        for text,stderr,status in [(raw,b'',1),(raw,b'warning',0),(raw.replace('179','0'),b'',0),(raw+raw,b'',0),(raw+'extra\n',b'',0),(raw.replace(payload,'eyJ4IjoxLCJ4IjoyfQ'),b'',0),(raw.replace(payload,'e30='),b'',0)]:
            with self.subTest(raw=text),self.assertRaises(ValueError):c.parse_reply(text.encode(),stderr,status)
    def test_boot_uuid_is_strict_and_bounded(self):
        c=api();valid='12345678-1234-1234-1234-123456789abc'
        self.assertEqual(c.boot_uuid((valid+'\n').encode()),valid)
        for raw in (b'',b'permission denied',valid.upper().encode(),(valid+'\nextra').encode(),b'0'*1000):
            with self.assertRaises(ValueError):c.boot_uuid(raw)




def raw_fixture(root,case='vpn_revoke'):
    """Complete synthetic raw inventory for replay controls, not runtime acceptance."""
    from scripts.tests.test_evidence179_route_evidence import RouteEvidenceTest
    c=api();value,events,_,_,source,_,_=RouteEvidenceTest().fixture(case)
    root=Path(root);root.mkdir(parents=True)
    boot='12345678-1234-1234-1234-123456789abc'
    identity=dict(schema=1,source_sha=source,run_nonce=str(list(c.PHASES).index(case)+1)*32,case=case,serial='emulator-5554',user=0,package=c.PACKAGE,boot_id=boot,host_clock_implementation='clock_gettime(CLOCK_MONOTONIC)',host_clock_resolution_ns=1)
    rows=[];clock_samples=[];tick=1000000000
    def append(kind,argv,out,hs=None,hr=None):
        nonlocal tick
        hs=tick if hs is None else hs;hr=hs+1000 if hr is None else hr;tick=hr+1000
        seq=len(rows)+1
        rows.append(dict(sequence=seq,kind=kind,argv=argv,host_send_ns=hs,host_receive_ns=hr,exit_status=0,timed_out=False))
        (root/f'{seq:06d}.stdout').write_bytes(out);(root/f'{seq:06d}.stderr').write_bytes(b'')
    def frame(payload):
        encoded=base64.urlsafe_b64encode(json.dumps(payload,separators=(',',':')).encode()).decode().rstrip('=')
        return encoded,('Broadcasting: Intent { act=clock }\nBroadcast completed: result=179, data="'+encoded+'"\n').encode()
    adb=['adb','-s',identity['serial']]
    append('select',['adb','get-serialno'],b'emulator-5554\n')
    append('serial_before',adb+['get-serialno'],b'emulator-5554\n')
    append('user_before',adb+['shell','am','get-current-user'],b'0\n')
    append('boot_before',adb+['shell','cat','/proc/sys/kernel/random/boot_id'],(boot+'\n').encode())
    for i,phase in enumerate(c.PHASES[case]):
        action=value['cancel'] if phase=='cancel' else value['retirement']
        arm=next(x for x in events if x['phase']==phase and x['kind']=='action-arm')
        ready=dict(schema=1,kind='ready',source_sha=source,run_nonce=identity['run_nonce'],case=case,method=c.METHODS[case],classname=c.CLASS,nonce=value['nonce'],package=c.PACKAGE,pid=1234,uid=value['uid'],user=0,boot_id=boot,instrumentation=c.RUNNER,phase=phase,arm_token=action['arm']['token'],arm_sequence=action['arm']['sequence'])
        append('state',c.broadcast(identity,phase),frame(ready)[1],int(arm['monotonic']*1e9))
        sample=dict(ready,kind='sample',request_nonce=str(list(c.PHASES).index(case)*2+i+1)*32,sample_elapsed_ns=action['action_elapsed_ns'])
        encoded,out=frame(sample);clock_samples.append(encoded)
        append('sample',c.broadcast(identity,phase,ready,sample['request_nonce']),out,int(arm['monotonic']*1e9)+10000000,int(arm['monotonic']*1e9)+20000000)
    append('serial_after',adb+['get-serialno'],b'emulator-5554\n')
    append('user_after',adb+['shell','am','get-current-user'],b'0\n')
    append('boot_after',adb+['shell','cat','/proc/sys/kernel/random/boot_id'],(boot+'\n').encode())
    original_command=['timeout','--signal=TERM','--kill-after=30s','15m','./gradlew',':app:connectedDebugAndroidTest',f'-Pandroid.testInstrumentationRunnerArguments.class={c.CLASS}#{c.METHODS[case]}','-Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/data/local/tmp/hidden-vpn1762.conf','-Pandroid.testInstrumentationRunnerArguments.evidence179OnionHttpsUrl=https://'+'a'*56+'.onion',f'-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha={source}','--stacktrace']
    clock_arguments=[f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockRunNonce={identity["run_nonce"]}',f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockUser={identity["user"]}',f'-Pandroid.testInstrumentationRunnerArguments.evidence179ClockSerial={identity["serial"]}']
    proc=dict(pid=101,boot_id=boot,start_ticks=123,uid=1000,executable='/usr/bin/python3',argv=['python3','scripts/evidence179_clock.py','--case',case,'--source-sha',source,'--output','app/build/evidence179/clock/'+case,'--']+original_command)
    life=dict(source_sha=source,case=case,host_boot_start=boot,host_start_ns=999999999,helper=proc,child=dict(proc,pid=102,argv=original_command+clock_arguments),original_exit_status=0,clock_exit_status=0,errors=[],host_end_ns=tick,host_boot_end=boot,command_count=len(rows))
    for name,data in [('invocation.json',identity),('lifecycle.json',life)]: (root/name).write_text(json.dumps(data)+'\n')
    (root/'commands.jsonl').write_text(''.join(json.dumps(row)+'\n' for row in rows))
    for name in ('original-exit-status.txt','clock-exit-status.txt'):(root/name).write_text('0\n')
    value['clock']=dict(run_nonce=identity['run_nonce'],serial=identity['serial'],user=0,boot_id=boot,pid=1234,uid=value['uid'],package=c.PACKAGE,instrumentation=c.RUNNER,samples=clock_samples,closed=True)
    return value,source


class RawClockReplay(unittest.TestCase):
    def test_complete_inventory_is_replayable_at_extraction_path(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            samples,files=c.load_case(root,value,source)
            self.assertEqual(list(samples),['cancel','revoke'])
            self.assertEqual({p.name for p in files},{p.name for p in root.iterdir()})
    def test_every_required_raw_file_deletion_fails(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            for path in sorted(root.iterdir()):
                original=path.read_bytes();path.unlink()
                with self.subTest(file=path.name),self.assertRaises((ValueError,FileNotFoundError)):c.load_case(root,value,source)
                path.write_bytes(original)
    def test_foreign_identity_failed_status_and_changed_raw_payload_fail(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            c.load_case(root,value,source)
            for field,bad in [('run_nonce','e'*32),('serial','emulator-9999'),('user',1),('boot_id','22345678-1234-1234-1234-123456789abc'),('pid',9999),('uid',9999),('closed',False),('samples',[])]:
                v=copy.deepcopy(value);v['clock'][field]=bad
                with self.subTest(field=field),self.assertRaises(ValueError):c.load_case(root,v,source)
            for name,bad in [('clock-exit-status.txt',b'1\n'),('original-exit-status.txt',b'1\n'),('000006.stderr',b'warning'),('000006.stdout',b'Broadcast completed: result=0\n')]:
                path=root/name;old=path.read_bytes();path.write_bytes(bad)
                with self.subTest(file=name),self.assertRaises(ValueError):c.load_case(root,value,source)
                path.write_bytes(old)
    def test_reordered_duplicate_late_wide_and_foreign_command_fail(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root);path=root/'commands.jsonl';old=path.read_bytes()
            for defect in ('sequence','duplicate','argv','wide','reversed','bool','failed','timeout'):
                rows=[json.loads(line) for line in old.splitlines()]
                if defect=='sequence':rows[5]['sequence']=5
                if defect=='duplicate':rows.insert(6,rows[5])
                if defect=='argv':rows[5]['argv'][2]='emulator-9999'
                if defect=='wide':rows[5]['host_receive_ns']=rows[5]['host_send_ns']+100000001
                if defect=='reversed':rows[5]['host_receive_ns']=rows[5]['host_send_ns']-1
                if defect=='bool':rows[5]['host_send_ns']=True
                if defect=='failed':rows[5]['exit_status']=1
                if defect=='timeout':rows[5]['timed_out']=True
                path.write_text(''.join(json.dumps(x)+'\n' for x in rows))
                with self.subTest(defect=defect),self.assertRaises(ValueError):c.load_case(root,value,source)
            path.write_bytes(old)
    def test_same_validator_cli_requires_raw_calibration_and_removes_stale_pass(self):
        import subprocess
        import xml.etree.ElementTree as ET
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);xml=root/'android';xml.mkdir();manifest=json.loads(Path('scripts/evidence179_cases.json').read_text())
            suite=ET.Element('testsuite')
            for case in manifest:ET.SubElement(suite,'testcase',**case)
            ET.ElementTree(suite).write(xml/'synthetic.xml')
            for i,case in enumerate(c.PHASES):
                value,source=raw_fixture(root/'clock'/case,case)
                # Each invocation has independent raw bytes; use one case for deletion controls.
                (root/f'evidence179-route-{case}.json').write_text(json.dumps(value))
            (root/'source-sha.txt').write_text(source+'\n')
            command=['python3','scripts/verify_evidence179_routes.py','--android-results',str(xml),'--vpn-root',str(root/'vpn'),'--onion-root',str(root/'onion'),'--results-root',str(root),'--source-sha',source,'--cases','scripts/evidence179_cases.json']
            for name in ('invocation.json','lifecycle.json','commands.jsonl','000006.stdout','000006.stderr'):
                path=root/'clock/vpn_revoke'/name;old=path.read_bytes();path.unlink();(root/'route-proof.json').write_text('STALE SYNTHETIC CONTROL')
                result=subprocess.run(command,text=True,capture_output=True)
                self.assertNotEqual(result.returncode,0);self.assertIn(name,result.stderr);self.assertFalse((root/'route-proof.json').exists())
                path.write_bytes(old)


class RecorderStdinIsolation(unittest.TestCase):
    def test_stdin_draining_command_preserves_all_four_route_rows(self):
        import os
        import subprocess
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            # Real shell loop, Recorder and child: removing stdin isolation lets
            # the first command consume the remaining three route selections.
            launcher="""import sys
from pathlib import Path
from scripts.evidence179_clock import Recorder
root=Path(sys.argv[1])/sys.argv[2];root.mkdir()
record,out,err=Recorder(root).command('stdin-control',[sys.executable,'-c','import sys;sys.stdout.buffer.write(sys.stdin.buffer.read())'])
raise SystemExit(record['exit_status'])
"""
            script=r'''set -euo pipefail
while IFS=$'\t' read -r case method; do
 printf '%s\t%s\n' "$case" "$method" >> "$CONTROL_ROOT/visited"
 python3 -c "$CONTROL_LAUNCHER" "$CONTROL_ROOT" "$case"
done < <(printf 'vpn_revoke\tm1\nvpn_loss\tm2\ntor_revoke\tm3\ntor_death\tm4\n')
'''
            result=subprocess.run(['bash','-c',script],env={**os.environ,'CONTROL_ROOT':temp,'CONTROL_LAUNCHER':launcher},capture_output=True,text=True,timeout=10)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertEqual((root/'visited').read_text(),'vpn_revoke\tm1\nvpn_loss\tm2\ntor_revoke\tm3\ntor_death\tm4\n')
            for case in ('vpn_revoke','vpn_loss','tor_revoke','tor_death'):
                self.assertEqual((root/case/'000001.stdout').read_bytes(),b'')
                self.assertEqual((root/case/'000001.stderr').read_bytes(),b'')
                row=json.loads((root/case/'commands.jsonl').read_text())
                self.assertEqual(row['exit_status'],0);self.assertFalse(row['timed_out'])


class HostProtocolProcess(unittest.TestCase):
    """Real subprocess acquisition with a disposable fake device, never Android proof."""
    def run_host(self,defect='',original=0,stdin_payload=None):
        import os
        import subprocess
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);bin=root/'bin';bin.mkdir();output=root/'observations'
            adb=bin/'adb'
            adb.write_text(r'''#!/usr/bin/env python3
import base64,json,os,sys,time
args=sys.argv[1:];defect=os.environ.get('CLOCK_DEFECT','')
if '-s' in args:args=args[2:]
if args==['get-serialno']:print('emulator-5554');raise SystemExit()
if args==['shell','am','get-current-user']:print('0');raise SystemExit()
if args==['shell','cat','/proc/sys/kernel/random/boot_id']:
 if defect=='boot_denied':print('Permission denied',file=sys.stderr);raise SystemExit(1)
 print('12345678-1234-1234-1234-123456789abc');raise SystemExit()
assert args[:3]==['shell','am','broadcast'],args
extras={args[i+1]:args[i+2] for i,x in enumerate(args) if x=='--es'}
print('Broadcasting: Intent { act=synthetic-clock }',flush=True)
if defect=='missing_receiver':print('Broadcast completed: result=0');raise SystemExit()
phase=extras['phase']
v=dict(schema=1,kind='ready',source_sha=extras['source_sha'],run_nonce=extras['run_nonce'],case=extras['case'],method=extras['method'],classname='uk.co.traynor.privategallery.core.evidence.EvidenceOwnedRoute179Test',nonce='a'*32,package='uk.co.traynor.privategallery.hiddenprototype',pid=1234,uid=10123,user=0,boot_id='12345678-1234-1234-1234-123456789abc',instrumentation='androidx.test.runner.AndroidJUnitRunner',phase=phase,arm_token='d'*32,arm_sequence=5 if phase=='cancel' else 9)
if extras['op']=='SAMPLE':
 if defect=='timeout':time.sleep(2)
 v.update(kind='sample',request_nonce=extras['request_nonce'],sample_elapsed_ns=10000000000)
 if defect=='wrong_nonce':v['nonce']='b'*32
 if defect=='changed_pid':v['pid']=9999
 if defect=='early_reply':v['sample_elapsed_ns']=0
payload=base64.urlsafe_b64encode(json.dumps(v).encode()).decode().rstrip('=')
print('Broadcast completed: result=179, data="'+payload+'"')
''');adb.chmod(0o755)
            child=root/'invocation.py';child.write_text("import os,time,sys\nfrom pathlib import Path\nif os.environ.get('CLOCK_READ_STDIN')=='1':Path(os.environ['CLOCK_STDIN_PATH']).write_bytes(sys.stdin.buffer.read())\nif os.environ['CLOCK_DEFECT']!='snapshot_after_exit':time.sleep(.6)\nsys.exit(int(os.environ['CLOCK_ORIGINAL']))\n")
            # This container does not expose its PID namespace in /proc. Simulate
            # only snapshots; acquisition, process wait/status and timeouts remain real.
            launcher="""from scripts import evidence179_clock as c
from unittest.mock import patch
import os
def snapshot(pid):
 if pid!=os.getpid() and os.environ['CLOCK_DEFECT']=='snapshot_after_exit':
  os.waitid(os.P_PID,pid,os.WEXITED|os.WNOWAIT)
  raise ValueError('Owned process already exited')
 return dict(pid=pid,boot_id='12345678-1234-1234-1234-123456789abc',start_ticks=123,uid=1000,executable='/synthetic/python3',argv=['synthetic'])
with patch.object(c,'process_identity',snapshot):c.main()
"""
            command=['python3','-c',launcher,'--case','vpn_revoke' ,'--source-sha','b'*40,'--output',str(output),'--','python3',str(child)]
            stdin_path=root/'original-stdin.bin'
            result=subprocess.run(command,env={**os.environ,'PATH':str(bin)+':'+os.environ['PATH'],'CLOCK_DEFECT':defect,'CLOCK_ORIGINAL':str(original),'CLOCK_READ_STDIN':str(int(stdin_payload is not None)),'CLOCK_STDIN_PATH':str(stdin_path)},input=stdin_payload,text=True,capture_output=True,timeout=8)
            self.assertTrue((output/'lifecycle.json').is_file(),result.stderr)
            lifecycle=json.loads((output/'lifecycle.json').read_text());rows=[json.loads(line) for line in (output/'commands.jsonl').read_text().splitlines()]
            raw={p.name:p.read_bytes() for p in output.iterdir()}
            if stdin_path.exists():raw['original-stdin-observation']=stdin_path.read_bytes()
            return result,lifecycle,rows,raw
    def test_original_child_sees_eof_without_losing_real_status(self):
        for defect,original,clock_status in (('',0,0),('',23,0),('wrong_nonce',23,1)):
            with self.subTest(defect=defect,original=original):
                result,life,rows,raw=self.run_host(defect,original,stdin_payload='vpn_loss\tm2\ntor_revoke\tm3\ntor_death\tm4\n')
                self.assertEqual(raw['original-stdin-observation'],b'')
                self.assertEqual(life['original_exit_status'],original)
                self.assertEqual(life['clock_exit_status'],clock_status)
                self.assertEqual(result.returncode,original or clock_status)
                self.assertEqual(raw['original-exit-status.txt'],(str(original)+'\n').encode())
                self.assertEqual(raw['clock-exit-status.txt'],(str(clock_status)+'\n').encode())
    def test_real_host_commands_bracket_exactly_two_one_shot_samples(self):
        r,life,rows,raw=self.run_host()
        self.assertEqual(r.returncode,0,r.stderr+repr(life));self.assertEqual(life['clock_exit_status'],0)
        samples=[row for row in rows if row['kind']=='sample'];self.assertEqual(len(samples),2)
        for row in samples:
            self.assertGreater(row['host_receive_ns'],row['host_send_ns']);self.assertLessEqual(row['host_receive_ns']-row['host_send_ns'],100000000)
            self.assertIn('--user',row['argv']);self.assertIn('-p',row['argv']);self.assertNotIn('--async',row['argv'])
        self.assertNotEqual(life['helper']['pid'],life['child']['pid'])
        self.assertEqual(raw['original-exit-status.txt'],b'0\n')
    def test_clock_protocol_failure_keeps_true_original_process_status_and_raw_errors(self):
        for defect in ('wrong_nonce','changed_pid','early_reply','timeout','missing_receiver'):
            r,life,rows,raw=self.run_host(defect,23)
            with self.subTest(defect=defect):
                self.assertEqual(r.returncode,23,r.stderr);self.assertEqual(life['original_exit_status'],23);self.assertEqual(life['clock_exit_status'],1);self.assertTrue(life['errors'])
                self.assertEqual(raw['original-exit-status.txt'],b'23\n');self.assertEqual(raw['clock-exit-status.txt'],b'1\n')
                self.assertLessEqual(sum(x['kind']=='sample' for x in rows),1)
                if defect=='timeout':self.assertTrue(any(x['timed_out'] and x['exit_status']==124 for x in rows))
    def test_already_exited_original_is_reaped_after_snapshot_failure(self):
        for original in (0,23):
            with self.subTest(original=original):
                result,life,rows,raw=self.run_host('snapshot_after_exit',original)
                self.assertEqual(life['original_exit_status'],original)
                self.assertEqual(raw['original-exit-status.txt'],(str(original)+'\n').encode())
                self.assertEqual(life['clock_exit_status'],1)
                self.assertEqual(result.returncode,original or 1)
                self.assertIn('Owned process already exited',life['errors'])
                self.assertNotIn('child',life)  # Failed ownership was not promoted.
                self.assertFalse(any(row['kind']=='sample' for row in rows))

    def test_denied_independent_boot_read_stops_before_android_without_privilege_fallback(self):
        r,life,rows,raw=self.run_host('boot_denied')
        self.assertNotEqual(r.returncode,0);self.assertNotIn('child',life);self.assertEqual(life['clock_exit_status'],1)
        self.assertEqual(rows[-1]['kind'],'boot_before');self.assertEqual(rows[-1]['exit_status'],1)
        self.assertTrue(any(b'Permission denied' in content for name,content in raw.items() if name.endswith('.stderr')))
        self.assertFalse(any('su' in row['argv'] or 'root' in row['argv'] for row in rows))


class StrictLifecycleScalars(unittest.TestCase):
    def test_boolean_status_identity_and_unbound_child_are_rejected(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            for name,key,bad in [('invocation.json','schema',True),('lifecycle.json','original_exit_status',False),('lifecycle.json','clock_exit_status',False),('lifecycle.json','command_count',True)]:
                path=root/name;old=path.read_bytes();v=json.loads(old);v[key]=bad;path.write_text(json.dumps(v))
                with self.subTest(key=key),self.assertRaises(ValueError):c.load_case(root,value,source)
                path.write_bytes(old)
            path=root/'lifecycle.json';v=json.loads(path.read_text());v['child']['argv']=['unrelated-process'];path.write_text(json.dumps(v))
            with self.assertRaises(ValueError):c.load_case(root,value,source)


class RawProtocolAdversaries(unittest.TestCase):
    def test_second_sample_without_ready_cannot_be_hidden_by_valid_inventory(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            path=root/'commands.jsonl';rows=[json.loads(line) for line in path.read_text().splitlines()]
            rows[6]['kind']='sample';rows[6]['argv']=rows[5]['argv']
            (root/'000007.stdout').write_bytes((root/'000006.stdout').read_bytes())
            path.write_text(''.join(json.dumps(x)+'\n' for x in rows))
            with self.assertRaisesRegex(ValueError,'without exact readiness'):c.load_case(root,value,source)
    def test_reused_challenge_fails_even_when_android_bytes_and_raw_argv_agree(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root);path=root/'commands.jsonl';rows=[json.loads(line) for line in path.read_text().splitlines()]
            encoded,payload=c.parse_reply((root/'000008.stdout').read_bytes(),b'',0);payload['request_nonce']='1'*32
            replacement=base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
            (root/'000008.stdout').write_text('Broadcasting: Intent { act=clock }\nBroadcast completed: result=179, data="'+replacement+'"\n')
            value['clock']['samples'][1]=replacement
            rows[7]['argv'][-1]='1'*32
            path.write_text(''.join(json.dumps(x)+'\n' for x in rows))
            with self.assertRaisesRegex(ValueError,'Reused challenge'):c.load_case(root,value,source)
    def test_extra_raw_files_symlinks_and_noninteger_sequence_fail(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root)
            extra=root/'extra';extra.write_text('unexpected')
            with self.assertRaises(ValueError):c.load_case(root,value,source)
            extra.unlink();target=Path(temp)/'stdout';path=root/'000006.stdout';path.rename(target);path.symlink_to(target)
            with self.assertRaises(ValueError):c.load_case(root,value,source)
            path.unlink();target.rename(path)
            path=root/'commands.jsonl';rows=[json.loads(line) for line in path.read_text().splitlines()];rows[0]['sequence']=True;path.write_text(''.join(json.dumps(x)+'\n' for x in rows))
            with self.assertRaises(ValueError):c.load_case(root,value,source)
    def test_wrong_coroutine_class_and_missing_calibration_are_rejected(self):
        from scripts.tests.test_evidence179_route_evidence import RouteEvidenceTest
        data=list(RouteEvidenceTest().fixture());data[0]['classname']='kotlinx.coroutines.SupervisorCoroutine'
        with self.assertRaisesRegex(ValueError,'Case identity'):routes.check_case(*data)
        data=list(RouteEvidenceTest().fixture());data[5].pop('cancel')
        with self.assertRaisesRegex(ValueError,'clock phases'):routes.check_case(*data)


class InvocationCommandBinding(unittest.TestCase):
    def test_helper_original_command_must_reconstruct_the_recorded_child(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'relocated';value,source=raw_fixture(root);path=root/'lifecycle.json'
            samples,_=c.load_case(root,value,source);self.assertEqual(list(samples),['cancel','revoke'])
            life=json.loads(path.read_text());helper=life['helper']['argv'];life['helper']['argv']=helper[:helper.index('--')+1]+['sh','-c','exit 23'];path.write_text(json.dumps(life))
            with self.assertRaises(ValueError):c.load_case(root,value,source)
    def test_conflicting_source_property_in_recorded_child_is_rejected(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root);path=root/'lifecycle.json';life=json.loads(path.read_text())
            life['child']['argv'].append('-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha='+'c'*40);path.write_text(json.dumps(life))
            with self.assertRaises(ValueError):c.load_case(root,value,source)
    def test_matching_helper_and_child_still_reject_duplicate_property_keys(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root);path=root/'lifecycle.json';original=json.loads(path.read_text())
            for prop in ('class','evidence179SourceSha','evidence179ClockRunNonce','evidence179ClockUser','evidence179ClockSerial'):
                life=copy.deepcopy(original);extra='-Pandroid.testInstrumentationRunnerArguments.'+prop+'=conflicting'
                life['helper']['argv'].append(extra)
                life['child']['argv'].insert(-3,extra)
                path.write_text(json.dumps(life))
                with self.subTest(property=prop),self.assertRaises(ValueError):c.load_case(root,value,source)
    def test_matching_records_still_require_the_original_runner_contract(self):
        c=api()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'clock';value,source=raw_fixture(root);path=root/'lifecycle.json';original=json.loads(path.read_text())
            for defect in ('source','selection','task','vpn','onion','stacktrace'):
                life=copy.deepcopy(original);helper=life['helper']['argv'];at=helper.index('--')+1;command=helper[at:]
                if defect=='source':command[9]='-Pandroid.testInstrumentationRunnerArguments.evidence179SourceSha='+'c'*40
                if defect=='selection':command[6]='-Pandroid.testInstrumentationRunnerArguments.class=foreign.Test#method'
                if defect=='task':command[5]=':app:testDebugUnitTest'
                if defect=='vpn':command[7]='-Pandroid.testInstrumentationRunnerArguments.hiddenVpnConfig=/foreign'
                if defect=='onion':command[8]='-Pandroid.testInstrumentationRunnerArguments.evidence179OnionHttpsUrl=http://example.invalid'
                if defect=='stacktrace':command[10]='--continue'
                life['helper']['argv']=helper[:at]+command
                life['child']['argv']=command+life['child']['argv'][-3:]
                path.write_text(json.dumps(life))
                with self.subTest(defect=defect),self.assertRaises(ValueError):c.load_case(root,value,source)


if __name__=='__main__':unittest.main()
