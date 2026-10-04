import pathlib,subprocess,json,hashlib
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-photo-ci-repair'
inputs=json.loads((base/'photo-ci-repair-frozen-inputs.json').read_text())
def confirm():
 for p,h in inputs.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
 actual={str(p.relative_to(root))for p in (root/'app/src').rglob('*')if p.is_file()};assert actual=={p for p in inputs if p.startswith('app/src/')}
confirm()
results={}
for name,tasks in [('debug',[':app:lintDebug',':app:lintPhase0Evidence',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:assemblePhase0Evidence']),('phase0',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]:
 confirm();args=['-I',str(base/'photo-ci-repair-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-photo-ci-repair/project-cache',*tasks,'--offline','--stacktrace']
 with(base/('photo-ci-repair-art-cleanup-'+name+'.log')).open('w')as out:run=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args],cwd=root,stdout=out,stderr=subprocess.STDOUT)
 confirm();results[name]={'exit':run.returncode,'command':args,'inputs':len(inputs),'qualification':'Only ART quota fixture action/wait order changed since full JVM execution; variant-specific final checks.'}
 (base/'photo-ci-repair-art-cleanup-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,results[name],flush=True)
 if run.returncode:raise SystemExit(run.returncode)
