"""Host-only mDNS classification must never exempt Android origin routing."""
import ast
from pathlib import Path
import re
import unittest

SOURCE = Path(__file__).resolve().parents[1] / 'verify_hidden_vpn_fixture1762.py'
node = next(n for n in ast.parse(SOURCE.read_text()).body if isinstance(n, ast.FunctionDef) and n.name == 'host_fixture_adb_multicast')
namespace = {'re': re}
exec(compile(ast.Module(body=[node], type_ignores=[]), str(SOURCE), 'exec'), namespace)
classify = namespace['host_fixture_adb_multicast']


def packet(questions, count=3, size=69):
    return ('2026-10-30 09:21:56.607341 lo    In  IP (tos 0x0, ttl 1, id 54526, offset 0, flags [DF], proto UDP (17), length 97)\n'
            '    203.0.113.10.5353 > 224.0.0.251.5353: [bad udp cksum 0x1d65 -> 0x7ce4!] 0 ['+str(count)+'q] '+questions+' ('+str(size)+')\n')


class HostMdnsClassificationTest(unittest.TestCase):
    def test_exact_host_android_lookup(self):
        self.assertTrue(classify(packet('AAAA ANY (QM)? Android.local. A ANY (QM)? Android.local. AAAA ANY (QM)? Android.local.')))

    def test_exact_host_adb_emulator_lookup_question_order(self):
        parts = ['SRV ANY (QM)? adb-EMULATOR37X2X13X0-7QJ9fS._adb._tcp.local.',
                 'AAAA ANY (QM)? Android_D4U1E2V7.local.', 'AAAA ANY (QM)? Android_D4U1E2V7.local.',
                 'A ANY (QM)? Android_D4U1E2V7.local.']
        for questions in (parts, parts[::-1]):
            self.assertTrue(classify(packet(' '.join(questions), 4, 146)))

    def test_existing_exact_adb_service_browse_is_retained(self):
        q = 'ANY ANY (QM)? _adb-tls-pairing._tcp.local. ANY ANY (QM)? _adb._tcp.local. ANY ANY (QM)? _adb-tls-connect._tcp.local.'
        self.assertTrue(classify(packet(q, 3, 99)))
        self.assertFalse(classify(packet(q.replace('_adb._tcp.local.', '_http._tcp.local.'), 3, 99)))

    def test_origin_or_other_packets_never_exempted(self):
        valid = packet('AAAA ANY (QM)? Android.local. A ANY (QM)? Android.local. AAAA ANY (QM)? Android.local.')
        changes = [(' lo ', ' eth0 '), (' lo ', ' pg1762 '), ('ttl 1,', 'ttl 64,'),
                   ('proto UDP (17)', 'proto TCP (6)'), ('203.0.113.10.5353', '10.91.0.2.5353'),
                   ('203.0.113.10.5353', '203.0.113.10.443'), ('224.0.0.251.5353', '203.0.113.10.443'),
                   ('Android.local.', 'fixture.invalid.'), ('Android.local.', 'private.example.'),
                   ('[3q]', '[4q]'), ('(69)', '(70)'), ('A ANY (QM)?', 'TXT ANY (QM)?')]
        for old, new in changes:
            with self.subTest(change=(old, new)):
                self.assertFalse(classify(valid.replace(old, new)))
        for text in ('Android.local.evil.', 'Android_D4U1E2V7.private.', 'secret.local.'):
            self.assertFalse(classify(valid.replace('Android.local.', text)))

    def test_adb_name_and_same_hostname_are_required(self):
        q = 'SRV ANY (QM)? adb-EMULATOR37X2X13X0-7QJ9fS._adb._tcp.local. AAAA ANY (QM)? Android_D4U1E2V7.local. AAAA ANY (QM)? Android_D4U1E2V7.local. A ANY (QM)? Android_D4U1E2V7.local.'
        valid = packet(q, 4, 146)
        for old, new in [('_adb._tcp.local.', '_http._tcp.local.'), ('adb-EMULATOR', 'private-EMULATOR'),
                         ('Android_D4U1E2V7.local.', 'fixture.invalid.'), ('[4q]', '[3q]'), ('(146)', '(147)')]:
            self.assertFalse(classify(valid.replace(old, new)))
        self.assertFalse(classify(packet(q.replace(' A ANY (QM)? Android_D4U1E2V7.local.', ' A ANY (QM)? Android_12345678.local.'), 4, 146)))


if __name__ == '__main__':
    unittest.main()
