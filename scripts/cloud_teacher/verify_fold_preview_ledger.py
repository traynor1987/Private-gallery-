#!/usr/bin/env python3
"""Report the existing Fold trial allowance without opening or modifying the trial."""
from contextlib import closing
import hashlib
import hmac
import json
from pathlib import Path
import re
import sqlite3
import sys


APPROVAL = 'fold-owner-20261123'
STATE = Path('/var/lib/nova-fold-owner-preview/approval-20261123')
CEILING = 250_000
RESERVATION = 102_000
INPUTS = {
    'ask_nova': 'assistant\0What is a haiku?',
    'guardian_second_opinion': 'Please reply right away or I will be upset.',
}


def verify(state):
    token_path = state / 'owner-token'
    database = state / 'ledger' / 'budget.db'
    if not token_path.is_file() or not database.is_file():
        raise ValueError('The original preview token or ledger is missing.')
    token = token_path.read_text().strip()
    if not re.fullmatch(r'[a-f0-9]{64}', token):
        raise ValueError('The persisted preview identity is invalid.')
    fixtures = {
        hmac.new(token.encode(), text.encode(), hashlib.sha256).hexdigest(): name
        for name, text in INPUTS.items()
    }
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True, timeout=3)) as db:
        db.execute('BEGIN')
        if db.execute('PRAGMA quick_check').fetchall() != [('ok',)]:
            raise ValueError('The preview ledger failed its integrity check.')
        metadata = dict(db.execute('SELECT key, value FROM budget_metadata'))
        if metadata.get('legacy_unattributed') != '0':
            raise ValueError('Preview accounting includes an unexpected legacy reservation.')
        totals = dict(db.execute('SELECT cycle, total FROM reservations'))
        rows = db.execute('SELECT digest, cycle, bound, state, actual FROM request_reservations').fetchall()

    dispositions = dict.fromkeys(INPUTS, 'available')
    reserved = 0
    for digest, cycle, bound, disposition, actual in rows:
        name = fixtures.get(digest)
        if (name is None or dispositions[name] != 'available' or cycle != APPROVAL or bound != RESERVATION
                or disposition not in ('pending', 'uncertain', 'settled')):
            raise ValueError('The ledger includes an unexpected preview reservation.')
        if disposition == 'settled' and (type(actual) is not int or not 0 <= actual <= bound):
            raise ValueError('The ledger includes invalid measured usage.')
        if disposition != 'settled' and actual is not None:
            raise ValueError('The ledger includes invalid pending usage.')
        dispositions[name] = disposition
        reserved += actual if disposition == 'settled' else bound
    if set(totals) - {APPROVAL} or totals.get(APPROVAL, 0) != reserved or reserved > CEILING:
        raise ValueError('The preview ledger totals do not match its reservations.')

    return {
        'verified': True,
        'approval': APPROVAL,
        'ceiling_micro_usd': CEILING,
        'reserved_upper_bound_micro_usd': reserved,
        'remaining_requests': sum(value == 'available' for value in dispositions.values()),
        'fixtures': dispositions,
        'paid_requests_halted': 'halted' in metadata,
    }


def main():
    try:
        print(json.dumps(verify(STATE)))
    except (OSError, sqlite3.Error, ValueError) as error:
        why = str(error) if type(error) is ValueError else 'The persisted preview ledger could not be read.'
        print(json.dumps({'verified': False, 'reason': why}))
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
