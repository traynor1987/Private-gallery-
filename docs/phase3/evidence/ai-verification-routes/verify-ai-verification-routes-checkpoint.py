import pathlib,subprocess,json,shutil,hashlib,xml.etree.ElementTree as E
base=pathlib.Path(__file__).resolve().parent
root=base/'runtime-native-output'
inputs=json.loads((base/'ai-verification-routes-frozen-inputs.json').read_text())
def confirm():
    changed=[p for p,h in inputs.items() if hashlib.sha256((root/p).read_bytes()).hexdigest()!=h]
    assert not changed,changed
commands=[
 ('targeted',[':app:testDebugUnitTest','--tests','*Owned*Test','--tests','*ReservedValueFactoryTest','--tests','*ReservedOwnershipTest','--tests','*ReleaseCapacityTest','--tests','*Phase3AiVerification*Test','--tests','*OpenAiImageApiTest','--tests','*ReplicateSeedreamTest']),
 ('full-jvm',[':app:testDebugUnitTest']),
 ('lint-apks',[':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest']),
 ('phase0',[':app:testPhase0EvidenceUnitTest',':app:lintPhase0Evidence',':app:assemblePhase0Evidence']),
 ('phase0-instrumentation',['-PPRIVATE_GALLERY_PHASE0_EVIDENCE=true',':app:assemblePhase0EvidenceAndroidTest'])]
results={}
for name,args in commands:
    confirm()
    with (base/('ai-verification-routes-checkpoint-'+name+'.log')).open('w') as output:
        run=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args,'--offline','--stacktrace'],cwd=root,stdout=output,stderr=subprocess.STDOUT)
    entry={'exit':run.returncode,'command':args}
    if name in ['targeted','full-jvm','phase0'] and run.returncode==0:
        source=root/'app/build/test-results'/('testPhase0EvidenceUnitTest' if name=='phase0' else 'testDebugUnitTest')
        dest=base/('ai-verification-routes-checkpoint-'+name+'-xml');dest.mkdir(exist_ok=True); [shutil.copy2(p,dest/p.name) for p in source.glob('TEST-*.xml') if name != 'targeted' or any(n in p.name for n in ['Owned','ReservedValueFactoryTest','ReservedOwnershipTest','ReleaseCapacityTest','Phase3AiVerification','OpenAiImageApiTest','ReplicateSeedreamTest'])]
        entry.update({k:sum(int(E.parse(p).getroot().attrib[k])for p in dest.glob('TEST-*.xml'))for k in ['tests','failures','errors','skipped']})
    results[name]=entry
    (base/'ai-verification-routes-checkpoint-build-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(name,entry,flush=True)
    if run.returncode:raise SystemExit(run.returncode)
confirm();print('Frozen source unchanged: '+str(len(inputs))+' inputs',flush=True)
