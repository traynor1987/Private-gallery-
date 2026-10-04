import pathlib,subprocess,json,hashlib,sys
base=pathlib.Path(__file__).resolve().parent;root=base/'runtime-bounded-staging-cipher';label=sys.argv[1];out=base/('cipher-'+label+'-classes');out.mkdir(exist_ok=True)
jars=[next(pathlib.Path('/root/.gradle/caches/modules-2/files-2.1').glob(p))for p in ['org.bouncycastle/bcprov-jdk18on/1.79/*/*.jar','junit/junit/4.13.2/*/*.jar','org.hamcrest/hamcrest-core/1.3/*/*.jar']];cp=':'.join(str(p)for p in jars);java=base/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64/bin'
files=[*sorted((root/'app/src/main/java/uk/co/traynor/privategallery/core/security/staging').glob('*.java')),root/'app/src/test/java/uk/co/traynor/privategallery/core/security/staging/Phase3FixedStagingCipherTest.java']
snapshot=base/('cipher-'+label+'-source');snapshot.mkdir(exist_ok=True)
for p in files:(snapshot/p.name).write_bytes(p.read_bytes())
results={'files':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest()for p in files},'qualification':'Standalone bounded primitive JVM; no full app, ART, original/descriptor/DIRECT proof'}
with(base/('cipher-'+label+'-compile.log')).open('w')as log:r=subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(out),*[str(p)for p in files]],stdout=log,stderr=subprocess.STDOUT)
results['compile']=r.returncode
if r.returncode==0:
 command=[str(java/'java'),'-Xmx1g','-cp',str(out)+':'+cp,'org.junit.runner.JUnitCore','uk.co.traynor.privategallery.core.security.staging.Phase3FixedStagingCipherTest']
 with(base/('cipher-'+label+'-tests.log')).open('w')as log:r=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT)
 results['testExit']=r.returncode;results['testLog']= (base/('cipher-'+label+'-tests.log')).read_text()
(base/('cipher-'+label+'-results.json')).write_text(json.dumps(results,indent=2)+'\n');print(results.get('testLog',results));raise SystemExit(r.returncode)
