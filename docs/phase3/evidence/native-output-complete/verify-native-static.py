from pathlib import Path
import json,subprocess,os,hashlib
b=Path('/workspace/scratch/7f3a71577b99');r=b/'runtime-native-output';cases=json.loads((b/'stamped-static-results.json').read_text());env=os.environ.copy();env['ANDROID_HOME']=str(b/'toolchain/android-sdk');env['JAVA_HOME']=str(b/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64');env['PATH']=env['JAVA_HOME']+'/bin:'+env['ANDROID_HOME']+'/build-tools/36.0.0:'+env['PATH'];results={}
for name,item in cases.items():
 if name.startswith(('backup-debug','backup-phase0','model-debug','model-phase0')):continue
 if name.startswith('instrumentation-fixture-'):
  filename=name[len('instrumentation-fixture-'):];a=r/'app/src/test/resources/phase0/legacy-v1'/filename;c=r/'app/src/androidTest/assets/phase0'/filename;assert a.read_bytes()==c.read_bytes();results[name]={'exit':0,'sha256':hashlib.sha256(a.read_bytes()).hexdigest()};continue
 cmd=item['command'];cwd=r/'app/src/test/resources/phase0/legacy-v1' if name=='fixtures' else r
 with (b/('native-static-'+name+'.log')).open('w') as f:p=subprocess.run(cmd,cwd=cwd,env=env,stdout=f,stderr=subprocess.STDOUT)
 results[name]={'exit':p.returncode,'command':cmd};(b/'native-static-results.json').write_text(json.dumps(results,indent=2)+'\n');print(name,p.returncode,flush=True);assert p.returncode==0
