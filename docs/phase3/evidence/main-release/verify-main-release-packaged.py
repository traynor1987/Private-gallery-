import pathlib,subprocess,json,os
base=pathlib.Path(__file__).resolve().parent
root=base/'runtime-main-release'
env=os.environ.copy();env['ANDROID_HOME']=str(base/'toolchain/android-sdk');env['ANDROID_SDK_ROOT']=env['ANDROID_HOME'];env['JAVA_HOME']=str(base/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64');env['PATH']=env['JAVA_HOME']+'/bin:'+env['ANDROID_HOME']+'/build-tools/36.0.0:'+env['PATH']
commands=json.loads((base/'ai-verification-packaged-static-results.json').read_text())
results={}
for name,entry in commands.items():
    with(base/('main-release-packaged-'+name+'.log')).open('w')as out:
        run=subprocess.run(entry['command'],cwd=root,env=env,stdout=out,stderr=subprocess.STDOUT)
    results[name]={**entry,'exit':run.returncode}
    (base/'main-release-packaged-static-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(name,run.returncode,flush=True)
    if run.returncode:raise SystemExit(run.returncode)
