"""One persistent ledger: funded-cycle ceilings and conservative UTC monthly exposure.

Amounts are integer micro-USD. Provider token reports can release a reservation, but
are not independently verified billing. Unknown outcomes retain their full bound.
"""
import math
import re
import sqlite3
from datetime import UTC, datetime
from time import time
from uuid import UUID, uuid4

# Preserve the existing tighter funded-cycle policy; the monthly maximum is separate.
DEFAULT_LIMIT_MICRO_USD = 90_000_000
MONTHLY_MAX_MICRO_USD = 100_000_000


def _integer(value, minimum=0, maximum=MONTHLY_MAX_MICRO_USD):
    return type(value) is int and minimum <= value <= maximum


class Budget:
    def __init__(self, database, cycle, expires_at, credits, reserve,
                 limit=DEFAULT_LIMIT_MICRO_USD, clock=time, *, monthly_limit=MONTHLY_MAX_MICRO_USD,
                 request_limit=None):
        if (not isinstance(cycle, str) or not cycle or len(cycle) > 200 or
                not _integer(limit, 1, DEFAULT_LIMIT_MICRO_USD) or
                not _integer(credits, maximum=2**63-1) or not _integer(reserve, 1) or
                not _integer(monthly_limit, 1) or type(expires_at) not in (int, float) or
                not math.isfinite(expires_at) or
                (request_limit is not None and not _integer(request_limit,1,1_000_000))):
            raise ValueError("Invalid credit-cycle configuration")
        self.database, self.cycle, self.expires_at = database, cycle, expires_at
        self.funded_allowance, self.spending_limit = credits, limit
        self.ceiling, self.reserve_amount = min(credits, limit), reserve
        self.clock, self.monthly_limit = clock, monthly_limit
        self.request_limit = request_limit
        with sqlite3.connect(database) as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("CREATE TABLE IF NOT EXISTS reservations (cycle TEXT PRIMARY KEY, total INTEGER NOT NULL)")
            db.execute("""CREATE TABLE IF NOT EXISTS daily_reservations (
                day TEXT PRIMARY KEY, total INTEGER NOT NULL, requests INTEGER NOT NULL)""")
            db.execute("CREATE TABLE IF NOT EXISTS budget_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execute("CREATE TABLE IF NOT EXISTS legacy_reservation_dates (day TEXT PRIMARY KEY, total INTEGER NOT NULL)")
            db.execute("""CREATE TABLE IF NOT EXISTS request_reservations (
                id TEXT PRIMARY KEY, digest TEXT NOT NULL, cycle TEXT NOT NULL, day TEXT NOT NULL,
                bound INTEGER NOT NULL CHECK(bound>0), state TEXT NOT NULL,
                actual INTEGER, settled_day TEXT)""")
            self._validate_totals(db)
            # Old ledgers can lack dates. Never invent dates or forgive that exposure.
            if db.execute("SELECT 1 FROM budget_metadata WHERE key='legacy_unattributed'").fetchone() is None:
                cycles = db.execute("SELECT COALESCE(SUM(total),0) FROM reservations").fetchone()[0]
                dated = db.execute("SELECT COALESCE(SUM(total),0) FROM daily_reservations").fetchone()[0]
                db.execute("INSERT INTO budget_metadata VALUES ('legacy_unattributed',?)", (str(max(0, cycles-dated)),))
                db.execute("INSERT INTO legacy_reservation_dates SELECT day,total FROM daily_reservations")

    @staticmethod
    def _validate_totals(db):
        for table in ('reservations', 'daily_reservations'):
            if db.execute(f"SELECT 1 FROM {table} WHERE typeof(total)!='integer' OR total<0 LIMIT 1").fetchone():
                raise ValueError("Invalid budget ledger")
        if db.execute("SELECT 1 FROM daily_reservations WHERE typeof(requests)!='integer' OR requests<0 LIMIT 1").fetchone():
            raise ValueError("Invalid budget activity")

    def _period(self, db, *, update=False):
        now = self.clock()
        if type(now) not in (int, float) or not math.isfinite(now) or now < 0:
            raise ValueError("Invalid budget clock")
        row = db.execute("SELECT value FROM budget_metadata WHERE key='latest_request_time'").fetchone()
        if row is not None:
            previous = float(row[0])
            if not math.isfinite(previous) or previous < 0 or now < previous:
                raise ValueError("Budget clock moved backwards or is invalid")
        if update:
            db.execute("INSERT INTO budget_metadata VALUES ('latest_request_time',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", (str(now),))
        day = datetime.fromtimestamp(now, UTC).date().isoformat()
        return now, day, day[:7]

    def _exposure(self, db, month):
        dated = db.execute("SELECT COALESCE(SUM(total),0) FROM daily_reservations WHERE day BETWEEN ? AND ?", (month+'-01', month+'-31')).fetchone()[0]
        carry = db.execute("SELECT COALESCE(SUM(bound),0) FROM request_reservations WHERE state IN ('pending','uncertain') AND day<?", (month+'-01',)).fetchone()[0]
        # Legacy dated reservations also lack final usage/outcome receipts.
        carry += db.execute("SELECT COALESCE(SUM(total),0) FROM legacy_reservation_dates WHERE day<?", (month+'-01',)).fetchone()[0]
        legacy = int(db.execute("SELECT value FROM budget_metadata WHERE key='legacy_unattributed'").fetchone()[0])
        if min(dated, carry, legacy) < 0:
            raise ValueError("Invalid budget exposure")
        return dated + carry + legacy, carry, legacy

    def reserve(self):
        """Compatibility for existing callers; unreconciled reservations stay charged."""
        try:
            return self.begin_request(str(uuid4()), '0'*64)['decision'] == 'reserved'
        except ValueError:
            return False

    def begin_request(self, request_id, payload_digest):
        try:
            key = str(UUID(request_id))
        except (ValueError, TypeError, AttributeError):
            raise ValueError("Invalid request identity") from None
        if not isinstance(payload_digest, str) or not re.fullmatch('[a-f0-9]{64}', payload_digest):
            raise ValueError("Invalid request identity")
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN IMMEDIATE")
            self._validate_totals(db)
            now, day, month = self._period(db, update=True)
            existing = db.execute("SELECT digest,state FROM request_reservations WHERE id=?", (key,)).fetchone()
            if existing is not None:
                return {'decision': 'duplicate' if existing[0] == payload_digest else 'conflict', 'state': existing[1]}
            if db.execute("SELECT 1 FROM budget_metadata WHERE key='halted'").fetchone():
                return {'decision': 'halted'}
            if now >= self.expires_at:
                return {'decision': 'expired'}
            requests = db.execute('SELECT COUNT(*) FROM request_reservations WHERE cycle=?', (self.cycle,)).fetchone()[0]
            if self.request_limit is not None and requests >= self.request_limit:
                return {'decision': 'approved_request_limit_exhausted'}
            row = db.execute("SELECT total FROM reservations WHERE cycle=?", (self.cycle,)).fetchone()
            total = (row[0] if row else 0) + self.reserve_amount
            if total > self.ceiling:
                return {'decision': 'funded_allowance_exhausted'}
            exposure, _, _ = self._exposure(db, month)
            if exposure + self.reserve_amount > self.monthly_limit:
                return {'decision': 'monthly_limit_exhausted'}
            db.execute("INSERT INTO reservations VALUES (?,?) ON CONFLICT(cycle) DO UPDATE SET total=excluded.total", (self.cycle, total))
            db.execute("""INSERT INTO daily_reservations VALUES (?,?,1) ON CONFLICT(day)
                DO UPDATE SET total=total+excluded.total, requests=requests+1""", (day, self.reserve_amount))
            db.execute("INSERT INTO request_reservations VALUES (?,?,?,?,?,'pending',NULL,NULL)", (key, payload_digest, self.cycle, day, self.reserve_amount))
            return {'decision': 'reserved', 'request_id': key, 'reserved_micro_usd': self.reserve_amount}

    def mark_uncertain(self, request_id):
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("UPDATE request_reservations SET state='uncertain' WHERE id=? AND state='pending'", (str(UUID(request_id)),))

    def halt_unverified_pricing(self):
        with sqlite3.connect(self.database) as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute("INSERT OR REPLACE INTO budget_metadata VALUES ('halted','pricing_unverified')")

    def reconcile(self, request_id, usage_cost_micro_usd):
        """Server-only verified usage calculation; never an Android-supplied cost.

        When a request crosses months, retain its actual cost in both dates to avoid
        claiming knowledge of the provider's billing-month attribution.
        """
        if not _integer(usage_cost_micro_usd):
            raise ValueError("Invalid usage cost")
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN IMMEDIATE")
            self._validate_totals(db)
            _, day, month = self._period(db, update=True)
            key = str(UUID(request_id))
            row = db.execute("SELECT cycle,day,bound,state,actual FROM request_reservations WHERE id=?", (key,)).fetchone()
            if row is None:
                raise ValueError("Unknown reservation")
            cycle, original_day, bound, state, actual = row
            if state == 'settled':
                if actual != usage_cost_micro_usd:
                    raise ValueError("Usage was already reconciled differently")
                return False
            if usage_cost_micro_usd > bound:
                db.execute("INSERT OR REPLACE INTO budget_metadata VALUES ('halted','cost_bound_exceeded')")
                db.commit()
                raise ValueError("Usage exceeds the verified cost bound; paid requests halted")
            refund = bound - usage_cost_micro_usd
            db.execute("UPDATE reservations SET total=total-? WHERE cycle=?", (refund, cycle))
            db.execute("UPDATE daily_reservations SET total=total-? WHERE day=?", (refund, original_day))
            if original_day[:7] != month:
                db.execute("""INSERT INTO daily_reservations VALUES (?,?,0) ON CONFLICT(day)
                    DO UPDATE SET total=total+excluded.total""", (day, usage_cost_micro_usd))
            db.execute("UPDATE request_reservations SET state='settled',actual=?,settled_day=? WHERE id=?", (usage_cost_micro_usd, day, key))
            self._validate_totals(db)
            return True

    def status(self):
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN")
            self._validate_totals(db)
            now, day, month = self._period(db)
            row = db.execute("SELECT total FROM reservations WHERE cycle=?", (self.cycle,)).fetchone()
            daily = db.execute("SELECT total,requests FROM daily_reservations WHERE day=?", (day,)).fetchone()
            monthly = db.execute("SELECT COALESCE(SUM(total),0),COALESCE(SUM(requests),0) FROM daily_reservations WHERE day BETWEEN ? AND ?", (month+'-01', day)).fetchone()
            exposure, carry, legacy = self._exposure(db, month)
            measured = db.execute("SELECT COALESCE(SUM(actual),0) FROM request_reservations WHERE state='settled' AND (day BETWEEN ? AND ? OR settled_day BETWEEN ? AND ?)", (month+'-01',month+'-31',month+'-01',month+'-31')).fetchone()[0]
            halted = db.execute("SELECT 1 FROM budget_metadata WHERE key='halted'").fetchone() is not None
            request_count = db.execute('SELECT COUNT(*) FROM request_reservations WHERE cycle=?', (self.cycle,)).fetchone()[0]
        total, expired = row[0] if row else 0, now >= self.expires_at
        percent = max(min(100,100*total//self.ceiling) if self.ceiling else 100,
                      min(100,100*exposure//self.monthly_limit))
        return {'cycle':self.cycle, 'expires_at':self.expires_at, 'expired':expired,
                'funded_allowance_micro_usd':self.funded_allowance,
                'configured_spending_limit_micro_usd':self.spending_limit,
                'configured_ceiling_micro_usd':self.ceiling, 'reserved_upper_bound_micro_usd':total,
                'remaining_micro_usd':0 if expired or halted else max(0,min(self.ceiling-total,self.monthly_limit-exposure)),
                'request_reservation_micro_usd':self.reserve_amount,
                'monthly_spending_limit_micro_usd':self.monthly_limit,
                'monthly_exposure_micro_usd':exposure,
                'monthly_remaining_micro_usd':0 if halted else max(0,self.monthly_limit-exposure),
                'usage_reported_cost_micro_usd':measured,
                'provider_billing_verified':False,
                'unresolved_carry_micro_usd':carry,
                'legacy_unattributed_micro_usd':legacy,
                'paid_requests_halted':halted,
                'approved_request_limit':self.request_limit,
                'approved_requests_remaining':None if self.request_limit is None else max(0,self.request_limit-request_count),
                'reservation_activity_utc':{'day':day,'month':month,
                    'daily_reserved_micro_usd':daily[0] if daily else 0,
                    'daily_requests':daily[1] if daily else 0,
                    'monthly_reserved_micro_usd':monthly[0], 'monthly_requests':monthly[1]},
                'warning_percent':max(x for x in (0,50,75,90,100) if x<=percent)}
