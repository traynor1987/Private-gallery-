import pathlib,subprocess,json,hashlib
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-photo-encoder'
inputs=json.loads((base/'photo-encoder-frozen-inputs.json').read_text())
def confirm():
 for p,h in inputs.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
 actual={str(p.relative_to(root))for p in (root/'app/src').rglob('*')if p.is_file()};assert actual=={p for p in inputs if p.startswith('app/src/')}
confirm()
args=['-I',str(base/'photo-encoder-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-photo-encoder/project-cache',':app:lintDebug',':app:lintPhase0Evidence',':app:assembleDebugAndroidTest','-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest','--offline','--stacktrace']
with(base/'photo-encoder-art-cleanup-checks.log').open('w')as out:run=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args],cwd=root,stdout=out,stderr=subprocess.STDOUT)
confirm();result={'exit':run.returncode,'command':args,'inputs':len(inputs),'qualification':'Only ART PNG fixture cleanup changed since full JVM execution. Both final lints and test APKs reverified.'}
(base/'photo-encoder-art-cleanup-results.json').write_text(json.dumps(result,indent=2)+'\n');print(result,flush=True);raise SystemExit(run.returncode)
