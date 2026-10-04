import pathlib,subprocess,json,shutil,hashlib,xml.etree.ElementTree as E
base=pathlib.Path(__file__).resolve().parent
root=base/'runtime-native-output'; candidate=base/'runtime-probe-buffer-binding'
ev=candidate/'docs/phase3/evidence/probe-buffer-binding'
def hashes():
 return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(root.glob('app/build/outputs/apk/**/*.apk'))}
(ev/'pre-marker-apk-hashes.json').write_text(json.dumps(hashes(),indent=2)+'\n')
for p in root.glob('app/build/reports/lint-results-*.xml'):shutil.copy2(p,ev/('pre-marker-'+p.name))
changed=['app/src/test/java/uk/co/traynor/privategallery/core/security/OwnedByteBufferTest.kt','app/src/androidTest/java/uk/co/traynor/privategallery/core/security/Phase3ProbeBufferBindingTest.kt']
for p in changed:shutil.copy2(candidate/p,root/p)
inputs=json.loads((base/'probe-buffer-binding-frozen-inputs.json').read_text())
inputs={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in inputs}
assert all(hashlib.sha256((candidate/p).read_bytes()).hexdigest()==h for p,h in inputs.items()),'candidate mismatch'
(base/'probe-buffer-binding-final-inputs.json').write_text(json.dumps(inputs,indent=2)+'\n')
results={}
commands=[('debug',[':app:testDebugUnitTest','--tests','*OwnedByteBufferTest']),('phase0',[':app:testPhase0EvidenceUnitTest','--tests','*OwnedByteBufferTest']),('debug-android',[':app:assembleDebug',':app:assembleDebugAndroidTest']),('phase0-android',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0Evidence',':app:assemblePhase0EvidenceAndroidTest'])]
for name,args in commands:
 with(base/('probe-buffer-binding-marker-'+name+'.log')).open('w') as out:r=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args,'--offline','--stacktrace'],cwd=root,stdout=out,stderr=subprocess.STDOUT)
 entry={'exit':r.returncode,'command':args}
 if name in ['debug','phase0'] and r.returncode==0:
  source=root/'app/build/test-results'/('testDebugUnitTest' if name=='debug' else 'testPhase0EvidenceUnitTest')/'TEST-uk.co.traynor.privategallery.core.security.OwnedByteBufferTest.xml'
  shutil.copy2(source,ev/('marker-'+name+'-OwnedByteBufferTest.xml'))
  entry.update({k:int(E.parse(source).getroot().attrib[k]) for k in ['tests','failures','errors','skipped']})
 results[name]=entry;(base/'probe-buffer-binding-marker-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,entry,flush=True)
 if r.returncode:raise SystemExit(r.returncode)
assert all(hashlib.sha256((root/p).read_bytes()).hexdigest()==h for p,h in inputs.items())
(ev/'final-apk-hashes.json').write_text(json.dumps(hashes(),indent=2)+'\n')
print('Final source matches candidate: '+str(len(inputs))+' inputs',flush=True)
