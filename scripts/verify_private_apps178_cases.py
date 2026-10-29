#!/usr/bin/env python3
"""Exact selected-case gate. Missing, duplicate, skipped or unexpected evidence refuses approval."""
import json,pathlib,sys,xml.etree.ElementTree as ET
expected=json.loads(pathlib.Path('scripts/private_apps178_cases.json').read_text())
want=[(x['classname'],x['name']) for x in expected]
assert want and len(set(want))==len(want)
cases=[c for p in pathlib.Path(sys.argv[1]).rglob('*.xml') for c in ET.parse(p).getroot().iter('testcase')]
got=[(c.get('classname'),c.get('name')) for c in cases]
assert len(got)==len(want) and set(got)==set(want),(sorted(set(want)-set(got)),sorted(set(got)-set(want)))
assert all(not any(c.find(t) is not None for t in ('failure','error','skipped')) for c in cases)
print('PASS: exact Private Apps Android cases',len(got))
