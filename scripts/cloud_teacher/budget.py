"""Persistent, fail-closed reservations for an explicitly funded credit cycle."""
import sqlite3
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

    def reserve(self):
        with sqlite3.connect(self.database) as db:
            db.execute("BEGIN IMMEDIATE")
            if self.clock() >= self.expires_at:
                return False
            row = db.execute("SELECT total FROM reservations WHERE cycle = ?", (self.cycle,)).fetchone()
            total = (row[0] if row else 0) + self.reserve_amount
            if total > self.ceiling:
                return False
            db.execute("INSERT INTO reservations VALUES (?, ?) ON CONFLICT(cycle) DO UPDATE SET total = excluded.total",
                       (self.cycle, total))
            return True

    def status(self):
        with sqlite3.connect(self.database) as db:
            row = db.execute("SELECT total FROM reservations WHERE cycle = ?", (self.cycle,)).fetchone()
        total = row[0] if row else 0
        expired = self.clock() >= self.expires_at
        percent = min(100, 100 * total // self.ceiling) if self.ceiling else 100
        return {"cycle": self.cycle, "expires_at": self.expires_at, "expired": expired,
                "configured_ceiling_micro_usd": self.ceiling, "reserved_upper_bound_micro_usd": total,
                "remaining_micro_usd": 0 if expired else max(0, self.ceiling - total),
                "warning_percent": max((x for x in (0, 50, 75, 90, 100) if x <= percent))}
