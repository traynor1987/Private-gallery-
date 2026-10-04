import sys,json,hashlib,subprocess,pathlib,shutil,xml.etree.ElementTree as ET
root=pathlib.Path('/workspace/scratch/7f3a71577b99');repo=root/'runtime-staging-key-root';label=sys.argv[1];selection=sys.argv[2:]
paths=[*repo.glob('app/src/main/java/uk/co/traynor/privategallery/core/security/staging/*'),*repo.glob('app/src/main/c/*'),repo/'app/src/test/java/uk/co/traynor/privategallery/core/security/staging/Phase3OwnedStagingKeyTest.kt',repo/'app/build.gradle.kts',repo/'.github/workflows/android.yml']
paths += [repo/f'app/src/main/java/uk/co/traynor/privategallery/core/security/{n}.kt' for n in ['ScopedIoGuard','ReleaseReservation','OwnedResource','OwnedByteBuffer','OwnedNativeOutputBuffer']]
inputs={str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}
pre=root/(label+'-source');pre.mkdir(exist_ok=True)
for name in inputs:
 p=pre/name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(repo/name,p)
cmd=['python3',str(root/'toolchain/run-gradle.py'),'-I',str(root/'staging-key-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-staging-key/project-cache',':app:testDebugUnitTest']
for name in selection:cmd+=['--tests',name]
cmd+=['--offline','--stacktrace']
with (root/(label+'.log')).open('w') as out:r=subprocess.run(cmd,cwd=repo,stdout=out,stderr=subprocess.STDOUT)
xml={};dest=root/(label+'-xml');dest.mkdir(exist_ok=True)
for p in pathlib.Path('/tmp/privategallery-phase3-staging-key/app-build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
 shutil.copyfile(p,dest/p.name);xml[p.name]={'attributes':ET.parse(p).getroot().attrib,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
totals={k:sum(int(v['attributes'][k]) for v in xml.values()) for k in ['tests','failures','errors','skipped']}
d={'command':cmd,'exit':r.returncode,'inputs':inputs,'sourceUnchanged':all(hashlib.sha256((repo/n).read_bytes()).hexdigest()==h for n,h in inputs.items()),'xml':xml,'totals':totals,'qualification':'Host-JVM actual JNI/kernel; held Java borrow is not blocked native invocation'}
(root/(label+'-results.json')).write_text(json.dumps(d,indent=2)+'\n');print(json.dumps({k:d[k] for k in ['exit','sourceUnchanged','totals']}));sys.exit(r.returncode)
