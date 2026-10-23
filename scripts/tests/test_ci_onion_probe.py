import importlib.util
from pathlib import Path
import tempfile
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
if __name__=='__main__':unittest.main()
