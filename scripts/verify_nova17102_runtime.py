#!/usr/bin/env python3
"""Account original UI/route XML without turning incomplete evidence into release approval."""
import argparse,json,sys,zipfile,xml.etree.ElementTree as ET
from pathlib import Path
MATRIX=Path(__file__).resolve().parent.parent/'docs/release17102/runtime-cases.json'
def verify(path,matrix=MATRIX):
 expected={(cls,name) for cls,names in json.loads(matrix.read_text()).items() for name in names}
 seen=set()
 with zipfile.ZipFile(path) as z:
  names=z.namelist();assert len(names)==len(set(names)),'Duplicate archive members'
  for name in names:
   if 'outputs/androidTest-results/' not in name or not name.endswith('.xml'):continue
   root=ET.fromstring(z.read(name))
   for case in root.iter('testcase'):
    assert not any(case.find(x) is not None for x in ('failure','error','skipped')),'Nonpassing case '+str(case.attrib)
    identity=(case.get('classname'),case.get('name'))
    assert identity not in seen,'Duplicate original case';seen.add(identity)
  assert seen==expected,{'missing':sorted(expected-seen),'extra':sorted(seen-expected)}
  for suffix in ('nova17102/runtime-result.txt','nova17102/route-accounting.txt','nova17102/tor-route-result.txt'):
   assert any(name.endswith(suffix) and z.getinfo(name).file_size>0 for name in names),'Missing route evidence '+suffix
 return len(seen)
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('archive');parser.add_argument('--matrix',type=Path,default=MATRIX);args=parser.parse_args()
 print('PASS: original selected NovaOS UI/transport XML;',verify(args.archive,args.matrix),'cases; selected matrix',args.matrix)
