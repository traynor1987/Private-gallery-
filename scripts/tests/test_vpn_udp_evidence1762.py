"""The evidence verifier must not treat an empty STUN set as route proof."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class UdpEvidenceTest(unittest.TestCase):
    def verify(self, stun):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            http = lambda path, flag: dict(kind='http', source='10.91.0.2', method='POST', path=path, tls=True, **{flag:True})
            events = [dict(kind='http',source='10.91.0.2',method='GET',path='/media.png'),dict(kind='http',source='10.91.0.2',method='GET',path='/network-loss'),dict(kind='http',source='10.91.0.2',method='GET',path='/basic',basic_present=True,tls=True),http('/form-result','form_body'),http('/post','javascript_body'), http('/form-result','form_body'), http('/uploaded','multipart_png'), http('/worker-post','worker_body'),
                      dict(kind='http',source='10.91.0.2',continuous_started=True,time=1),dict(kind='http',source='10.91.0.2',revocation_barrier=True,time=3),dict(kind='websocket',tls=True,exact_body=True), dict(kind='http',source='10.91.0.2',probe_started=True), dict(kind='http',source='10.91.0.2',probe_complete=True),
                      dict(kind='slow-start'),dict(kind='slow-closed'),dict(kind='dns',name='native-phase.fixture.invalid',qtype=1,source='10.91.0.2')] + stun
            (root/'events.jsonl').write_text('\n'.join(map(json.dumps,events)))
            (root/'packets.txt').write_text('2026-10-28 12:00:00 pg1762 fixture.invalid. 203.0.113.10\n')
            (root/'traffic.pcap').write_bytes(b'x'*25)
            (root/'vpn-network-loss1762-result.json').write_text(json.dumps(dict(network_lost=True,child_dead=True,old_capability_denied=True,dead_network_failed=True,credentials_retained=True,owned_roots_removed=True)))
            (root/'vpn-native-revocation1762-result.json').write_text(json.dumps(dict(wireguard_retained=True,owned_roots_removed=True,post_barrier_stun=0,death_observed_ms=200,lock_return_ms=100,revocation_barrier_seconds=3)))
            return subprocess.run([sys.executable,str(Path(__file__).resolve().parents[1]/'verify_hidden_vpn_fixture1762.py'),str(root)],capture_output=True,text=True)

    def test_empty_stun_is_missing_evidence(self):
        result=self.verify([])
        self.assertNotEqual(result.returncode,0)
        self.assertIn('No STUN traffic observed',result.stderr)

    def test_direct_stun_is_rejected(self):
        result=self.verify([dict(kind='stun',source='192.0.2.7')])
        self.assertNotEqual(result.returncode,0)
        self.assertIn('STUN bypassed',result.stderr)

    def test_actual_peer_stun_satisfies_observation(self):
        result=self.verify([dict(kind='stun',source='10.91.0.2',time=at) for at in (2,2.1,2.2)])
        self.assertEqual(result.returncode,0,result.stderr)

    def test_stun_after_confirmed_death_barrier_is_rejected(self):
        result=self.verify([dict(kind='stun',source='10.91.0.2',time=at) for at in (2,2.1,2.2,3.5)])
        self.assertNotEqual(result.returncode,0)
        self.assertIn('continued after',result.stderr)
