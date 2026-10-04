import pathlib,subprocess,json,hashlib,shutil,xml.etree.ElementTree as E
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-photo-encoder';inputs=json.loads((base/'photo-encoder-baseline-inputs.json').read_text())
for p,h in inputs.items():assert hashlib.sha256((root/p).read_bytes()).hexdigest()==h,p
args=['-I',str(base/'photo-encoder-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-photo-encoder/project-cache',':app:testDebugUnitTest','--tests','*Phase3PhotoEncoderBufferTest','--offline','--stacktrace']
with(base/'photo-encoder-baseline.log').open('w')as out:r=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args],cwd=root,stdout=out,stderr=subprocess.STDOUT)
ev=root/'docs/phase3/evidence/photo-encoder/original-source-red';ev.mkdir(parents=True)
shutil.copy2(base/'photo-encoder-baseline.log',ev/'build.log');shutil.copy2(base/'photo-encoder-baseline-inputs.json',ev/'inputs.json')
for p in (root/'app/src/test/java/uk/co/traynor/privategallery/core/editor').glob('Phase3PhotoEncoderBufferTest.kt'):shutil.copy2(p,ev/p.name)
shutil.copy2(root/'app/src/main/java/uk/co/traynor/privategallery/core/editor/PhotoRenderer.kt',ev/'PhotoRenderer.kt')
files=list(pathlib.Path('/tmp/privategallery-phase3-photo-encoder/app-build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
entries={p.name:E.parse(p).getroot().attrib for p in files}
for p in files:shutil.copy2(p,ev/p.name)
(ev/'summary.json').write_text(json.dumps({'exit':r.returncode,'command':args,'xml':entries,'qualification':'Actual preceding remote0b2f8c1 encoder source, unchanged production. Inspect actual XML/diagnostics; neither expected RED nor OOM inferred as behavioral evidence.'},indent=2)+'\n')
print({'exit':r.returncode,'xml':entries},flush=True)
