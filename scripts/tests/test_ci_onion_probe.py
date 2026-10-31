import importlib.util
from pathlib import Path
import tempfile
import os
import unittest
from unittest.mock import patch,Mock
s=importlib.util.spec_from_file_location('probe',Path(__file__).resolve().parents[1]/'ci_onion_probe.py');p=importlib.util.module_from_spec(s);s.loader.exec_module(p)

class ReadinessTest(unittest.TestCase):
 def run_probe(self,reply):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory)
   with patch.object(p.processes179,'register'),patch.object(p.subprocess,'Popen',return_value=Mock(pid=123,poll=lambda:None)),patch.object(p.subprocess,'run',return_value=reply) as run,patch.object(p.time,'monotonic',side_effect=[0,1,301]),patch.object(p.time,'sleep'):
    p.verify_independent_retrieval(root,'fixture.onion','expected')
   args=run.call_args.args[0]
   self.assertIn('socks5h://127.0.0.1:', ' '.join(args));self.assertEqual('',args[args.index('--noproxy')+1])
 def test_requires_exact_body_through_remote_dns_socks(self):
  self.run_probe(Mock(returncode=0,stdout=b'expected'))
 def test_rejects_wrong_body_even_with_http_success(self):
  with self.assertRaises(RuntimeError):self.run_probe(Mock(returncode=0,stdout=b'wrong'))
 def test_rejects_curl_transport_failure(self):
  with self.assertRaises(RuntimeError):self.run_probe(Mock(returncode=7,stdout=b'expected'))
 def fixture(self,root):
  (root/'service').mkdir();(root/'service/hostname').write_text('fixture.onion')
  (root/'response').write_text('expected');(root/'client.port').write_text('12345')
  for name in ('client.pid','tor.pid','http.pid'):(root/name).write_text(str(os.getpid()))
 def test_fresh_health_requires_all_owned_processes_and_exact_retrieval(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);self.fixture(root)
   def retrieved(*args):args[-1].write_text('exact bytes PASS\n')
   with patch.object(p.Path,'exists',return_value=True),patch.object(p.Path,'read_bytes',return_value=str(root).encode()),patch.object(p,'retrieve_exact',side_effect=retrieved) as retrieve:
    p.verify_fixture(root,'full')
   self.assertEqual('fixture.onion',retrieve.call_args.args[1]);self.assertEqual('expected',retrieve.call_args.args[2])
   self.assertIn('phase=full', (root/'fixture-health.txt').read_text())
 def test_fresh_health_rejects_unowned_process_without_network_request(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);self.fixture(root)
   with patch.object(p.Path,'read_bytes',return_value=b'unrelated process'),patch.object(p,'retrieve_exact') as retrieve:
    with self.assertRaises(RuntimeError):p.verify_fixture(root,'preflight')
    retrieve.assert_not_called()
   self.assertFalse((root/'fixture-health.txt').exists())
 def test_fresh_health_cannot_claim_success_on_failed_exact_retrieval(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);self.fixture(root)
   with patch.object(p.Path,'exists',return_value=True),patch.object(p.Path,'read_bytes',return_value=str(root).encode()),patch.object(p,'retrieve_exact',side_effect=RuntimeError('not ready')):
    with self.assertRaises(RuntimeError):p.verify_fixture(root,'full')
   self.assertFalse((root/'fixture-health.txt').exists())
 def test_diagnostic_phases_still_require_owned_processes_and_exact_retrieval(self):
  for phase in ('diagnostic-before','diagnostic-after'):
   with self.subTest(phase=phase),tempfile.TemporaryDirectory() as directory:
    root=Path(directory);self.fixture(root)
    def retrieved(*args):args[-1].write_text('exact bytes PASS\n')
    with patch.object(p.Path,'exists',return_value=True),patch.object(p.Path,'read_bytes',return_value=str(root).encode()),patch.object(p,'retrieve_exact',side_effect=retrieved) as retrieve:
     p.verify_fixture(root,phase)
    retrieve.assert_called_once();self.assertIn('phase='+phase,(root/'fixture-health.txt').read_text())

class Evidence179TlsReadinessTest(unittest.TestCase):
 def root(self,directory):
  root=Path(directory);(root/'client.port').write_text('12345');(root/'evidence179-ready').write_bytes(b'exact-ready')
  (root/'fixture-ca.crt').write_bytes(b'ca');(root/'fixture.crt').write_bytes(b'leaf');return root
 def test_https_readiness_uses_remote_dns_ca_and_exact_hostname(self):
  with tempfile.TemporaryDirectory() as directory:
   root=self.root(directory);host='a'*56+'.onion'
   with patch.object(p.subprocess,'run',return_value=Mock(returncode=0,stdout=b'exact-ready')) as run:
    p.retrieve_tls(root,host)
   command=run.call_args.args[0]
   self.assertEqual(command[-1],f'https://{host}/evidence179/ready');self.assertNotIn('-k',command)
   self.assertEqual(command[command.index('--cacert')+1],str(root/'fixture-ca.crt'))
   self.assertEqual(command[command.index('--proxy')+1],'socks5h://127.0.0.1:12345')
   import json
   report=json.loads((root/'evidence179-tls-readiness.json').read_text());self.assertTrue(report['tls_verified']);self.assertEqual(report['hostname'],host)
 def test_http_body_or_failed_tls_cannot_publish_readiness(self):
  for result in (Mock(returncode=0,stdout=b'http-only-ready'),Mock(returncode=60,stdout=b'exact-ready')):
   with tempfile.TemporaryDirectory() as directory:
    root=self.root(directory)
    with patch.object(p.subprocess,'run',return_value=result),patch.object(p.time,'monotonic',side_effect=[0,1,301]),patch.object(p.time,'sleep'):
     with self.assertRaises(RuntimeError):p.retrieve_tls(root,'a'*56+'.onion')
    self.assertFalse((root/'evidence179-tls-readiness.json').exists())
 def test_invalid_onion_cannot_start_probe(self):
  with tempfile.TemporaryDirectory() as directory:
   root=self.root(directory)
   with patch.object(p.subprocess,'run') as run:
    with self.assertRaises(RuntimeError):p.retrieve_tls(root,'fixture.onion')
    run.assert_not_called()
 def test_fresh_tls_mode_requires_owned_https_process(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory);ReadinessTest().fixture(root);(root/'evidence179-enabled').touch();(root/'https.pid').write_text('99999999')
   original=p.Path.read_bytes
   def command(path):return b'unrelated process' if str(path)=='/proc/99999999/cmdline' else str(root).encode()
   with patch.object(p.Path,'exists',return_value=True),patch.object(p.Path,'read_bytes',command),patch.object(p,'retrieve_exact') as get,patch.object(p,'retrieve_tls') as tls:
    with self.assertRaises(RuntimeError):p.verify_fixture(root,'preflight')
    get.assert_not_called();tls.assert_not_called()

class OwnedCleanupTest(unittest.TestCase):
 def test_reused_or_unverified_pid_never_falls_back_to_os_kill(self):
  for has_identity in (False,True):
   with self.subTest(has_identity=has_identity),tempfile.TemporaryDirectory() as directory:
    root=Path(directory)/'fixture';root.mkdir();(root/'http.pid').write_text('123')
    if has_identity:(root/'http.identity.json').write_text('{}')
    previous=Path.cwd()
    try:
     os.chdir(directory)
     with patch.object(p.processes179,'stop_file',return_value='ownership-rejected') as stop,patch.object(p.os,'kill') as kill:
      with self.assertRaises(RuntimeError):p.stop(root)
      kill.assert_not_called();self.assertEqual(stop.call_count,1 if has_identity else 0)
     self.assertTrue(root.exists())
    finally:os.chdir(previous)
 def test_verified_cleanup_archives_only_public_files_and_uses_identity_stops(self):
  with tempfile.TemporaryDirectory() as directory:
   root=Path(directory)/'fixture';root.mkdir()
   for name in ('http','https','tor','client'):(root/(name+'.identity.json')).write_text('{}')
   (root/'fixture.crt').write_text('public');(root/'fixture.key').write_text('never archive private fixture key')
   previous=Path.cwd()
   try:
    os.chdir(directory)
    with patch.object(p.processes179,'stop_file',return_value='stopped') as stop,patch.object(p.os,'kill') as kill:
     p.stop(root);self.assertEqual(stop.call_count,4);kill.assert_not_called()
    self.assertFalse(root.exists());artifact=Path('app/build/acceptance-diagnostics/onion-fixture')
    self.assertEqual((artifact/'fixture.crt').read_text(),'public');self.assertFalse(list(artifact.rglob('*.key')))
   finally:os.chdir(previous)

if __name__=='__main__':unittest.main()
