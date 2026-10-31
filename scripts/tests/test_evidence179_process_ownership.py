import copy
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import unittest
from unittest.mock import Mock,patch
from scripts import evidence179_processes as p
from scripts import archive_evidence179_routes as archive


def identity(pid=123):
 return dict(pid=pid,boot_id='boot-a',start_ticks=55,uid=0,executable='/usr/bin/tcpdump',argv=['tcpdump','-w','/fixture/traffic.pcap'],outputs={'/fixture/traffic.pcap':dict(device=1,inode=22)})

class FakeSystem:
 def __init__(self,value,exited=True):self.value=value;self.exited=exited;self.signals=[];self.closed=[]
 def open(self,pid):
  if self.value is None:raise ProcessLookupError()
  return 777
 def inventory(self,pid):return self.value
 def send(self,fd,sig):self.signals.append((fd,sig))
 def wait(self,fd,timeout):return self.exited
 def close(self,fd):self.closed.append(fd)

class OwnershipTest(unittest.TestCase):
 def snapshot(self):
  value=identity();value['files']=value.pop('outputs');return value
 def test_pidfd_signals_only_exact_start_argv_executable_uid_and_open_output(self):
  for changed in (None,'start_ticks','boot_id','uid','executable','argv','files'):
   value=self.snapshot()
   if changed=='files':value['files']['/fixture/traffic.pcap']['inode']=999
   elif changed is not None:value[changed]=['other'] if changed=='argv' else 999 if changed in ('uid','start_ticks') else 'other'
   system=FakeSystem(value)
   result=p.terminate(identity(),signal.SIGINT,system=system)
   self.assertEqual(result,'stopped' if changed is None else 'ownership-rejected')
   self.assertEqual(system.signals,[(777,signal.SIGINT)] if changed is None else [])
   self.assertEqual(system.closed,[777])
 def test_missing_and_unflushed_process_never_claim_flush_or_signal_another_pid(self):
  missing=FakeSystem(None);self.assertEqual(p.terminate(identity(),system=missing),'missing');self.assertEqual(missing.signals,[])
  live=FakeSystem(self.snapshot(),exited=False);self.assertEqual(p.terminate(identity(),signal.SIGINT,system=live),'unflushed');self.assertEqual(live.signals,[(777,signal.SIGINT)])
 def test_sudo_wrapper_registration_selects_exact_child_and_records_start(self):
  with tempfile.TemporaryDirectory() as directory:
   output=str(Path(directory,'traffic.pcap').resolve());Path(output).touch()
   child=self.snapshot();child['pid']=124;child['argv']=['tcpdump','-w',output];child['files']={output:dict(device=1,inode=22)}
   wrapper=dict(child,pid=123,argv=['sudo','tcpdump','-w',output],executable='/usr/bin/sudo')
   with patch.object(p,'descendants',return_value=[123,124]),patch.object(p,'inventory',side_effect=lambda pid:{123:wrapper,124:child}[pid]):
    result=p.register(Path(directory,'capture.identity.json'),123,'tcpdump',[output],[output])
   self.assertEqual(result['pid'],124);self.assertEqual(result['start_ticks'],55);self.assertEqual(result['outputs'],{output:dict(device=1,inode=22)})
 def test_open_pidfd_then_reused_identity_is_rejected_without_any_signal(self):
  system=FakeSystem(dict(self.snapshot(),start_ticks=56))
  self.assertEqual(p.terminate(identity(),system=system),'ownership-rejected');self.assertEqual(system.signals,[])

class ArchiveCleanupTest(unittest.TestCase):
 def test_rejected_archive_cannot_fall_through_to_legacy_numeric_pid_kill(self):
  repository=Path(__file__).resolve().parents[2]
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);private=root/'private-gallery-vpn1762';private.mkdir();(private/'capture.pid').write_text('123')
   (private/'capture.identity.json').write_text(json.dumps(identity()))
   work=root/'work';work.mkdir();(work/'app/build/hidden-vpn1762').mkdir(parents=True)
   env={'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.9-evidence-locker','RUNNER_TEMP':str(root),'GITHUB_WORKSPACE':str(work)}
   prior=Path.cwd()
   try:
    os.chdir(work)
    def command(args,**kwargs):
     if 'evidence179_processes.py' in ' '.join(args):return Mock(returncode=1,stdout='ownership-rejected\n')
     return Mock(returncode=0,stdout=b'public\n')
    with patch.dict(os.environ,env),patch.object(archive.subprocess,'run',side_effect=command) as run:
     archive.archive()
    self.assertFalse(json.loads((work/'app/build/evidence179/capture.json').read_text())['stopped'])
    self.assertTrue((private/'capture.identity.json').exists());self.assertTrue((private/'capture.pid').exists())
    self.assertFalse(any('kill' in args.args[0] for args in run.call_args_list))
   finally:os.chdir(prior)
   binpath=root/'bin';binpath.mkdir();log=root/'commands.log'
   sudo=binpath/'sudo';sudo.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$COMMAND_LOG"\ncase "$*" in *evidence179_processes.py*) printf "ownership-rejected\\n"; exit 1;; esac\nexit 0\n');sudo.chmod(0o755)
   result=subprocess.run(['bash',str(repository/'scripts/cleanup_hidden_vpn_fixture1762.sh')],cwd=repository,env={**os.environ,**env,'PATH':str(binpath)+':'+os.environ['PATH'],'COMMAND_LOG':str(log)},capture_output=True,text=True)
   self.assertNotEqual(result.returncode,0)
   calls=log.read_text().splitlines();self.assertTrue(any('evidence179_processes.py stop' in x for x in calls));self.assertFalse(any('kill' in x for x in calls))
 def test_unflushed_capture_fails_and_completed_archive_is_immutable(self):
  for outcome in ('unflushed','stopped'):
   with self.subTest(outcome=outcome),tempfile.TemporaryDirectory() as directory:
    root=Path(directory);private=root/'private-gallery-vpn1762';private.mkdir();(private/'capture.identity.json').write_text(json.dumps(identity()));(private/'capture.pid').write_text('123')
    work=root/'work';work.mkdir();prior=Path.cwd()
    try:
     os.chdir(work)
     def command(args,**kwargs):
      if 'evidence179_processes.py' in ' '.join(args):return Mock(returncode=0 if outcome=='stopped' else 1,stdout=outcome+'\n')
      return Mock(returncode=0,stdout=b'public\n')
     with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.9-evidence-locker','RUNNER_TEMP':str(root)}),patch.object(archive.subprocess,'run',side_effect=command):archive.archive()
     file=work/'app/build/evidence179/capture.json';before=file.read_bytes();self.assertEqual(json.loads(before)['stopped'],outcome=='stopped')
     with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.9-evidence-locker','RUNNER_TEMP':str(root)}),patch.object(archive.subprocess,'run') as run:archive.archive();run.assert_not_called()
     self.assertEqual(file.read_bytes(),before);self.assertEqual((private/'capture.pid').exists(),outcome!='stopped')
    finally:os.chdir(prior)


class ControlledDrainTest(unittest.TestCase):
 def test_drain_is_after_proof_and_refuses_socket_cleanup_without_verified_owner(self):
  from scripts import drain_evidence179_vpn as d
  for outcome in ('stopped','ownership-rejected'):
   with self.subTest(outcome=outcome),tempfile.TemporaryDirectory() as directory:
    root=Path(directory);private=root/'private-gallery-vpn1762';private.mkdir();(private/'server.identity.json').write_text('{}');(private/'server.pid').write_text('123')
    work=root/'work';(work/'app/build/evidence179').mkdir(parents=True)
    phases=work/'app/build/evidence179/host-phases.jsonl';phases.write_text(''.join(json.dumps(dict(case=case,kind='end'))+'\n' for case in ('vpn_revoke','vpn_loss')))
    prior=Path.cwd()
    try:
     os.chdir(work)
     def command(args,**kwargs):return Mock(returncode=0 if outcome=='stopped' or 'ss' in args else 1,stdout=outcome+'\n' if 'ss' not in args else '')
     with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.9-evidence-locker','GITHUB_SHA':'b'*40,'RUNNER_TEMP':str(root)}),patch.object(d.subprocess,'run',side_effect=command) as run,patch.object(d.time,'sleep'):
      if outcome=='stopped':d.drain()
      else:
       with self.assertRaises(RuntimeError):d.drain()
     report=json.loads((work/'app/build/evidence179/vpn-controlled-drain.json').read_text());self.assertEqual(report['kind'],'controlled_fixture_cleanup_not_peer_close')
     commands=[call.args[0] for call in run.call_args_list]
     self.assertIn('evidence179_processes.py',commands[0][2])
     if outcome=='ownership-rejected':self.assertEqual(len(commands),1)
     else:self.assertEqual(commands[1],['sudo','ss','-K','state','all','src','203.0.113.10','dst','10.91.0.2'])
    finally:os.chdir(prior)
 def test_drain_cannot_run_inside_or_after_tor_window(self):
  from scripts import drain_evidence179_vpn as d
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);(root/'app/build/evidence179').mkdir(parents=True)
   (root/'app/build/evidence179/host-phases.jsonl').write_text(json.dumps(dict(case='tor_revoke',kind='start'))+'\n')
   prior=Path.cwd()
   try:
    os.chdir(root)
    with patch.dict(os.environ,{'GITHUB_ACTIONS':'true','GITHUB_REF':'refs/heads/release/1.7.9-evidence-locker'}),patch.object(d.subprocess,'run') as run:
     with self.assertRaises(RuntimeError):d.drain()
     run.assert_not_called()
   finally:os.chdir(prior)

if __name__=='__main__':unittest.main()
