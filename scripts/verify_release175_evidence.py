"""Validate immutable, exact-source CI evidence before release-only continuation."""
import hashlib,json,pathlib,sys,xml.etree.ElementTree as ET
FROZEN='69bfade789df4a49d911091bb8cf8205c88a3a39'
EXPECTED={'jvm':349,'focused':91,'security':74,'full':365}
def count_reports(directory,expected):
 reports=sorted(pathlib.Path(directory).rglob('TEST-*.xml'))
 if not reports:raise ValueError('Missing XML reports')
 count=0
 for report in reports:
  root=ET.parse(report).getroot();cases=list(root.iter('testcase'))
  if int(root.get('tests',len(cases)))!=len(cases):raise ValueError('Report count mismatch')
  for key in ('failures','errors','skipped'):
   if int(root.get(key,0)):raise ValueError('Non-passing report '+key)
  for case in cases:
   if any(case.find(key) is not None for key in ('failure','error','skipped')):raise ValueError('Non-passing testcase')
  count+=len(cases)
 if count!=expected:raise ValueError(f'Expected {expected} tests, got {count}')
 return count

def verify(root,source):
 root=pathlib.Path(root)
 if source!=FROZEN:raise ValueError('Wrong frozen source')
 if (root/'acceptance-diagnostics/professional-ui/source-sha.txt').read_text().strip()!=source:raise ValueError('Focused evidence source mismatch')
 paths={'jvm':'test-results/testDebugUnitTest','focused':'acceptance-diagnostics/professional-ui/results','security':'acceptance-diagnostics/hidden-browser-preflight','full':'outputs/androidTest-results/connected/debug'}
 counts={key:count_reports(root/path,EXPECTED[key]) for key,path in paths.items()}
 apk=root/'outputs/apk/debug/app-debug.apk'
 expected=(root/'acceptance-diagnostics/apk-sha256.txt').read_text().split()[0]
 actual=hashlib.sha256(apk.read_bytes()).hexdigest()
 if actual!=expected:raise ValueError('Original tested debug APK hash mismatch')
 for name in ('process-seed.txt','process-verify.txt','camera-process-seed.txt','camera-process-verify.txt'):
  if 'OK (1 test)' not in (root/'acceptance-diagnostics/hidden-browser-preflight'/name).read_text():raise ValueError('Missing process boundary proof')
 return {'source_sha':source,'counts':counts,'tested_debug_apk_sha256':actual,'zero_failures_errors_skips':True}
if __name__=='__main__':print(json.dumps(verify(sys.argv[1],sys.argv[2]),indent=2))
