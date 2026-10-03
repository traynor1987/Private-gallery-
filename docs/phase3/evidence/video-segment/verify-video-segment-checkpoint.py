import pathlib,subprocess,json,shutil,hashlib,xml.etree.ElementTree as E
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-video-segment'
inputs=json.loads((base/'video-segment-frozen-inputs.json').read_text())
def confirm():
 changed=[p for p,h in inputs.items()if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h];assert not changed,changed
filters=['*Owned*Test','*ReservedValueFactoryTest','*ReservedOwnershipTest','*ReleaseCapacityTest','*Phase3AiVerification*Test','*Phase3AiSetup*Test','*OpenAiImageApiTest','*ReplicateSeedreamTest','*Secondary*Test','*Phase3Activity*Test','*MainActivitySessionTest','*Phase3Video*Test','*Scoped*Test','*BrowserVideo*Test','*VideoHeader*Test','*Manifest*Test']
commands=[('targeted',[':app:testDebugUnitTest',*[part for f in filters for part in ['--tests',f]]]),('full-jvm',[':app:testDebugUnitTest']),('lint-apks',[':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest']),('phase0',[':app:testPhase0EvidenceUnitTest',':app:lintPhase0Evidence',':app:assemblePhase0Evidence']),('phase0-instrumentation',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]
results={}
for name,args in commands:
 confirm()
 with(base/('video-segment-checkpoint-'+name+'.log')).open('w')as output:run=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args,'--offline','--stacktrace'],cwd=root,stdout=output,stderr=subprocess.STDOUT)
 entry={'exit':run.returncode,'command':args}
 if name in ['targeted','full-jvm','phase0']and run.returncode==0:
  source=root/'app/build/test-results'/('testPhase0EvidenceUnitTest'if name=='phase0'else'testDebugUnitTest');dest=base/('video-segment-checkpoint-'+name+'-xml');dest.mkdir(exist_ok=True)
  for p in source.glob('TEST-*.xml'):
   if name!='targeted'or any(n in p.name for n in ['Owned','ReservedValueFactoryTest','ReservedOwnershipTest','ReleaseCapacityTest','Phase3AiVerification','Phase3AiSetup','OpenAiImageApiTest','ReplicateSeedreamTest','Secondary','Phase3Activity','MainActivitySessionTest','Phase3Video','Scoped','BrowserVideo','VideoHeader','Manifest']):shutil.copy2(p,dest/p.name)
  entry.update({k:sum(int(E.parse(p).getroot().attrib[k])for p in dest.glob('TEST-*.xml'))for k in ['tests','failures','errors','skipped']})
 results[name]=entry;(base/'video-segment-checkpoint-build-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,entry,flush=True)
 if run.returncode:raise SystemExit(run.returncode)
confirm();print('Frozen source unchanged: '+str(len(inputs))+' inputs',flush=True)
