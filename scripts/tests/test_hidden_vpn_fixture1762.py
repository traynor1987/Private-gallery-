import importlib.util, socket, struct, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('fixture1762',Path('scripts/hidden_vpn_fixture1762.py'))
fixture=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture)

def query(name,kind=1):
 return b'\x12\x34'+struct.pack('!HHHHH',0x100,1,0,0,0)+b''.join(bytes([len(x)])+x.encode() for x in name.split('.'))+b'\0'+struct.pack('!HH',kind,1)

class HiddenVpnFixtureTest(unittest.TestCase):
 def test_ipv4_fixture_answer_and_transaction_match(self):
  request=query('fixture.invalid');reply=fixture.fixture_answer(request)
  self.assertEqual(b'\x12\x34',reply[:2]);self.assertEqual(1,struct.unpack_from('!H',reply,6)[0]);self.assertEqual(socket.inet_aton('203.0.113.10'),reply[-4:])
 def test_no_ipv6_address_for_ipv4_only_tunnel(self):
  reply=fixture.fixture_answer(query('fixture.invalid',28));self.assertEqual(0,struct.unpack_from('!H',reply,6)[0])
 def test_android_edns_question_is_supported_without_returning_opt_as_answer(self):
  request=bytearray(query('fixture.invalid'));struct.pack_into('!H',request,10,1)
  request+=b'\0'+struct.pack('!HHIH',41,1232,0,0)
  reply=fixture.fixture_answer(bytes(request))
  self.assertEqual(1,struct.unpack_from('!H',reply,6)[0]);self.assertEqual(0,struct.unpack_from('!H',reply,10)[0])
 def test_non_fixture_dns_is_forwarded_not_fabricated(self):
  self.assertIsNone(fixture.fixture_answer(query('example.com')))
 def test_malformed_questions_are_rejected(self):
  for request in (b'',b'\x00'*17,query('fixture.invalid')+b'extra'):
   with self.assertRaises((ValueError,IndexError)):fixture.question(request)

if __name__=='__main__':unittest.main()
