import hashlib
import hmac
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest import TestCase
from uuid import uuid4

from budget import Budget
from verify_fold_preview_ledger import APPROVAL, CEILING, INPUTS, RESERVATION, verify


class FoldPreviewLedgerVerificationTest(TestCase):
    def setUp(self):
        self.state = Path(self.enterContext(TemporaryDirectory()))
        self.token = 'a' * 64
        self.token_path = self.state / 'owner-token'
        self.token_path.write_text(self.token)
        self.ledger = self.state / 'ledger' / 'budget.db'

    def create_budget(self):
        self.ledger.parent.mkdir()
        return Budget(self.ledger, APPROVAL, 100, CEILING, RESERVATION,
                      clock=lambda: 99, limit=CEILING, monthly_limit=CEILING, request_limit=2)

    def reserve(self, budget, fixture):
        identity = str(uuid4())
        digest = hmac.new(self.token.encode(), INPUTS[fixture].encode(), hashlib.sha256).hexdigest()
        self.assertEqual(budget.begin_request(identity, digest)['decision'], 'reserved')
        return identity

    def test_missing_ledger_cannot_be_reported_as_unused(self):
        with self.assertRaisesRegex(ValueError, 'missing'):
            verify(self.state)
        self.assertFalse(self.ledger.exists())

    def test_read_only_report_counts_uncertain_and_settled_as_used(self):
        budget = self.create_budget()
        self.assertEqual(verify(self.state)['remaining_requests'], 2)
        ask = self.reserve(budget, 'ask_nova')
        budget.mark_uncertain(ask)
        self.assertEqual(verify(self.state)['remaining_requests'], 1)
        guardian = self.reserve(budget, 'guardian_second_opinion')
        budget.reconcile(guardian, 259)
        before = self.ledger.read_bytes()

        result = verify(self.state)

        self.assertEqual(result['fixtures'], {'ask_nova': 'uncertain', 'guardian_second_opinion': 'settled'})
        self.assertEqual(result['remaining_requests'], 0)
        self.assertEqual(result['reserved_upper_bound_micro_usd'], RESERVATION + 259)
        self.assertEqual(self.ledger.read_bytes(), before)
        self.token_path.write_text('b' * 64)
        with self.assertRaisesRegex(ValueError, 'unexpected preview reservation'):
            verify(self.state)
