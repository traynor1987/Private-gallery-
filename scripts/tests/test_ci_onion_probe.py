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
   with patch.object(p.subprocess,'Popen',return_value=Mock(pid=123,poll=lambda:None)),patch.object(p.subprocess,'run',return_value=reply) as run,patch.object(p.time,'monotonic',side_effect=[0,1,301]),patch.object(p.time,'sleep'):
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
if __name__=='__main__':unittest.main()
