import pathlib,subprocess,json,shutil,hashlib,xml.etree.ElementTree as E
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-photo-encoder'
inputs=json.loads((base/'photo-encoder-frozen-inputs.json').read_text())
def confirm():
 changed=[p for p,h in inputs.items()if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h];assert not changed,changed
 actual={str(p.relative_to(root))for p in (root/'app/src').rglob('*')if p.is_file()};assert actual=={p for p in inputs if p.startswith('app/src/')},'app/src membership mismatch'
filters=['*Phase3PhotoEncoder*Test','*Phase3PhotoGeneration*Test','*Phase3Text*Test','*Phase3Flux*Test','*Owned*Test','*ReservedValueFactoryTest','*ReservedOwnershipTest','*ReleaseCapacityTest','*Phase3AiVerification*Test','*Phase3AiSetup*Test','*OpenAiImageApiTest','*Replicate*Test','*Phase3PredictionStatus*Test','*Phase3PredictionCancellation*Test','*Secondary*Test','*Phase3Activity*Test','*MainActivitySessionTest','*Phase3Video*Test','*Scoped*Test','*BrowserVideo*Test','*VideoHeader*Test','*Manifest*Test']
commands=[('targeted',[':app:testDebugUnitTest',*[part for f in filters for part in ['--tests',f]]]),('full-jvm',[':app:testDebugUnitTest']),('lint-apks',[':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest']),('phase0',[':app:testPhase0EvidenceUnitTest',':app:lintPhase0Evidence',':app:assemblePhase0Evidence']),('phase0-instrumentation',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]
results={}
for name,args in commands:
 confirm()
 if name in ['targeted','full-jvm','phase0']:
  xmlroot=pathlib.Path('/tmp/privategallery-phase3-photo-encoder/app-build/test-results')/('testPhase0EvidenceUnitTest'if name=='phase0'else'testDebugUnitTest')
  for old in xmlroot.glob('TEST-*.xml'):old.unlink()
 with(base/('photo-encoder-checkpoint-'+name+'.log')).open('w')as output:run=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),'-I',str(base/'photo-encoder-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-photo-encoder/project-cache',*args,'--offline','--stacktrace'],cwd=root,stdout=output,stderr=subprocess.STDOUT)
 entry={'exit':run.returncode,'command':['-I','docs/phase3/evidence/photo-encoder/isolated-build.init.gradle','--project-cache-dir','/tmp/privategallery-phase3-photo-encoder/project-cache',*args]}
 if name in ['targeted','full-jvm','phase0']and run.returncode==0:
  source=pathlib.Path('/tmp/privategallery-phase3-photo-encoder/app-build/test-results')/('testPhase0EvidenceUnitTest'if name=='phase0'else'testDebugUnitTest');dest=base/('photo-encoder-checkpoint-'+name+'-xml');dest.mkdir(exist_ok=True)
  for p in source.glob('TEST-*.xml'):
   if name!='targeted'or any(n in p.name for n in ['Phase3PhotoEncoder','Phase3PhotoGeneration','Phase3Text','Phase3Flux','Owned','ReservedValueFactoryTest','ReservedOwnershipTest','ReleaseCapacityTest','Phase3AiVerification','Phase3AiSetup','OpenAiImageApiTest','Replicate','Phase3PredictionStatus','Phase3PredictionCancellation','Secondary','Phase3Activity','MainActivitySessionTest','Phase3Video','Scoped','BrowserVideo','VideoHeader','Manifest']):shutil.copy2(p,dest/p.name)
  entry.update({k:sum(int(E.parse(p).getroot().attrib[k])for p in dest.glob('TEST-*.xml'))for k in ['tests','failures','errors','skipped']})
 results[name]=entry;(base/'photo-encoder-checkpoint-build-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,entry,flush=True)
 if run.returncode:raise SystemExit(run.returncode)
confirm();print('Frozen source unchanged: '+str(len(inputs))+' inputs',flush=True)
