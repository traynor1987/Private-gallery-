from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from budget import Budget


class BudgetTest(unittest.TestCase):
    def test_monthly_limit_is_atomic_across_independent_cycles(self):
        from datetime import UTC, datetime
        now = datetime(2026, 11, 18, tzinfo=UTC).timestamp()
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'budget.db'
            budgets = [Budget(path, str(i), now+86400, 90_000_000, 15_000_000, clock=lambda: now) for i in range(20)]
            with ThreadPoolExecutor(max_workers=10) as workers:
                self.assertEqual(sum(workers.map(lambda b: b.reserve(), budgets)), 6)
            self.assertEqual(budgets[0].status()['monthly_exposure_micro_usd'], 90_000_000)

    def test_duplicate_identity_cannot_reserve_again_and_payload_change_conflicts(self):
        from uuid import uuid4
        with TemporaryDirectory() as directory:
            path = Path(directory) / 'budget.db'
            budget = Budget(path, 'one', 100, 100, 10, clock=lambda: 99)
            identity = str(uuid4())
            self.assertEqual(budget.begin_request(identity, 'a'*64)['decision'], 'reserved')
            reopened = Budget(path, 'different-cycle', 100, 100, 10, clock=lambda: 99)
            self.assertEqual(reopened.begin_request(identity, 'a'*64)['decision'], 'duplicate')
            self.assertEqual(reopened.begin_request(identity, 'b'*64)['decision'], 'conflict')
            self.assertEqual(budget.status()['reserved_upper_bound_micro_usd'], 10)

    def test_concurrent_duplicate_request_has_one_reservation(self):
        from uuid import uuid4
        with TemporaryDirectory() as directory:
            budget = Budget(Path(directory)/'budget.db', 'one', 100, 100, 10, clock=lambda: 99)
            identity = str(uuid4())
            with ThreadPoolExecutor(max_workers=8) as workers:
                decisions = list(workers.map(lambda _: budget.begin_request(identity, 'a'*64)['decision'], range(20)))
            self.assertEqual(decisions.count('reserved'), 1)
            self.assertEqual(decisions.count('duplicate'), 19)

    def test_verified_usage_reconciles_once_without_claiming_provider_billing(self):
        from uuid import uuid4
        with TemporaryDirectory() as directory:
            budget = Budget(Path(directory)/'budget.db', 'one', 100, 100, 10, clock=lambda: 99)
            identity = str(uuid4())
            budget.begin_request(identity, 'a'*64)
            self.assertTrue(budget.reconcile(identity, 3))
            self.assertFalse(budget.reconcile(identity, 3))
            with self.assertRaises(ValueError):
                budget.reconcile(identity, 2)
            status = budget.status()
            self.assertEqual(status['remaining_micro_usd'], 97)
            self.assertEqual(status['usage_reported_cost_micro_usd'], 3)
            self.assertFalse(status['provider_billing_verified'])

    def test_uncertain_paid_outcome_retains_full_bound_across_months(self):
        from datetime import UTC, datetime
        from uuid import uuid4
        now = datetime(2026, 11, 30, 23, 59, tzinfo=UTC).timestamp()
        with TemporaryDirectory() as directory:
            path = Path(directory)/'budget.db'
            before = Budget(path, 'nov', now+86400, 90_000_000, 60_000_000, clock=lambda: now)
            identity = str(uuid4())
            before.begin_request(identity, 'a'*64)
            before.mark_uncertain(identity)
            now += 60
            after = Budget(path, 'dec', now+86400, 90_000_000, 60_000_000, clock=lambda: now)
            self.assertFalse(after.reserve())
            self.assertEqual(after.status()['unresolved_carry_micro_usd'], 60_000_000)
            self.assertEqual(after.status()['monthly_remaining_micro_usd'], 40_000_000)
            self.assertTrue(before.reconcile(identity, 10_000_000))
            self.assertTrue(after.reserve())
            self.assertEqual(after.status()['monthly_exposure_micro_usd'], 70_000_000)

    def test_month_rollover_renews_monthly_room_but_never_funded_credit(self):
        from datetime import UTC, datetime
        from uuid import uuid4
        now = datetime(2026, 11, 30, 23, 59, tzinfo=UTC).timestamp()
        with TemporaryDirectory() as directory:
            budget = Budget(Path(directory)/'budget.db', 'same-funded-cycle', now+86400, 90_000_000, 60_000_000, clock=lambda: now)
            identity = str(uuid4())
            budget.begin_request(identity, 'a'*64)
            budget.reconcile(identity, 60_000_000)
            now += 60
            self.assertEqual(budget.status()['monthly_remaining_micro_usd'], 100_000_000)
            self.assertFalse(budget.reserve())

    def test_cost_exceeding_verified_bound_halts_future_paid_requests_persistently(self):
        from uuid import uuid4
        with TemporaryDirectory() as directory:
            path = Path(directory)/'budget.db'
            budget = Budget(path, 'one', 100, 100, 10, clock=lambda: 99)
            identity = str(uuid4())
            budget.begin_request(identity, 'a'*64)
            with self.assertRaises(ValueError):
                budget.reconcile(identity, 11)
            reopened = Budget(path, 'different-cycle', 100, 100, 10, clock=lambda: 99)
            self.assertFalse(reopened.reserve())
            self.assertTrue(reopened.status()['paid_requests_halted'])

    def test_clock_rollback_fails_closed_without_starting_new_reservation(self):
        now = 99
        with TemporaryDirectory() as directory:
            budget = Budget(Path(directory)/'budget.db', 'one', 200, 100, 10, clock=lambda: now)
            self.assertTrue(budget.reserve())
            now -= 1
            self.assertFalse(budget.reserve())
            now += 1
            self.assertEqual(budget.status()['reserved_upper_bound_micro_usd'], 10)

    def test_legacy_dated_reservations_are_preserved_as_unreconciled_carry(self):
        import sqlite3
        from datetime import UTC, datetime
        now = datetime(2026, 12, 1, tzinfo=UTC).timestamp()
        with TemporaryDirectory() as directory:
            path = Path(directory)/'budget.db'
            with sqlite3.connect(path) as db:
                db.execute('CREATE TABLE reservations (cycle TEXT PRIMARY KEY, total INTEGER NOT NULL)')
                db.execute("INSERT INTO reservations VALUES ('old',60000000)")
                db.execute('CREATE TABLE daily_reservations (day TEXT PRIMARY KEY, total INTEGER NOT NULL, requests INTEGER NOT NULL)')
                db.execute("INSERT INTO daily_reservations VALUES ('2026-11-30',60000000,1)")
            budget = Budget(path, 'new', now+86400, 90_000_000, 60_000_000, clock=lambda: now)
            self.assertFalse(budget.reserve())
            self.assertEqual(budget.status()['unresolved_carry_micro_usd'], 60_000_000)
            reopened = Budget(path, 'new', now+86400, 90_000_000, 60_000_000, clock=lambda: now)
            self.assertEqual(reopened.status()['unresolved_carry_micro_usd'], 60_000_000)

    def test_legacy_undated_totals_cannot_be_forgotten_by_cycle_rename(self):
        import sqlite3
        with TemporaryDirectory() as directory:
            path = Path(directory)/'budget.db'
            with sqlite3.connect(path) as db:
                db.execute('CREATE TABLE reservations (cycle TEXT PRIMARY KEY, total INTEGER NOT NULL)')
                db.execute("INSERT INTO reservations VALUES ('old',90000000)")
            budget = Budget(path, 'new', 100, 90_000_000, 20_000_000, clock=lambda: 99)
            self.assertFalse(budget.reserve())
            self.assertEqual(budget.status()['legacy_unattributed_micro_usd'], 90_000_000)

    def test_invalid_amounts_and_request_identifiers_fail_without_billing(self):
        from uuid import uuid4
        with TemporaryDirectory() as directory:
            path = Path(directory)/'budget.db'
            for extra in ({'monthly_limit':100_000_001},{'monthly_limit':True}):
                with self.subTest(extra=extra), self.assertRaises(ValueError):
                    Budget(path, 'one', 100, 100, 10, **extra)
            budget = Budget(path, 'one', 100, 100, 10, clock=lambda: 99)
            for identity, digest in [('private content', 'a'*64), (str(uuid4()), 'private content')]:
                with self.assertRaises(ValueError):
                    budget.begin_request(identity, digest)
            self.assertEqual(budget.status()['reserved_upper_bound_micro_usd'], 0)

    def test_new_funded_cycle_cannot_reset_the_calendar_month_cap(self):
        from datetime import UTC, datetime
        now = datetime(2026, 11, 18, tzinfo=UTC).timestamp()
        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            first = Budget(path, "first", now + 86400, 90_000_000, 60_000_000, clock=lambda: now)
            second = Budget(path, "second", now + 86400, 90_000_000, 60_000_000, clock=lambda: now)
            self.assertTrue(first.reserve())
            self.assertFalse(second.reserve(), "A cycle rename must not authorize $120 in one calendar month")

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

    def test_status_distinguishes_funding_from_the_spending_limit(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "budget.db"
            status = Budget(path, "funded", 100, 37_000_000, 102_000, clock=lambda: 99).status()
            self.assertEqual(status["funded_allowance_micro_usd"], 37_000_000)
            self.assertEqual(status["configured_spending_limit_micro_usd"], 90_000_000)
            self.assertEqual(status["configured_ceiling_micro_usd"], 37_000_000)
