#!/usr/bin/env python3
"""Exact case accounting only; runtime and release approval are separate gates."""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(reports, expected):
    if not isinstance(expected, list) or not expected:
        raise ValueError('Empty or invalid reviewed case manifest')
    want = []
    for entry in expected:
        if not isinstance(entry, dict) or set(entry) != {'classname', 'name'}:
            raise ValueError('Malformed case manifest entry')
        pair = entry['classname'], entry['name']
        if not all(isinstance(value, str) and value.strip() == value and value for value in pair):
            raise ValueError('Invalid case identity')
        want.append(pair)
    if len(want) != len(set(want)):
        raise ValueError('Duplicate manifest identity')
    cases = [case for report in Path(reports).rglob('*.xml')
             for case in ET.parse(report).iter('testcase')]
    got = [(case.get('classname'), case.get('name')) for case in cases]
    if len(got) != len(want) or set(got) != set(want):
        raise ValueError(f'Case mismatch: missing={sorted(set(want)-set(got))}; unexpected={set(got)-set(want)}; count={len(got)}')
    if any(case.find(tag) is not None for case in cases for tag in ('failure', 'error', 'skipped')):
        raise ValueError('Failed, errored or skipped required Android case')
    return len(cases)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: verify_evidence179_cases.py REPORT_DIRECTORY REVIEWED_MANIFEST')
    count = verify(Path(sys.argv[1]), json.loads(Path(sys.argv[2]).read_text()))
    print(f'PASS: {count} exact Evidence Locker Android cases, once each; accounting only')
