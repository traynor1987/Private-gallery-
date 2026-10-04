import pathlib,subprocess,json,shutil,hashlib,xml.etree.ElementTree as E
root=pathlib.Path('/workspace/scratch/7f3a71577b99/runtime-child-recovery');out=root.parent;runner=out/'toolchain/run-gradle.py'
commands=[('lint-apks',[':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest']),('phase0',[':app:testPhase0EvidenceUnitTest',':app:lintPhase0Evidence',':app:assemblePhase0Evidence']),('phase0-instrumentation',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]
results=json.loads((out/'child-reconstructed-checkpoint-build-results.json').read_text());inputs=json.loads((out/'child-reconstructed-frozen-inputs.json').read_text())
def confirm():
 changed=[p for p,h in inputs.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h];assert not changed,changed
for name,args in commands:
 confirm();log=out/('child-reconstructed-checkpoint-'+name+'.log')
 with log.open('w') as f:r=subprocess.run(['python3',str(runner),*args,*([] if name=='lint-apks' else ['--offline']),'--stacktrace'],cwd=root,stdout=f,stderr=subprocess.STDOUT)
 entry={'exit':r.returncode,'command':args}
 if name in ['full-jvm','phase0'] and r.returncode==0:
  xml=root/'app/build/test-results'/('testPhase0EvidenceUnitTest' if name=='phase0' else 'testDebugUnitTest');dest=out/('child-reconstructed-checkpoint-'+name+'-xml');shutil.copytree(xml,dest,dirs_exist_ok=True)
  entry.update({k:sum(int(E.parse(p).getroot().attrib[k]) for p in dest.glob('TEST-*.xml')) for k in ['tests','failures','errors','skipped']})
 results[name]=entry;(out/'child-reconstructed-checkpoint-build-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,entry,flush=True)
 if r.returncode:raise SystemExit(r.returncode)
confirm();print('Frozen source unchanged',flush=True)
