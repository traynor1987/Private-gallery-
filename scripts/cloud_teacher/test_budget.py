from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from budget import Budget


class BudgetTest(unittest.TestCase):
    def test_concurrent_reservations_cannot_exceed_credit_and_persist(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            budget = Budget(path, "cycle", 100, 10, 3, clock=lambda: 99)
            with ThreadPoolExecutor(max_workers=8) as workers:
                self.assertEqual(sum(workers.map(lambda _: budget.reserve(), range(20))), 3)
            reopened = Budget(path, "cycle", 100, 10, 3, clock=lambda: 99)
            self.assertFalse(reopened.reserve())
            self.assertEqual(reopened.status()["remaining_micro_usd"], 1)
            self.assertEqual(reopened.status()["warning_percent"], 90)

    def test_expired_cycle_and_unfunded_cycle_refuse_requests(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            self.assertFalse(Budget(path, "expired", 100, 10, 1, clock=lambda: 100).reserve())
            self.assertFalse(Budget(path, "unfunded", 100, 0, 1, clock=lambda: 99).reserve())
            with self.assertRaises(ValueError):
                Budget(path, "invalid", 100, 10, 1, limit=90_000_001)
