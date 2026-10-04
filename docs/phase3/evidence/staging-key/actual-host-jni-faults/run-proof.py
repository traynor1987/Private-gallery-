import pathlib,subprocess,os,json,hashlib,zipfile,shutil,sys
base=pathlib.Path(__file__).resolve().parent;workspace=base.parent;repo=workspace/'runtime-staging-key-root';jdk=workspace/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64';out=pathlib.Path('/tmp/privategallery-phase3-staging-key-native-fault');out.mkdir(exist_ok=True);cp=(workspace/'staging-key-host-runtime-classpath.txt').read_text();source=repo/'app/src/main/c/staging_entropy.c';assert hashlib.sha256(source.read_bytes()).hexdigest()=='0fc0379aab0e01b454a5fcde07c551a4b243143afd86fbe82ab4fa4a24c071fb'
assert 'phase0Evidence' in cp
commands=[]
def command(label,args,env=None):
 commands.append({'label':label,'command':args})
 r=subprocess.run(args,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(base/(label+'.log')).write_text(r.stdout);commands[-1]['exit']=r.returncode;(base/'command-results.json').write_text(json.dumps(commands,indent=2)+'\n');print(label,r.returncode,flush=True)
 if r.returncode:raise SystemExit(r.returncode)
 return r.stdout
flags=['gcc','-std=c11','-O2','-Wall','-Wextra','-Werror','-fPIC','-fvisibility=hidden','-fstack-protector-strong','-I'+str(jdk/'include'),'-I'+str(jdk/'include/linux')]
symbol='Java_uk_co_traynor_privategallery_core_security_staging_StagingEntropyInvocation_fillNative'
command('compile-identical-leaf',flags+['-D'+symbol+'=pg_entropy_candidate','-c',str(source),'-o',str(out/'candidate.o')])
command('compile-link-test-only-shim',flags+['-shared',str(base/'entropy_fault_shim.c'),str(out/'candidate.o'),'-pthread','-Wl,--wrap=syscall','-o',str(out/'libpg_staging_entropy.so')])
command('compile-public-harness',[str(jdk/'bin/javac'),'-cp',cp,'-d',str(out/'classes'),str(base/'StagingEntropyFaultHarness.java')])
results={}
for mode in ['eagain','eintr','0','1','7','31','33','partial','success','blocked']:
 env=os.environ.copy();env['PG_STAGING_ENTROPY_TEST_MODE']=mode
 text=command('actual-host-jni-'+mode,[str(jdk/'bin/java'),'-Djava.library.path='+str(out),'-cp',str(out/'classes')+':'+cp,'uk.co.traynor.privategallery.core.security.staging.StagingEntropyFaultHarness'],env)
 results[mode]=json.loads(text.strip());assert results[mode]['passed'] is True
(root:=base/'actual-host-jni-results.json').write_text(json.dumps({'cases':results,'totalCases':len(results),'qualification':'Actual host JVM JNI + SAME production C with TEST ONLY linked syscall/region delegates, separate JVM per mode. Public37 scratch/Java fixture, no real kernel entropy in these cases. Blocked case holds inside real JNI copy callback after actual32byte Java copy, validates attached original/worker-return/pending-private-ACK/fresh-auth denial then actualreturn/wipe/terminal. Not Android/ART/OEM/GC/JIT or dead-stack wipe proof.','inputs':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in [source,base/'entropy_fault_shim.c',base/'StagingEntropyFaultHarness.java',workspace/'staging-key-frozen-inputs.json']},'librarySHA256':hashlib.sha256((out/'libpg_staging_entropy.so').read_bytes()).hexdigest(),'classpathSHA256':hashlib.sha256(cp.encode()).hexdigest()},indent=2)+'\n');print('Actual10cases passed',flush=True)
