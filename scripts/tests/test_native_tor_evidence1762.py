import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('native_tor_verify1762',Path(__file__).resolve().parents[1]/'verify_native_tor1762.py')
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)


class NativeTorEvidenceTest(unittest.TestCase):
    def fixture(self,root):
        result=dict(mode='TOR_ONLY',javascript=False,form=True,multipart=True,download_verified=True,discard_v4=True,discard_v6=True,discard_dns_udp=True,session_revoked=True,tor_process_death=True)
        events=[dict(method='POST',path='/form-result',form_ok=True),dict(method='POST',path='/uploaded',multipart_png=True),dict(method='GET',path='/script-probe'),dict(method='GET',path='/media.png'),dict(method='GET',path='/cookie-echo',cookie_present=True),dict(kind='slow-start'),dict(kind='slow-closed')]
        (root/'native-tor1762-result.json').write_text(json.dumps(result))
        (root/'interactive-events.jsonl').write_text('\n'.join(map(json.dumps,events)))
        (root/'packets.txt').write_text('Controlled capture\n')
        (root/'traffic.pcap').write_bytes(b'x'*25)
        return result,events

    def test_exact_observations_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);self.fixture(root)
            self.assertTrue(v.verify(root,root).startswith('PASS:'))
            (root/'packets.txt').write_text('private-gallery-tor-sink-only')
            with self.assertRaisesRegex(AssertionError,'UDP marker'):v.verify(root,root)

    def test_script_execution_or_missing_post_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);_,events=self.fixture(root)
            events.append(dict(method='GET',path='/script-must-not-run'))
            (root/'interactive-events.jsonl').write_text('\n'.join(map(json.dumps,events)))
            with self.assertRaisesRegex(AssertionError,'JavaScript'):v.verify(root,root)
            events=[event for event in events if event.get('path') not in ('/uploaded','/script-must-not-run')]
            (root/'interactive-events.jsonl').write_text('\n'.join(map(json.dumps,events)))
            with self.assertRaisesRegex(AssertionError,'actual Tor body'):v.verify(root,root)

    def test_absent_sink_attempt_or_dns_leak_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);result,_=self.fixture(root)
            result['discard_v6']=False;(root/'native-tor1762-result.json').write_text(json.dumps(result))
            with self.assertRaisesRegex(AssertionError,'discard_v6'):v.verify(root,root)
            self.fixture(root);(root/'packets.txt').write_text('A? tor.invalid.')
            with self.assertRaisesRegex(AssertionError,'DNS probe'):v.verify(root,root)
