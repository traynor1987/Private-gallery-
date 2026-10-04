import pathlib, subprocess, json, shutil, hashlib, xml.etree.ElementTree as E
root=pathlib.Path('/workspace/scratch/7f3a71577b99/runtime-native-output');out=root.parent;runner=out/'toolchain/run-gradle.py'
paths=sorted(p for p in root.rglob('*') if p.is_file() and ('app/src' in str(p.relative_to(root)) or str(p.relative_to(root)).startswith(('.github/','scripts/','gradle/')) or p.name in ['build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat']))
paths=[p for p in paths if '__pycache__' not in str(p)]
inputs={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths};(out/'native-frozen-inputs.json').write_text(json.dumps(inputs,indent=2)+'\n')

commands=[('targeted',[':app:testDebugUnitTest','--tests','*Owned*Test','--tests','*ReservedValueFactoryTest','--tests','*ReservedOwnershipTest','--tests','*ReleaseCapacityTest']),('full-jvm',[':app:testDebugUnitTest']),('lint-apks',[':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest']),('phase0',[':app:testPhase0EvidenceUnitTest',':app:lintPhase0Evidence',':app:assemblePhase0Evidence']),('phase0-instrumentation',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]
results={}
def confirm():
 changed=[p for p,h in inputs.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h];assert not changed,changed
for name,args in commands:
 confirm()
 with (out/('native-checkpoint-'+name+'.log')).open('w') as f:r=subprocess.run(['python3',str(runner),*args,'--offline','--stacktrace'],cwd=root,stdout=f,stderr=subprocess.STDOUT)
 entry={'exit':r.returncode,'command':args}
 if name in ['targeted','full-jvm','phase0'] and r.returncode==0:
  xml=root/'app/build/test-results'/('testPhase0EvidenceUnitTest' if name=='phase0' else 'testDebugUnitTest');dest=out/('native-checkpoint-'+name+'-xml');shutil.copytree(xml,dest,dirs_exist_ok=True)
  entry.update({k:sum(int(E.parse(p).getroot().attrib[k]) for p in dest.glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']})
 results[name]=entry;(out/'native-checkpoint-build-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,entry,flush=True)
 if r.returncode:raise SystemExit(r.returncode)
confirm();print('Frozen source unchanged: '+str(len(inputs))+' inputs',flush=True)
