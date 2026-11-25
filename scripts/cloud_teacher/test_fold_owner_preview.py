import hmac
import json
from hashlib import sha256
from http.client import HTTPConnection
from pathlib import Path
from tempfile import TemporaryDirectory
from threading import Thread
from time import time
from unittest import TestCase
from unittest.mock import patch
from uuid import uuid4

from budget import Budget
from fold_owner_preview import APPROVAL, CEILING, FIXTURES, MODEL, PreviewServer


class FoldOwnerPreviewTest(TestCase):
    def setUp(self):
        self.root = Path(self.enterContext(TemporaryDirectory()))
        self.token = 'a' * 64
        (self.root / 'owner-token').write_text(self.token)
        self.ledger = self.root / 'budget.db'
        self.start_server()
        self.addCleanup(self.stop_server)

    def start_server(self):
        self.server = PreviewServer(('127.0.0.1', 0), self.root, self.ledger, int(time()) + 120)
        self.thread = Thread(target=self.server.serve_forever)
        self.thread.start()

    def stop_server(self):
        self.server.shutdown()
        self.thread.join()
        self.server.server_close()

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

    def test_status_routes_require_auth_and_report_the_same_disarmed_shared_ledger(self):
        for route in ('/assistant/ask', '/guardian/status'):
            self.assertEqual(self.request(route, authenticated=False, phone=True)[0], 401)
            self.assertFalse(self.request(route)[1]['fold_owner_preview']['phone_checked'])

        ask = self.request('/assistant/ask', phone=True)[1]
        self.assertFalse(ask['fold_owner_preview']['phone_checked'])
        guardian = self.request('/guardian/status', phone=True)[1]
        self.assertTrue(guardian['fold_owner_preview']['phone_checked'])
        self.assertTrue(self.request('/assistant/ask')[1]['fold_owner_preview']['phone_checked'])
        self.assertEqual(ask['budget'], guardian['budget'])
        for status in (ask, guardian):
            self.assertEqual(status['status'], 'authenticated')
            self.assertEqual(status['model'], MODEL)
            self.assertTrue(status['assistant_available'])
            self.assertFalse(status['analysis_enabled'])
            self.assertFalse(status['cloud_teacher']['automatic_analysis_enabled'])
            self.assertEqual(status['budget']['cycle'], APPROVAL)
            self.assertEqual(status['budget']['funded_allowance_micro_usd'], CEILING)
            self.assertEqual(status['budget']['configured_spending_limit_micro_usd'], CEILING)
            self.assertEqual(status['budget']['configured_ceiling_micro_usd'], CEILING)
            self.assertEqual(status['budget']['monthly_spending_limit_micro_usd'], CEILING)
            self.assertEqual(status['budget']['remaining_micro_usd'], CEILING)
            self.assertEqual(status['budget']['monthly_remaining_micro_usd'], CEILING)
            self.assertEqual(status['budget']['approved_request_limit'], 2)
            self.assertEqual(status['budget']['approved_requests_remaining'], 2)
            self.assertEqual(status['budget']['request_reservation_micro_usd'], 102_000)

    def test_both_approved_fixtures_stay_disarmed_even_with_legacy_arming_markers(self):
        for route in ('/assistant/ask', '/guardian/status'):
            self.assertEqual(self.request(route, phone=True)[0], 200)
        (self.root / 'approved').touch()
        fake_key = 'sk-synthetic-preview-test-only'
        (self.root / 'openai-key').write_text(fake_key)
        with patch.dict('os.environ', {'OPENAI_API_KEY': fake_key, 'NOVA_CLOUD_ANALYSIS_APPROVED': 'true'}), \
             patch('server.provider_reply') as provider:
            for route in FIXTURES:
                code, body = self.submit(route)
                self.assertEqual((code, body['error']), (503, 'analysis_disabled'))
            provider.assert_not_called()
        self.assertEqual(self.server.budget.status()['approved_requests_remaining'], 2)
        self.assertEqual(self.server.budget.status()['reserved_upper_bound_micro_usd'], 0)

    def test_posts_require_auth_valid_approval_shape_and_the_exact_fixture(self):
        for route in FIXTURES:
            self.assertEqual(self.request(route, {'consent': True}, authenticated=False)[0], 401)
            for fields in ({'message': 'Different text.'}, {'consent': False}, {'mode': 'automatic'},
                           {'request_id': 'invalid'}, {'protocol_version': '2'}, {'message': 'x' * 2050}):
                with self.subTest(route=route, fields=fields.keys()):
                    code, body = self.submit(route, **fields)
                    self.assertEqual((code, body['error']), (400, 'unapproved_preview_fixture_or_consent'))
        self.assertEqual(self.server.budget.status()['approved_requests_remaining'], 2)

    def test_ledger_keeps_one_slot_per_fixture_across_restarts(self):
        digest = hmac.new(self.token.encode(), b'assistant\x00What is a haiku?', sha256).hexdigest()
        budget = self.server.budget
        self.assertEqual(budget.begin_request(str(uuid4()), digest)['decision'], 'reserved')
        self.stop_server()
        self.start_server()

        self.assertEqual(self.server.budget.begin_request(str(uuid4()), digest)['decision'], 'duplicate')
        budget = self.request('/guardian/status')[1]['budget']
        self.assertEqual(budget['approved_requests_remaining'], 1)
        self.assertEqual(budget['reserved_upper_bound_micro_usd'], 102_000)
        self.assertEqual(self.submit('/guardian/analyze')[0], 503)

    def test_rejects_a_ledger_that_belongs_to_an_earlier_approval(self):
        historical_path = self.root / 'historical.db'
        Budget(historical_path, 'fold-owner-20261123', int(time()) + 120, CEILING, 102_000)
        before = historical_path.read_bytes()
        with self.assertRaisesRegex(ValueError, 'different approval or token'):
            PreviewServer(('127.0.0.1', 0), self.root, historical_path, int(time()) + 120)
        self.assertEqual(historical_path.read_bytes(), before)

    def test_rejects_new_token_even_when_the_existing_ledger_has_no_reservations(self):
        before = self.ledger.read_bytes()
        (self.root / 'owner-token').write_text('b' * 64)
        with self.assertRaisesRegex(ValueError, 'different approval or token'):
            PreviewServer(('127.0.0.1', 0), self.root, self.ledger, int(time()) + 120)
        self.assertEqual(self.ledger.read_bytes(), before)

    def test_expired_server_closes_health_status_and_post_routes(self):
        self.server.expires_at = int(time()) - 1
        for route in ('/health', '/assistant/ask', '/guardian/status'):
            code, body = self.request(route)
            self.assertEqual((code, body['error']), (503, 'preview_expired'))
        for route in FIXTURES:
            code, body = self.submit(route)
            self.assertEqual((code, body['error']), (503, 'preview_expired'))
