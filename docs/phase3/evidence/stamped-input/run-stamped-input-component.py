from pathlib import Path
import subprocess,sys,os,json,hashlib
b=Path(__file__).resolve().parent;r=b/'runtime-stamped-input';m=b/'runtime-direct/app/build/tmp/kotlin-classes/debug';t=b/'runtime-direct/app/build/tmp/kotlin-classes/debugUnitTest';cache=Path('/root/.gradle/caches/modules-2/files-2.1');out=b/('stamped-input-'+sys.argv[1]);out.mkdir(exist_ok=True)
def jar(group,artifact,version):
 paths=list((cache/group/artifact/version).glob('*/*.jar'));assert len(paths)==1,(group,artifact,version,paths);return paths[0]
stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.0.21');junit=jar('junit','junit','4.13.2');hamcrest=jar('org.hamcrest','hamcrest-core','1.3');annotations=list((cache/'org.jetbrains/annotations').glob('*/*/*.jar'))[-1];coroutines=sorted((cache/'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm').glob('*/*/*.jar'))[-1]
compiler=[jar('org.jetbrains.kotlin',a,'2.0.21') for a in ['kotlin-compiler-embeddable','kotlin-script-runtime','kotlin-daemon-embeddable']]+[stdlib,jar('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),jar('org.jetbrains.intellij.deps','trove4j','1.0.20200330'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.6.4'),annotations]
java=str(b/'toolchain/amazon-corretto-17.0.20.12.1-linux-x64/bin/java');cp=os.pathsep.join(map(str,[m,t,stdlib,junit,hamcrest,coroutines]));sources=[r/('app/src/main/java/uk/co/traynor/privategallery/core/security/'+n+'.kt') for n in ['ScopedIoGuard','ReleaseReservation']]+[r/'app/src/test/java/uk/co/traynor/privategallery/core/security/OwnedManifestInputTest.kt'];classes=out/'classes';classes.mkdir(exist_ok=True)
(out/'source-sha256.json').write_text(json.dumps({str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},indent=2)+'\n')
for p in sources:(out/p.name).write_bytes(p.read_bytes())
cmd=[java,'-cp',os.pathsep.join(map(str,compiler)),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-module-name','app_debug','-classpath',cp,'-Xfriend-paths='+str(m)+','+str(t),'-d',str(classes),*map(str,sources)]
a=subprocess.run(cmd);assert a.returncode==0,a.returncode
raise SystemExit(subprocess.call([java,'-cp',str(classes)+os.pathsep+cp,'org.junit.runner.JUnitCore','uk.co.traynor.privategallery.core.security.OwnedManifestInputTest']))
