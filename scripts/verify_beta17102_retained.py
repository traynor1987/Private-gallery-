#!/usr/bin/env python3
"""Require pinned, source-scoped original evidence; never relabel this as one full run."""
import hashlib,json,sys,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
from verify_nova17102_runtime import verify

def retained(closure,routes):
 assert hashlib.sha256(Path(closure).read_bytes()).hexdigest()=='1794260bd5bbf24eb1fc5b8c5d9a06c616c8ce427086a7eb6d4f6e0ca8227056'
 assert hashlib.sha256(Path(routes).read_bytes()).hexdigest()=='c83d50172d840c6139ffa63e9c38b03e954e29cd670bc25347616e1707af7025'
 assert verify(closure,Path('docs/release17102/closure-cases.json'),False)==25
 expected={(c,n) for c,names in json.loads(Path('docs/release17102/route-cases.json').read_text()).items() for n in names}
 with zipfile.ZipFile(closure) as z:assert z.read('nova17102/source-sha.txt').decode().strip()=='2b67f751a91961c138b4290c165415931e7c8b89'
 with zipfile.ZipFile(routes) as z:
  assert z.read('nova17102/source-sha.txt').decode().strip()=='4f4dd4f15808f1aa95160da52ac49e32fd054bee'
  seen=[]
  for name in z.namelist():
   if not name.startswith('outputs/androidTest-results/') or not name.endswith('.xml'):continue
   for case in ET.fromstring(z.read(name)).iter('testcase'):
    assert not any(case.find(tag)is not None for tag in ('failure','error','skipped'))
    seen.append((case.get('classname'),case.get('name')))
  assert len(seen)==len(set(seen))==3 and set(seen)==expected
  assert z.read('nova17102/route-original-exit-status.txt').decode().strip()=='0'
  assert z.read('nova17102/tor-route-result.txt').decode().startswith('PASS:')
 return True
if __name__=='__main__':
 assert retained(*sys.argv[1:]);print('PASS: pinned unchanged-scope25 UI and3 actualVPN/Tor route cases; distinct source checkpoints')
