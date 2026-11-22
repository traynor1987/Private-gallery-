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

    def test_production_gate_binds_timely_peer_close_to_each_trigger(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            result, events = self.fixture(root)
            events = [event for event in events if event.get('kind') is None]
            result.update(case='loss-test', trigger_at_ms=1_000, trigger_elapsed_ns=5_000_000_000)
            lock = dict(case='lock-test', trigger_at_ms=10_000, trigger_elapsed_ns=30_000_000_000, session_locked=True, browser_retired=True,
                        tor_retired=True, sink_released=True, old_socket_denied=True)
            (root/'native-tor1762-result.json').write_text(json.dumps(result))
            (root/'native-tor1762-lock.json').write_text(json.dumps(lock))
            for phase, device_time in (('before', 1_000_000_000), ('after', 60_000_000_000)):
                (root / f'tor-clock-{phase}.json').write_text(json.dumps(dict(
                    android=device_time, before=device_time + 1_010_000_000, after=device_time + 1_020_000_000)))
            for marker in (result, lock):
                events += [
                    dict(kind='slow-start', case=marker['case'], at_ms=marker['trigger_at_ms'] - 500, monotonic_ns=marker['trigger_elapsed_ns'] + 500_000_000),
                    dict(kind='slow-closed', case=marker['case'], at_ms=marker['trigger_at_ms'] + 500, monotonic_ns=marker['trigger_elapsed_ns'] + 1_500_000_000,
                         close_evidence='peer_eof'),
                ]

            def verify():
                (root/'interactive-events.jsonl').write_text('\n'.join(map(json.dumps, events)))
                return v.verify(root, root, require_production_lock=True, clock_directory=root)

            self.assertIn('Hidden session lock closed upstream in 500 ms', verify())
            # A wall-clock jump must have no bearing on the measured deadline.
            events[-1]['at_ms'] += 10_000_000
            self.assertIn('Hidden session lock closed upstream in 500 ms', verify())
            events[-1]['monotonic_ns'] = lock['trigger_elapsed_ns'] + 999_999_999
            with self.assertRaisesRegex(AssertionError, 'no peer closure'):
                verify()
            events[-1]['monotonic_ns'] = lock['trigger_elapsed_ns'] + 21_000_000_001
            with self.assertRaisesRegex(AssertionError, 'no peer closure'):
                verify()
            events[-1]['case'] = 'lock-unrelated'
            with self.assertRaisesRegex(AssertionError, 'no peer closure'):
                verify()

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
