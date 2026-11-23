"""The owner-approved Fold preview. Reuses Cloud Teacher, its client contract and its ledger."""
from hashlib import sha256
from http.client import HTTPConnection
from io import BytesIO
from pathlib import Path
from threading import Lock
from time import time
import hmac
import json
import os
import re
import sqlite3
import sys

from budget import Budget
from server import Handler, RequestLimiter, TeacherServer, authorize, request_cost_bound


APPROVAL = 'fold-owner-20261123'
MODEL = 'gpt-5-mini-2025-08-07'
OUTPUT_TOKENS = 1000
CEILING = 250_000
FIXTURES = {
    '/assistant/ask': 'What is a haiku?',
    '/guardian/analyze': 'Please reply right away or I will be upset.',
}
STATUS_ROUTES = {'/assistant/ask', '/guardian/status'}


class PreviewBudget(Budget):
    def __init__(self, database, expires_at, owner_token, reserve):
        if reserve * len(FIXTURES) > CEILING:
            raise ValueError('The reviewed model exceeds the total preview ceiling')
        self.digests = {
            hmac.new(owner_token.encode(), text.encode(), sha256).hexdigest()
            for text in ('assistant\0' + FIXTURES['/assistant/ask'], FIXTURES['/guardian/analyze'])
        }
        super().__init__(database, APPROVAL, expires_at, CEILING, reserve, limit=CEILING,
                         monthly_limit=CEILING, request_limit=len(FIXTURES))
        with sqlite3.connect(self.database) as db:
            db.execute('BEGIN IMMEDIATE')
            existing = db.execute('SELECT cycle,digest FROM request_reservations').fetchall()
            if any(cycle != APPROVAL or digest not in self.digests for cycle, digest in existing):
                raise ValueError('The preview ledger belongs to a different approval or token')
            # The base ledger inserts in its spending transaction. An attempted second reservation
            # for either synthetic input rolls back all accounting in that same transaction.
            db.execute('CREATE UNIQUE INDEX IF NOT EXISTS fold_one_per_fixture '
                       'ON request_reservations(cycle,digest)')

    def begin_request(self, request_id, payload_digest):
        if payload_digest not in self.digests:
            raise ValueError('Unapproved preview fixture')
        try:
            return super().begin_request(request_id, payload_digest)
        except sqlite3.IntegrityError:
            with sqlite3.connect(self.database) as db:
                previous = db.execute('SELECT state FROM request_reservations WHERE cycle=? AND digest=?',
                                      (APPROVAL, payload_digest)).fetchone()
            if previous is None:
                raise
            return {'decision': 'duplicate', 'state': previous[0]}


class PreviewServer(TeacherServer):
    def __init__(self, address, control_dir, state_path, expires_at):
        self.control_dir = Path(control_dir)
        self.owner_token = (self.control_dir / 'owner-token').read_text().strip()
        if not re.fullmatch(r'[a-f0-9]{64}', self.owner_token):
            raise ValueError('Invalid independent preview token')
        if not 0 < expires_at - time() <= 1800:
            raise ValueError('The preview window must be within 30 minutes')
        self.model = MODEL
        self.output_tokens = OUTPUT_TOKENS
        self.reasoning_effort = 'minimal'
        self.cost_bound = request_cost_bound(self.model, self.output_tokens, self.reasoning_effort)
        self.budget = PreviewBudget(state_path, expires_at, self.owner_token, self.cost_bound)
        self.expires_at = expires_at
        self.automatic_analysis_enabled = False
        self.limiter = RequestLimiter()
        self.fold_checks = set()
        self.check_lock = Lock()
        super().__init__(address, PreviewHandler)

    def readiness(self):
        with self.check_lock:
            return self.fold_checks == STATUS_ROUTES

    @property
    def analysis_enabled(self):
        return (time() < self.expires_at and self.readiness()
                and (self.control_dir / 'approved').is_file()
                and (self.control_dir / 'openai-key').is_file())

    @property
    def openai_key(self):
        if not self.analysis_enabled:
            raise ValueError('Paid preview is closed')
        key = (self.control_dir / 'openai-key').read_text().strip()
        if not re.fullmatch(r'sk-[A-Za-z0-9_-]{16,}', key):
            raise ValueError('Invalid preview provider credential')
        return key


class PreviewHandler(Handler):
    def send_json(self, value):
        if self.command == 'GET' and self.path in STATUS_ROUTES and value.get('status') == 'authenticated':
            # The private gateway overwrites this header and marks only non-loopback Fold traffic.
            if self.headers.get('X-Nova-Fold-Client') == '1':
                with self.server.check_lock:
                    self.server.fold_checks.add(self.path)
            value = {**value, 'fold_owner_preview': {'phone_checked': self.server.readiness()}}
        super().send_json(value)

    def do_POST(self):
        if self.path not in FIXTURES:
            return self.send_failure(404, 'not_found')
        if not authorize(self.headers.get('Authorization', ''), self.server.owner_token):
            return self.send_failure(401, 'authentication_required')
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 2048 or self.headers.get('Transfer-Encoding'):
                raise ValueError
            raw = self.rfile.read(length)
            payload = json.loads(raw)
            if (not isinstance(payload, dict) or set(payload) != {'message', 'consent', 'request_id', 'protocol_version'}
                    or payload.get('consent') is not True or payload.get('message') != FIXTURES[self.path]):
                raise ValueError
        except (OSError, TypeError, ValueError):
            return self.send_failure(400, 'unapproved_preview_fixture_or_consent')
        if not self.server.analysis_enabled:
            return self.send_failure(503, 'analysis_disabled')
        original = self.rfile
        try:
            self.rfile = BytesIO(raw)
            super().do_POST()
        finally:
            self.rfile = original


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == '--health':
        conn = HTTPConnection('127.0.0.1', 8787, timeout=3)
        conn.request('GET', '/health')
        if conn.getresponse().status != 200:
            sys.exit(1)
    else:
        with PreviewServer(('0.0.0.0', 8787), '/preview-control', '/state/budget.db',
                           int(os.environ['NOVA_FOLD_PREVIEW_END'])) as server:
            server.serve_forever()
