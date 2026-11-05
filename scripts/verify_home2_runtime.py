"""Require the original, complete selected API37 cases exactly once, without skipped tests."""
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET, json
root=Path(__file__).resolve().parents[1]
classes=[
 'uk.co.traynor.privategallery.core.home2.SpacePreferencesTest',
 'uk.co.traynor.privategallery.core.home2.Home2IntegrationTest',
 'uk.co.traynor.privategallery.ui.DiscreetPinUnlockTest',
 'uk.co.traynor.privategallery.SmartUnlockLifecycle173Test',
 'uk.co.traynor.privategallery.Home2MainAuthorityTest',
 'uk.co.traynor.privategallery.core.hidden.HiddenCredentialStoreTest',
 'uk.co.traynor.privategallery.core.evidence.EvidenceStore179Test',
]
if len(sys.argv)==2 and sys.argv[1]=='--classes':
 print(','.join(classes));raise SystemExit
expected={(c,n) for c in classes for n in re.findall(r'@Test\s+fun\s+(\w+)\s*\(', (root/'app/src/androidTest/java'/Path(*c.split('.'))).with_suffix('.kt').read_text())}
assert expected
reports=Path(sys.argv[1]);cases=[c for f in reports.rglob('*.xml') for c in ET.parse(f).getroot().iter('testcase')]
actual=[(c.attrib['classname'],c.attrib['name']) for c in cases]
assert len(actual)==len(set(actual)), 'Duplicate runtime cases'
assert set(actual)==expected, {'missing':sorted(expected-set(actual)),'unexpected':sorted(set(actual)-expected)}
assert all(not any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases), 'Runtime failures/errors/skips'
print(json.dumps({'result':'PASS','cases':len(cases),'classes':classes},indent=2))
