"""Host mutation tests for separately archived actual Kotlin lifecycle controls."""
import base64
import copy
import importlib
import json
from pathlib import Path
import tempfile
import unittest
from scripts import evidence179_clock as clock

NAMES=['competing_samples','cancel_before_claim','cancel_after_claim','timeout_before_read','completion_deadline_race','late_stale_request','cleanup_throws','cancelled_caller']

def api():
    assert Path('scripts/evidence179_clock_lifecycle.py').is_file(),'lifecycle replay decoder missing'
    return importlib.import_module('scripts.evidence179_clock_lifecycle')

def fixture(completed=False,positive_override=None,source_override=None):
    source='a'*40;boot='12345678-1234-1234-1234-123456789abc'
    identity=dict(pid=1234,uid=10123,user=0,package=clock.PACKAGE,boot_id=boot,instrumentation=clock.RUNNER)
    positive=dict(case='vpn_revoke',nonce='b'*32,uid=10123,clock=dict(identity,run_nonce='c'*32))
    positive=positive_override or positive;source=source_override or source
    top=dict(schema=1,kind='android_clock_lifecycle_controls',source_sha=source,case='vpn_revoke',method=clock.METHODS['vpn_revoke'],classname=clock.CLASS,parent_route_nonce=positive['nonce'],identity=identity,scenarios=[])
    for index,name in enumerate(NAMES):
        run_nonce=f'{index+1:032x}';nonce=f'{index+20:032x}';token=f'{index+40:032x}'
        common=dict(schema=1,kind='ready',source_sha=source,run_nonce=run_nonce,case='vpn_loss',method=clock.METHODS['vpn_loss'],classname=clock.CLASS,nonce=nonce,**identity,phase='loss',arm_token=token,arm_sequence=1)
        specs=[('STATE',179,common)]
        count=2 if name in ('competing_samples','late_stale_request') else 1
        win=completed and name=='completion_deadline_race'
        duplicate=name=='competing_samples'
        sample_value=1003000000 if duplicate else 2001005000 if win else 0
        for i in range(count):
            request=f'{index*3+i+100:032x}'
            success=win or duplicate and i==0
            reply=dict(common,kind='sample',request_nonce=request,sample_elapsed_ns=sample_value) if success else dict(error='clock_rejected' if name in ('cancel_before_claim','late_stale_request') or i else 'clock_failed')
            specs.append(('SAMPLE',179 if success else 178,reply))
        specs.append(('STATE',0,None));events=[]
        for seq,(op,code,payload) in enumerate(specs):
            extras=dict(op=op,run_nonce=run_nonce,source_sha=source,case='vpn_loss',method=clock.METHODS['vpn_loss'],phase='loss')
            if op=='SAMPLE':extras.update(nonce=nonce,arm_token=token,arm_sequence='1',request_nonce=f'{index*3+seq-1+100:032x}')
            argv=['timeout','3','am','broadcast','--user','0','--receiver-foreground','-a','uk.co.traynor.privategallery.EVIDENCE179_CLOCK_'+run_nonce,'-p',clock.PACKAGE]
            for key,value in extras.items():argv+=['--es',key,value]
            if name=='late_stale_request' and seq==2:extras['arm_token']='e'*32;argv[-7]='e'*32
            raw='Broadcasting: Intent { act=synthetic }\nBroadcast completed: result='+str(code)
            if payload is not None:raw+=', data="'+base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')+'"'
            raw+='\n\nE179_CLOCK_STATUS=0\n'
            sent=1000000000+seq*1000000;received=sent+100000
            if name=='competing_samples' and op=='SAMPLE':received=1010000000
            if name in ('timeout_before_read','completion_deadline_race') and op=='SAMPLE':received=2001100000
            if seq==len(specs)-1:sent=2020000000;received=sent+100000
            script=" ".join("'"+x+"'" for x in argv)+"\ne179_status=$?\nprintf '\\nE179_CLOCK_STATUS=%s\\n' \"$e179_status\"\nexit \"$e179_status\"\n"
            events.append(dict(argv=argv,entrypoint='sh -s',script=script,stdout_b64=base64.urlsafe_b64encode(raw.encode()).decode().rstrip('='),stderr_b64='',raw=raw,sent_elapsed_ns=sent,received_elapsed_ns=received))
        accepted=0 if name in ('cancel_before_claim','late_stale_request') else 1
        poison=None if win else {'competing_samples':'Rejected clock command: java.lang.IllegalStateException','cancel_before_claim':'Clock rendezvous cancelled/expired','cancel_after_claim':'Clock closed without sample','timeout_before_read':'Clock sample deadline expired','completion_deadline_race':'Clock sample deadline expired','late_stale_request':'Clock rendezvous cancelled/expired','cleanup_throws':'Clock closed without sample','cancelled_caller':'Clock closed without sample'}[name]
        observation=dict(registered=False,thread_alive=False,owner_thread_id=42,poison=poison,poison_order=0 if win else 3 if duplicate else 1,poison_elapsed_ns=0 if win else 1005000000 if duplicate else 2001020000 if name in ('timeout_before_read','completion_deadline_race') else 1001050000,claims=[dict(phase='loss',accepted=accepted,finish_count=accepted,finish_thread_id=42 if accepted else 0,pending=False,sample_elapsed_ns=sample_value,claimed_elapsed_ns=1001010000 if accepted else 0,sample_order=1 if sample_value else 0,finish_order=2 if accepted else 0,finish_elapsed_ns=(1004000000 if duplicate else 2001060000 if win or name in ('timeout_before_read','completion_deadline_race') else 1001060000) if accepted else 0)])
        top['scenarios'].append(dict(name=name,run_nonce=run_nonce,nonce=nonce,arm_token=token,events=events,observation=observation,close_succeeded=win,post_close_action_error=poison or 'Clock is closing',action_elapsed_ns=sample_value,primary_error='injected-route-cleanup' if name=='cleanup_throws' else 'cancelled' if name in ('cancel_after_claim','cancelled_caller') else '',caller_cancelled=name in ('cancel_before_claim','cancel_after_claim','cancelled_caller')))
        last=top['scenarios'][-1];cancelled=name in ('cancel_after_claim','cancelled_caller');injected=name=='cleanup_throws'
        last.update(deadline_target_elapsed_ns=2001010000 if name=='completion_deadline_race' else 0,deadline_release_elapsed_ns=(2001000000 if win else 2001020000) if name=='completion_deadline_race' else 0,close_error_type='' if win else 'kotlinx.coroutines.JobCancellationException' if cancelled else 'java.lang.IllegalStateException',close_error_message='' if win else 'synthetic caller cancellation' if cancelled else 'injected-route-cleanup' if injected else poison,suppressed_close_errors=[dict(type='java.lang.IllegalStateException',message=poison)] if cancelled or injected else [])
    return top,positive,source

class LifecycleReplay(unittest.TestCase):
    def test_all_eight_controls_and_exclusive_deadline_winners(self):
        c=api()
        for completed in (False,True):
            value,positive,source=fixture(completed);c.check(value,positive,source)
    def test_missing_duplicate_foreign_or_generic_pass_cannot_replace_controls(self):
        c=api()
        for defect in ('missing','duplicate','source','parent','pid','nonce','generic','thread','pending','double_finish','owner','published_after_poison','no_retirement','bad_status','raw_mutation','wrong_package','swallowed_cleanup','wrong_error','deadline_early','missing_deadline'):
            value,positive,source=fixture();row=value['scenarios'][0]
            if defect=='missing':value['scenarios'].pop()
            if defect=='duplicate':value['scenarios'][-1]=copy.deepcopy(row)
            if defect=='source':value['source_sha']='f'*40
            if defect=='parent':value['parent_route_nonce']='e'*32
            if defect=='pid':value['identity']['pid']=4321
            if defect=='nonce':row['run_nonce']=positive['clock']['run_nonce']
            if defect=='generic':value['scenarios']=[dict(name=name,PASS=True) for name in NAMES]
            if defect=='thread':row['observation']['thread_alive']=True
            if defect=='pending':row['observation']['claims'][0]['pending']=True
            if defect=='double_finish':row['observation']['claims'][0]['finish_count']=2
            if defect=='owner':row['observation']['claims'][0]['finish_thread_id']=99
            if defect=='published_after_poison':row['observation']['claims'][0]['sample_elapsed_ns']=123
            if defect=='no_retirement':row['events'].pop()
            if defect=='bad_status':row['events'][0]['raw']=row['events'][0]['raw'].replace('STATUS=0','STATUS=124')
            if defect=='raw_mutation':row['events'][1]['raw']='PASS'
            if defect=='wrong_package':row['events'][0]['argv'][10]='foreign.package'
            if defect=='swallowed_cleanup':value['scenarios'][6]['suppressed_close_errors']=[]
            if defect=='wrong_error':row['close_error_type']='foreign.Error'
            if defect=='deadline_early':value['scenarios'][4]['deadline_release_elapsed_ns']=1
            if defect=='missing_deadline':value['scenarios'][4]['deadline_target_elapsed_ns']=0
            with self.subTest(defect=defect),self.assertRaises((ValueError,KeyError)):c.check(value,positive,source)
    def test_required_artifact_and_matching_android_copy(self):
        c=api();value,positive,source=fixture()
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);copydir=root/'android-output/vpn_revoke/device';copydir.mkdir(parents=True)
            name='evidence179-clock-lifecycle.json';data=json.dumps(value).encode();(root/name).write_bytes(data);(copydir/name).write_bytes(data)
            self.assertEqual(len(c.verify(root,positive,source)),2)
            (root/name).unlink()
            with self.assertRaises((ValueError,FileNotFoundError)):c.verify(root,positive,source)
            (root/name).write_bytes(data);(copydir/name).write_bytes(b'{}')
            with self.assertRaises(ValueError):c.verify(root,positive,source)

class LifecycleCliReplay(unittest.TestCase):
    def test_same_frozen_cli_requires_lifecycle_raw_bytes_and_semantics(self):
        import subprocess
        import xml.etree.ElementTree as ET
        from scripts.tests.test_evidence179_clock import raw_fixture
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);xml=root/'android';xml.mkdir();suite=ET.Element('testsuite')
            for case in json.loads(Path('scripts/evidence179_cases.json').read_text()):ET.SubElement(suite,'testcase',**case)
            ET.ElementTree(suite).write(xml/'synthetic.xml')
            values={}
            for case in clock.PHASES:
                value,source=raw_fixture(root/'clock'/case,case);values[case]=value
                (root/f'evidence179-route-{case}.json').write_text(json.dumps(value))
            (root/'source-sha.txt').write_text(source+'\n')
            control,_,_=fixture(positive_override=values['vpn_revoke'],source_override=source)
            path=root/'evidence179-clock-lifecycle.json';copy=root/'android-output/vpn_revoke/device'/path.name;copy.parent.mkdir(parents=True)
            command=['python3','scripts/verify_evidence179_routes.py','--android-results',str(xml),'--vpn-root',str(root/'vpn'),'--onion-root',str(root/'onion'),'--results-root',str(root),'--source-sha',source,'--cases','scripts/evidence179_cases.json']
            for defect in ('valid_controls_then_other_gate','missing_raw','missing_copy','duplicate','poisoned_sample','wrong_owner','bad_reply'):
                data=__import__('copy').deepcopy(control)
                if defect=='duplicate':data['scenarios'][-1]=data['scenarios'][0]
                if defect=='poisoned_sample':data['scenarios'][0]['observation']['claims'][0]['sample_elapsed_ns']=10
                if defect=='wrong_owner':data['identity']['pid']=9999
                if defect=='bad_reply':data['scenarios'][0]['events'][1]['raw']='generic PASS'
                path.write_text(json.dumps(data));copy.write_bytes(path.read_bytes())
                if defect=='missing_raw':path.unlink()
                if defect=='missing_copy':copy.unlink()
                (root/'route-proof.json').write_text('STALE SYNTHETIC CONTROL')
                result=subprocess.run(command,capture_output=True,text=True)
                with self.subTest(defect=defect):
                    self.assertNotEqual(result.returncode,0);self.assertFalse((root/'route-proof.json').exists())
                    if defect=='valid_controls_then_other_gate':self.assertIn('host-phases.jsonl',result.stderr)
                    else:self.assertIn('evidence179_clock_lifecycle.py',result.stderr);self.assertNotIn('host-phases.jsonl',result.stderr)

class LifecycleFixReviewControls(unittest.TestCase):
    def test_finished_first_sample_precedes_duplicate_poison_and_cannot_close_successfully(self):
        c=api();value,positive,source=fixture();c.check(value,positive,source)
        for defect in ('poison_before_read','finish_after_poison','sample_after_poison','healthy_close','deadline_poison','two_failed_requests'):
            v=copy.deepcopy(value);row=v['scenarios'][0];obs=row['observation'];claim=obs['claims'][0]
            if defect=='poison_before_read':obs['poison_elapsed_ns']=row['action_elapsed_ns']-1
            if defect=='finish_after_poison':claim['finish_order']=obs['poison_order']+1
            if defect=='sample_after_poison':claim['sample_order']=obs['poison_order']+1
            if defect=='healthy_close':row['close_succeeded']=True
            if defect=='deadline_poison':obs['poison']='Clock sample deadline expired'
            if defect=='two_failed_requests':row['events'][1]['raw']=row['events'][2]['raw'];row['events'][1]['stdout_b64']=row['events'][2]['stdout_b64']
            with self.subTest(defect=defect),self.assertRaises(ValueError):c.check(v,positive,source)
    def test_entrypoint_script_and_raw_stream_bindings_are_required(self):
        c=api();value,positive,source=fixture()
        for key,bad in [('entrypoint','sh -c quoted script'),('script','echo PASS\n'),('stdout_b64',''),('stderr_b64','ZXJyb3I')]:
            v=copy.deepcopy(value);v['scenarios'][0]['events'][0][key]=bad
            with self.subTest(key=key),self.assertRaises(ValueError):c.check(v,positive,source)
    def test_real_host_java_runtime_exec_uses_constant_shell_and_stdin_script(self):
        import os,subprocess
        c=api();self.assertTrue(hasattr(c,'script_for'),'explicit shell script contract missing')
        kotlin=Path('app/src/androidTest/java/uk/co/traynor/privategallery/core/evidence/EvidenceRouteClockLifecycle179.kt').read_text()
        self.assertIn('executeShellCommandRwe("sh -s")',kotlin)
        for use in ('AutoCloseInputStream(fds[0])','AutoCloseOutputStream(fds[1])','AutoCloseInputStream(fds[2])'):self.assertIn(use,kotlin)
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);bin=root/'bin';bin.mkdir();am=bin/'am'
            am.write_text("#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$AM_ARGS\"\nprintf 'Broadcasting: Intent { act=transport }\\nBroadcast completed: result=0\\n'\nif [ \"$AM_STATUS\" != 0 ]; then printf 'error-stream\\n' >&2; fi\nexit \"$AM_STATUS\"\n");am.chmod(0o755)
            java=root/'ShellRuntimeContract.java'
            java.write_text(r'''import java.nio.file.*; import java.util.*; import java.util.concurrent.*;
class ShellRuntimeContract {
 public static void main(String[] args) throws Exception {
  try { Runtime.getRuntime().exec("'timeout' '3' 'am' 'broadcast' 2>&1; exit 0"); throw new AssertionError("Quoted executable unexpectedly launched"); }
  catch(java.io.IOException expected) { System.out.println("quoted-string-rejected"); }
  Process p=Runtime.getRuntime().exec("sh -s");
  try(var input=p.getOutputStream()){input.write(Files.readAllBytes(Path.of(args[0])));}
  byte[] out=p.getInputStream().readAllBytes(),err=p.getErrorStream().readAllBytes();
  if(!p.waitFor(5,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Unbounded shell");}
  System.out.println(Base64.getEncoder().encodeToString(out));System.out.println(Base64.getEncoder().encodeToString(err));System.out.println(p.exitValue());
 }
}''')
            event=fixture()[0]['scenarios'][0]['events'][0]
            block=kotlin[kotlin.index('   val script='):kotlin.index('   val sent=',kotlin.index('   val script='))]
            body=block.split('"""')[1].strip('\n').replace("${'$'}",'$')
            actual_script=' '.join("'"+x+"'" for x in event['argv'])+'\n'+body+'\n'
            self.assertEqual(actual_script,c.script_for(event['argv']))
            script=root/'script';script.write_text(actual_script)
            for status in (0,23):
                result=subprocess.run(['java',str(java),str(script)],capture_output=True,text=True,timeout=15,env={**os.environ,'PATH':str(bin)+':'+os.environ['PATH'],'AM_STATUS':str(status),'AM_ARGS':str(root/'args')})
                self.assertEqual(result.returncode,0,result.stderr);lines=result.stdout.splitlines();self.assertEqual(lines[0],'quoted-string-rejected')
                stdout=base64.b64decode(lines[1]);stderr=base64.b64decode(lines[2]);self.assertEqual(int(lines[3]),status)
                self.assertTrue(stdout.endswith(('E179_CLOCK_STATUS='+str(status)+'\n').encode()))
                self.assertEqual(stderr,b'' if status==0 else b'error-stream\n')
                self.assertEqual((root/'args').read_text().splitlines(),event['argv'][3:])

if __name__=='__main__':unittest.main()
