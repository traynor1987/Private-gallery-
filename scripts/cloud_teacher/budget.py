"""Persistent, fail-closed reservations for an explicitly funded credit cycle."""
import sqlite3
from datetime import UTC, datetime
from time import time

DEFAULT_LIMIT_MICRO_USD = 90_000_000


class Budget:
    def __init__(self, database, cycle, expires_at, credits, reserve, limit=DEFAULT_LIMIT_MICRO_USD, clock=time):
        if not cycle or not 0 < limit <= DEFAULT_LIMIT_MICRO_USD or credits < 0 or reserve <= 0:
            raise ValueError("Invalid credit-cycle configuration")
        self.database = database
        self.cycle = cycle
        self.expires_at = expires_at
        self.ceiling = min(credits, limit)
        self.reserve_amount = reserve
        self.clock = clock
        with sqlite3.connect(database) as db:
            db.execute("CREATE TABLE IF NOT EXISTS reservations (cycle TEXT PRIMARY KEY, total INTEGER NOT NULL)")

            db.execute("""CREATE TABLE IF NOT EXISTS daily_reservations (
                day TEXT PRIMARY KEY, total INTEGER NOT NULL, requests INTEGER NOT NULL
            )""")

    def reserve(self):
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN IMMEDIATE")
            now = self.clock()
            if now >= self.expires_at:
                return False
            row = db.execute("SELECT total FROM reservations WHERE cycle = ?", (self.cycle,)).fetchone()
            total = (row[0] if row else 0) + self.reserve_amount
            if total > self.ceiling:
                return False
            db.execute("INSERT INTO reservations VALUES (?, ?) ON CONFLICT(cycle) DO UPDATE SET total = excluded.total",
                       (self.cycle, total))
            day = datetime.fromtimestamp(now, UTC).date().isoformat()
            db.execute("""INSERT INTO daily_reservations VALUES (?, ?, 1)
                ON CONFLICT(day) DO UPDATE SET total = total + excluded.total, requests = requests + 1""",
                       (day, self.reserve_amount))
            return True

    def status(self):
        now = self.clock()
        day = datetime.fromtimestamp(now, UTC).date().isoformat()
        month = day[:7]
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN")
            row = db.execute("SELECT total FROM reservations WHERE cycle = ?", (self.cycle,)).fetchone()
            daily = db.execute("SELECT total, requests FROM daily_reservations WHERE day = ?", (day,)).fetchone()
            monthly = db.execute("""SELECT COALESCE(SUM(total), 0), COALESCE(SUM(requests), 0)
                FROM daily_reservations WHERE day BETWEEN ? AND ?""", (month + "-01", day)).fetchone()
        total = row[0] if row else 0
        expired = now >= self.expires_at
        percent = min(100, 100 * total // self.ceiling) if self.ceiling else 100
        return {"cycle": self.cycle, "expires_at": self.expires_at, "expired": expired,
                "configured_ceiling_micro_usd": self.ceiling, "reserved_upper_bound_micro_usd": total,
                "remaining_micro_usd": 0 if expired else max(0, self.ceiling - total),
                "request_reservation_micro_usd": self.reserve_amount,
                "reservation_activity_utc": {
                    "day": day, "month": month,
                    "daily_reserved_micro_usd": daily[0] if daily else 0,
                    "daily_requests": daily[1] if daily else 0,
                    "monthly_reserved_micro_usd": monthly[0],
                    "monthly_requests": monthly[1],
                },
                "warning_percent": max((x for x in (0, 50, 75, 90, 100) if x <= percent))}
