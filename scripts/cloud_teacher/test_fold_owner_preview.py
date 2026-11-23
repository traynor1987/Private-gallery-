from concurrent.futures import ThreadPoolExecutor
from http.client import HTTPConnection
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Lock, Thread
from time import time
from unittest import TestCase
from unittest.mock import patch
from uuid import uuid4
import io
import json

from fold_owner_preview import CEILING, FIXTURES, MODEL, PreviewServer


class FoldOwnerPreviewTest(TestCase):
    def setUp(self):
        directory = self.enterContext(TemporaryDirectory())
        self.root = Path(directory)
        self.token = 'a' * 64
        (self.root / 'owner-token').write_text(self.token)
        self.calls = []
        self.call_lock = Lock()
        self.failure = None
        provider = self.enterContext(patch('server.build_opener'))
        provider.return_value.open.side_effect = self.simulate_provider
        self.start_server()
        self.addCleanup(self.stop_server)

    def start_server(self):
        self.server = PreviewServer(('127.0.0.1', 0), self.root, self.root / 'budget.db', int(time()) + 120)
        self.thread = Thread(target=self.server.serve_forever)
        self.thread.start()

    def stop_server(self):
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()

    def simulate_provider(self, request, **_):
        self.assertEqual(request.full_url, 'https://api.openai.com/v1/responses')
        data = json.loads(request.data)
        self.assertEqual(data['model'], MODEL)
        self.assertEqual(data['service_tier'], 'default')
        self.assertEqual(data['tools'], [])
        self.assertFalse(data['store'])
        self.assertLessEqual(data['max_output_tokens'], 1000)
        fixture = data['input']
        self.assertIn(fixture, FIXTURES.values())
        with self.call_lock:
            self.calls.append(fixture)
        if self.failure:
            raise self.failure
        reply = ('A short poetic form.' if 'text' not in data else json.dumps({
            'assessment': 'UNCERTAIN', 'patterns': ['EMOTIONAL_PRESSURE'],
            'uncertainty': 'HIGH', 'summary': 'Context could affect the reading.',
        }))
        return io.BytesIO(json.dumps({
            'status': 'completed', 'model': MODEL, 'service_tier': 'default',
            'usage': {'input_tokens': 100, 'output_tokens': 50, 'total_tokens': 150,
                      'input_tokens_details': {'cached_tokens': 0}, 'output_tokens_details': {'reasoning_tokens': 0}},
            'output': [{'type': 'message', 'content': [{'type': 'output_text', 'text': reply}]}],
        }).encode())

    def request(self, route, payload=None, *, authenticated=True, phone=False):
        headers = {'X-Nova-Fold-Client': '1' if phone else '0'}
        if authenticated:
            headers['Authorization'] = 'Bearer ' + self.token
        connection = HTTPConnection('127.0.0.1', self.server.server_port, timeout=4)
        try:
            connection.request('GET' if payload is None else 'POST', route,
                               body=None if payload is None else json.dumps(payload), headers=headers)
            response = connection.getresponse()
            return response.status, json.loads(response.read())
        finally:
            connection.close()

    def submit(self, route, **fields):
        return self.request(route, {'message': FIXTURES[route], 'consent': True,
                                    'request_id': str(uuid4()), 'protocol_version': 2, **fields})

    def arm(self):
        for route in ('/assistant/ask', '/guardian/status'):
            self.assertEqual(self.request(route, phone=True)[0], 200)
        (self.root / 'openai-key').write_text('sk-synthetic-preview-test-only')
        (self.root / 'approved').touch()

    def test_paid_requires_server_arming_and_both_authenticated_phone_checks(self):
        self.assertEqual(self.request('/assistant/ask', authenticated=False, phone=True)[0], 401)
        (self.root / 'openai-key').write_text('sk-synthetic-preview-test-only')
        (self.root / 'approved').touch()
        self.assertEqual(self.request('/assistant/ask')[0], 200)
        self.assertEqual(self.request('/guardian/status', phone=True)[0], 200)
        self.assertEqual(self.submit('/assistant/ask')[0], 503)
        self.assertFalse(self.calls)
        self.assertEqual(self.request('/assistant/ask', phone=True)[0], 200)
        status = self.request('/guardian/status')[1]
        self.assertTrue(status['analysis_enabled'])
        self.assertTrue(status['fold_owner_preview']['phone_checked'])
        self.assertFalse(status['cloud_teacher']['automatic_analysis_enabled'])
        self.assertEqual(status['budget']['configured_ceiling_micro_usd'], CEILING)
        self.assertEqual(status['budget']['approved_requests_remaining'], 2)

    def test_exact_fixtures_manual_approval_and_lifetime_cap_under_concurrency(self):
        self.arm()
        for route in FIXTURES:
            self.assertEqual(self.submit(route, message='A different text.')[0], 400)
            self.assertEqual(self.submit(route, consent=False)[0], 400)
            self.assertEqual(self.submit(route, mode='automatic')[0], 400)
        self.assertFalse(self.calls)
        with ThreadPoolExecutor(max_workers=6) as workers:
            results = list(workers.map(self.submit, list(FIXTURES) * 3))
        self.assertEqual(sum(code == 200 for code, _ in results), 2)
        self.assertCountEqual(self.calls, FIXTURES.values())
        status = self.request('/assistant/ask')[1]['budget']
        self.assertEqual(status['approved_requests_remaining'], 0)
        self.assertLessEqual(status['request_reservation_micro_usd'] * len(self.calls), CEILING)

    def test_restart_and_timeout_cannot_open_another_ask_slot(self):
        self.arm()
        self.failure = TimeoutError()
        self.assertEqual(self.submit('/assistant/ask')[0], 504)
        self.stop_server()
        self.start_server()
        self.assertEqual(self.submit('/guardian/analyze')[0], 503)
        self.arm()
        self.failure = None
        self.assertEqual(self.submit('/assistant/ask')[0], 409)
        code, result = self.submit('/guardian/analyze')
        self.assertEqual(code, 200)
        self.assertEqual(result['lesson']['uncertainty'], 'HIGH')
        self.assertCountEqual(self.calls, FIXTURES.values())

    def test_expired_preview_disarms_provider_dispatch(self):
        self.arm()
        self.server.expires_at = int(time()) - 1
        self.assertEqual(self.submit('/assistant/ask')[0], 503)
        self.assertFalse(self.calls)
