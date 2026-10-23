#!/usr/bin/env python3
"""Require fresh instrumentation XML with passing required UI classes.

The workflow deletes old outputs before each invocation and archives the full
regression results before the focused run. This checker never downloads evidence.
"""
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def verify(directory, required):
    reports = sorted(Path(directory).rglob('TEST-*.xml'))
    if not reports:
        raise ValueError('No instrumentation reports found')
    classes = set()
    count = 0
    skipped = []
    for report in reports:
        root = ET.parse(report).getroot()
        for case in root.iter('testcase'):
            if any(case.find(outcome) is not None for outcome in ('failure', 'error')):
                raise ValueError(f'Non-passing test: {case.get("classname")}#{case.get("name")}')
            if case.find('skipped') is not None:
                if case.get('classname') in required:
                    raise ValueError(f'Required UI test skipped: {case.get("classname")}#{case.get("name")}')
                # Existing WebView-capability assumptions remain visible, unchanged.
                skipped.append(f'{case.get("classname")}#{case.get("name")}')
                continue
            classes.add(case.get('classname'))
            count += 1
    missing = set(required) - classes
    if missing:
        raise ValueError(f'Required UI classes absent: {sorted(missing)}')
    if not count:
        raise ValueError('Empty instrumentation suite')
    return {'passed_tests': count, 'skipped_tests': skipped, 'required_classes': sorted(required), 'reports': [str(p) for p in reports]}


if __name__ == '__main__':
    try:
        print(json.dumps(verify(sys.argv[1], sys.argv[2].split(',')), indent=2))
    except (ValueError, ET.ParseError, OSError, IndexError) as error:
        print(f'Fresh UI evidence rejected: {error}', file=sys.stderr)
        sys.exit(1)
