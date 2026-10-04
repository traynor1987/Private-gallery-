import pathlib,subprocess,json
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-photo-producer'
args=['-I',str(base/'photo-producer-isolated.init.gradle'),'--project-cache-dir','/tmp/privategallery-phase3-photo-producer/project-cache',':app:testDebugUnitTest','--tests','*Phase3PhotoGeneration*Test','--offline','--stacktrace']
with(base/'photo-producer-api-red.log').open('w')as out:r=subprocess.run(['python3',str(base/'toolchain/run-gradle.py'),*args],cwd=root,stdout=out,stderr=subprocess.STDOUT)
(base/'photo-producer-api-red-results.json').write_text(json.dumps({'exit':r.returncode,'command':args,'qualification':'New helper API missing; inspect compilation diagnostics before interpreting RED. No executed case count inferred.'},indent=2)+'\n')
print('API RED exit',r.returncode,flush=True)
