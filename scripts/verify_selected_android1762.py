"""Check every selected Android case; this alone never approves a release gate."""
import re
import sys
from pathlib import Path
import xml.etree.ElementTree as ET


def verify(reports, selectors, source_root=Path('app/src/androidTest/java')):
    expected = set()
    for selector in selectors.split(','):
        name, separator, method = selector.partition('#')
        if separator:
            expected.add((name, method))
            continue
        source = source_root.joinpath(*name.split('.')).with_suffix('.kt')
        methods = re.findall(r'@Test(?:\([^)]*\))?\s+fun\s+(\w+)', source.read_text())
        if not methods:
            raise ValueError(f'No source cases for {name}')
        expected.update((name, method) for method in methods)
    cases = [c for report in reports.rglob('*.xml') for c in ET.parse(report).iter('testcase')]
    if any(c.find(kind) is not None for c in cases for kind in ('failure', 'error', 'skipped')):
        raise ValueError('Diagnostic has failed, errored or skipped cases')
    actual = {(c.get('classname'), c.get('name')) for c in cases}
    if not expected or actual != expected or len(cases) != len(expected):
        raise ValueError(f'Diagnostic case mismatch: missing={expected-actual}, extra={actual-expected}, count={len(cases)}')
    return len(cases)


if __name__ == '__main__':
    count = verify(Path(sys.argv[1]), sys.argv[2])
    print(f'PASS: {count} exact selected Android cases, once each; execution accounting only, no release approval')
