#!/usr/bin/env python3
"""Require every specified mandatory security/UI case in the original API37 XML."""
import sys,json,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from verify_home1710_runtime import verify as verify_baseline
MATRIX=Path(__file__).resolve().parent.parent/'docs/release17101/runtime-cases.json'
def verify(path):
 verify_baseline(path)
 expected={(cls,name) for cls,names in json.loads(MATRIX.read_text()).items() for name in names}
 seen=set()
 with zipfile.ZipFile(path) as archive:
  for name in archive.namelist():
   if 'outputs/androidTest-results/' not in name or not name.endswith('.xml'):continue
   root=ET.fromstring(archive.read(name))
   for case in root.iter('testcase'):
    identity=(case.get('classname',''),case.get('name',''))
    if identity in expected:
     if any(case.find(x) is not None for x in ('failure','error','skipped')):raise ValueError('Required case did not pass: '+str(identity))
     seen.add(identity)
 if not expected<=seen:raise ValueError('Missing required NovaOS cases: '+str(sorted(expected-seen)))
 return len(seen)
if __name__=='__main__':print('PASS: NovaOS original XML mandatory cases;',verify(sys.argv[1]),'passed')
