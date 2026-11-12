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

    def test_activity_rolls_over_without_resetting_the_funded_ceiling(self):
        from datetime import UTC, datetime

        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            now = datetime(2026, 1, 31, 23, 59, tzinfo=UTC).timestamp()
            budget = Budget(path, "funded", now + 86400, 10, 3, clock=lambda: now)
            self.assertTrue(budget.reserve())
            self.assertTrue(budget.reserve())
            self.assertEqual(budget.status()["reservation_activity_utc"]["monthly_requests"], 2)
            now += 60
            self.assertEqual(budget.status()["reservation_activity_utc"]["daily_requests"], 0)
            self.assertTrue(budget.reserve())
            self.assertFalse(budget.reserve())
            status = budget.status()
            self.assertEqual(status["remaining_micro_usd"], 1)
            self.assertEqual(status["reservation_activity_utc"], {
                "day": "2026-02-01", "month": "2026-02",
                "daily_reserved_micro_usd": 3, "daily_requests": 1,
                "monthly_reserved_micro_usd": 3, "monthly_requests": 1,
            })

    def test_existing_cycle_total_is_preserved_without_inventing_activity(self):
        import sqlite3

        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            with sqlite3.connect(path) as db:
                db.execute("CREATE TABLE reservations (cycle TEXT PRIMARY KEY, total INTEGER NOT NULL)")
                db.execute("INSERT INTO reservations VALUES ('funded', 8)")
            budget = Budget(path, "funded", 100, 10, 3, clock=lambda: 99)
            self.assertFalse(budget.reserve())
            status = budget.status()
            self.assertEqual(status["reserved_upper_bound_micro_usd"], 8)
            self.assertEqual(status["reservation_activity_utc"]["monthly_requests"], 0)
