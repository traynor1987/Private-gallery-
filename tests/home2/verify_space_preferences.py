from pathlib import Path
import subprocess, tempfile, os, shutil, re, xml.etree.ElementTree as ET
"""Focused host checks using real SpaceContext/VaultCipher, minimal Android and lease fixtures.

The native instrumentation tests exercise EvidenceAccess on device. This runner checks
the adapter contract without a device. Requires JDK 17 and cached Kotlin compiler jars.
Set JAVA_HOME and GRADLE_USER_HOME when those tools are not in their usual locations.
"""
root=Path(__file__).resolve().parents[2]
work=Path(tempfile.mkdtemp(prefix='space-preferences-'));print('Fixture directory:',work); src=work/'src'; out=work/'classes'; src.mkdir(exist_ok=True);out.mkdir(exist_ok=True)
java={
'android/content/Context.java':"""package android.content; public abstract class Context { public static final int MODE_PRIVATE=0; public Context getApplicationContext(){return this;} public abstract SharedPreferences getSharedPreferences(String name,int mode); }""",
'android/content/ContextWrapper.java':"""package android.content; public class ContextWrapper extends Context { public ContextWrapper(Context base){} public SharedPreferences getSharedPreferences(String n,int m){throw new AssertionError("Framework preferences touched");} }""",
'android/content/SharedPreferences.java':"""package android.content; import java.util.*; public interface SharedPreferences { Map<String,?> getAll(); String getString(String k,String d); Set<String> getStringSet(String k,Set<String>d); int getInt(String k,int d);long getLong(String k,long d);float getFloat(String k,float d);boolean getBoolean(String k,boolean d);boolean contains(String k);Editor edit();void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);interface Editor {Editor putString(String k,String v);Editor putStringSet(String k,Set<String>v);Editor putInt(String k,int v);Editor putLong(String k,long v);Editor putFloat(String k,float v);Editor putBoolean(String k,boolean v);Editor remove(String k);Editor clear();boolean commit();void apply();} interface OnSharedPreferenceChangeListener{void onSharedPreferenceChanged(SharedPreferences p,String k);} }""",
'android/os/Looper.java':"""package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}""",
'android/os/Handler.java':"""package android.os; public class Handler {public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;}}""",
'uk/co/traynor/privategallery/core/evidence/EvidenceEncryptedStore.java':"""package uk.co.traynor.privategallery.core.evidence;public class EvidenceEncryptedStore {public interface LeaseFactory{KeyLease acquire();} public interface KeyLease extends AutoCloseable{byte[] key();boolean current();void commit(CommitAction a)throws Exception;void close();}public interface CommitAction{void run()throws Exception;}}""",
'uk/co/traynor/privategallery/core/evidence/EvidenceAccess.java':"""package uk.co.traynor.privategallery.core.evidence;import java.io.*;import java.util.*;public class EvidenceAccess {public enum Owner{PRIMARY,HIDDEN} public final File root;public final Owner owner;public final EvidenceEncryptedStore.LeaseFactory leases;public boolean live=true;public boolean retireOnCommit;public int acquired,closed;private final byte[] key=new byte[32];private final List<Runnable> callbacks=new ArrayList<>();public EvidenceAccess(File root,Owner owner){this.root=root;this.owner=owner;Arrays.fill(key,(byte)42);leases=()->{checkCurrent();acquired++;return new EvidenceEncryptedStore.KeyLease(){byte[] owned=key.clone();boolean ended;public byte[] key(){checkCurrent();return owned;}public boolean current(){return live&&!ended;}public void commit(EvidenceEncryptedStore.CommitAction a)throws Exception{if(retireOnCommit)retire();checkCurrent();if(ended)throw new IllegalStateException();a.run();checkCurrent();}public void close(){if(!ended){ended=true;closed++;Arrays.fill(owned,(byte)0);}}};};}public boolean current(){return live;}public void checkCurrent(){if(!live)throw new IllegalStateException();}public boolean publishIfCurrent(Runnable action){if(!live)return false;action.run();return true;}public AutoCloseable onRetire(Runnable action){callbacks.add(action);return ()->callbacks.remove(action);}public void retire(){live=false;for(Runnable c:new ArrayList<>(callbacks))c.run();}}"""
}
files=[]
for name,body in java.items():
 p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body);files.append(str(p))
jdk=Path(os.environ.get('JAVA_HOME','/tmp/nova-widget-tools/jdk-17.0.21+9'))/'bin'
subprocess.run([str(jdk/'javac'),'-d',str(out),*files,*[str(p) for p in (root/'app/src/main/java/uk/co/traynor/privategallery/core/arcade').glob('*.java')],str(root/'app/src/main/java/uk/co/traynor/privategallery/home2/WidgetRules.java')],check=True)
cache=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/modules-2/files-2.1'
if not cache.is_dir(): cache=Path('/tmp/nova-widget-tools/cache/caches/modules-2/files-2.1')
# Only the compiler artifact and its declared runtime dependencies belong here.
# Gradle plugin jars also define K2JVMCompiler; a cache-wide classpath loads the wrong class.
def version_key(value): return tuple(map(int,re.findall(r"\d+",value)))
def artifact(group,name,version=None):
 module=cache/group/name
 versions=[v for v in module.iterdir() if v.is_dir()] if module.is_dir() else []
 selected=module/version if version and (module/version).is_dir() else max(versions,key=lambda v:version_key(v.name))
 matches=list(selected.glob(name+"-"+selected.name+".jar"))+list(selected.glob("*/"+name+"-"+selected.name+".jar"))
 assert len(matches)==1,(group,name,version,matches)
 return matches[0]
compiler_jar=artifact('org.jetbrains.kotlin','kotlin-compiler-embeddable')
pom=next(compiler_jar.parent.parent.glob('*/*.pom'))
model=ET.parse(pom).getroot()
compiler_jars=[compiler_jar]
for dependency in model.findall('{*}dependencies/{*}dependency'):
 group=dependency.find('{*}groupId').text;name=dependency.find('{*}artifactId').text;version=dependency.find('{*}version').text
 compiler_jars.append(artifact(group,name,version))
compiler_jars.append(artifact('org.jetbrains','annotations'))
compiler=':'.join(str(x) for x in compiler_jars)
stdlib=next(x for x in compiler_jars if x.name.startswith('kotlin-stdlib-'))
json_jar=artifact('org.json','json','20240303')
classpath=str(out)+':'+str(stdlib)+':'+str(json_jar)
compose=work/'ComposeState.kt'
compose.write_text("""package androidx.compose.runtime
import kotlin.reflect.KProperty
class MutableState<T>(var value:T)
fun <T> mutableStateOf(value:T)=MutableState(value)
fun mutableIntStateOf(value:Int)=MutableState(value)
fun mutableLongStateOf(value:Long)=MutableState(value)
operator fun <T> MutableState<T>.getValue(owner:Any?,property:KProperty<*>):T=value
operator fun <T> MutableState<T>.setValue(owner:Any?,property:KProperty<*>,next:T){value=next}
""")
kt=work/'Checks.kt'
kt.write_text(r"""package uk.co.traynor.privategallery.core.home2
import android.content.*
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
fun main(args:Array<String>){
 val root=File(args[0]).apply{deleteRecursively();mkdirs()}
 val base=ContextWrapper(null)
 val access=EvidenceAccess(File(root,"primary"),EvidenceAccess.Owner.PRIMARY)
 val space=SpaceContext(base,access)
 fun refused(action:()->Unit){var denied=false;try{action()}catch(_:IllegalStateException){denied=true};check(denied){"retired operation accepted"}}
 check(space.applicationContext===space);check(space.spaceId=="primary");var published=false;check(space.publishIfCurrent{published=true});check(published)
 val p=space.getSharedPreferences("../../layout",0)
 val source=mutableSetOf("secret1","secret2")
 check(p.edit().putString("name","PRIVATE_NAME").putStringSet("set",source).putInt("int",7).putLong("long",Long.MAX_VALUE).putFloat("float",1.25f).putBoolean("bool",true).commit())
 source.clear();check(p.getStringSet("set",null)!!.size==2)
 p.getStringSet("set",null)!!.clear();check(p.getStringSet("set",null)!!.size==2)
 (p.all["set"] as MutableSet<*>).clear();check(p.getStringSet("set",null)!!.size==2)
 check(p.getInt("int",0)==7);check(p.getLong("long",0)==Long.MAX_VALUE);check(p.getFloat("float",0f)==1.25f);check(p.getBoolean("bool",false))
 var notices=0;val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,_->notices++};p.registerOnSharedPreferenceChangeListener(listener)
 val first=p.edit().putString("early","one");p.edit().putString("late","two").apply();check(first.commit());check(p.getString("late",null)=="two");check(notices==2)
 p.unregisterOnSharedPreferenceChangeListener(listener)
 p.edit().clear().putString("only","survivor").apply();check(p.all==mapOf("only" to "survivor"))
 val reopen=SpaceContext(base,access);check(reopen.getSharedPreferences("../../layout",0).getString("only",null)=="survivor");reopen.close();check(space.current())
 val wallpaper="PRIVATE_WALLPAPER".encodeToByteArray();val id=space.writeWallpaper(wallpaper);check(wallpaper.contentEquals(space.readWallpaper(id)));check(space.readWallpaper(UUID.randomUUID().toString())==null)
 root.walkTopDown().filter{it.isFile}.forEach{val raw=it.readBytes().toString(Charsets.ISO_8859_1);check(!raw.contains("PRIVATE_NAME"));check(!raw.contains("survivor"));check(!raw.contains("PRIVATE_WALLPAPER"))}
 val discarded=space.writeWallpaper("old".encodeToByteArray());space.pruneWallpapers(setOf(id));check(space.readWallpaper(discarded)==null);check(wallpaper.contentEquals(space.readWallpaper(id)))
 val file=File(access.root,"home2").listFiles()!!.single{it.name.startsWith("prefs-")};val valid=file.readBytes()
 val hiddenAccess=EvidenceAccess(File(root,"hidden"),EvidenceAccess.Owner.HIDDEN);val hidden=SpaceContext(base,hiddenAccess);val hp=hidden.getSharedPreferences("../../layout",0);hp.edit().putString("owner","hidden").apply();val hfile=File(hiddenAccess.root,"home2").listFiles()!!.single();hfile.writeBytes(valid)
 try{hp.all;error("Foreign owner accepted")}catch(_:javax.crypto.AEADBadTagException){}
 val foreignName=space.getSharedPreferences("other-name",0);foreignName.edit().putBoolean("marker",true).apply();val other=File(access.root,"home2").listFiles()!!.single{it.name.startsWith("prefs-")&&it!=file};other.writeBytes(valid)
 try{foreignName.all;error("Foreign name accepted")}catch(_:javax.crypto.AEADBadTagException){}
 file.writeBytes(valid.clone().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()});val corrupt=file.readBytes()
 try{p.edit().clear().commit();error("Corruption replaced")}catch(_:javax.crypto.AEADBadTagException){};check(corrupt.contentEquals(file.readBytes()));file.writeBytes(valid)
 val editor=p.edit().putString("only","stale");access.retireOnCommit=true;refused{editor.commit()};check(valid.contentEquals(file.readBytes()));check(File(access.root,"home2").listFiles()!!.none{it.name.startsWith("space-")})
 published=false;check(!space.publishIfCurrent{published=true});check(!published)
 refused{p.all};refused{editor.commit()};check(!space.current());check(access.acquired==access.closed)
 val next=EvidenceAccess(access.root,EvidenceAccess.Owner.PRIMARY);val nextSpace=SpaceContext(base,next);check(nextSpace.getSharedPreferences("../../layout",0).getString("only",null)=="survivor");refused{p.all}
 val np=nextSpace.getSharedPreferences("../../layout",0);val ne=np.edit();nextSpace.close();refused{np.all};refused{ne.putString("fail","closed")}
 hidden.close();space.close();check(next.acquired==next.closed)
 val backupAccess=EvidenceAccess(File(root,"backup-source"),EvidenceAccess.Owner.PRIMARY);val backupSpace=SpaceContext(base,backupAccess)
 backupSpace.getSharedPreferences("nova_mind_v1",0).edit().putString("primary","[]").putString("hidden","FOREIGN_NOTE").commit()
 backupSpace.getSharedPreferences("nova_widgets_v1",0).edit().putString("layout:primary","[{\"id\":\"slot\",\"page\":0,\"x\":0,\"y\":0,\"w\":2,\"h\":2,\"active\":0,\"items\":[{\"id\":\"note\",\"type\":\"note\",\"config\":\"PORTABLE_NOTE\"}]}]").commit()
 backupSpace.getSharedPreferences("credentials",0).edit().putString("token","NEVER_EXPORT").commit()
 val tiles=uk.co.traynor.privategallery.core.arcade.Game2048.create(71).move(0).encode()
 val snake=uk.co.traynor.privategallery.core.arcade.SnakeGame.create(72).encode()
 val arcade=uk.co.traynor.privategallery.home2.ArcadeData(backupSpace)
 arcade.save(uk.co.traynor.privategallery.core.arcade.SudokuGame.create(3,73))
 val editable=(0..80).filter{arcade.sudoku!!.given(it)==0};val finalCell=editable.last()
 editable.dropLast(1).forEach{i->arcade.save(arcade.sudoku!!.enter(i,arcade.sudoku!!.solution()[i],false))};arcade.save(arcade.sudoku!!.hint(finalCell))
 check(arcade.sudokuWins==1&&"sudoku-no-hints" !in arcade.achievements)
 repeat(3){arcade.save(arcade.sudoku!!.undo());arcade.save(arcade.sudoku!!.enter(finalCell,arcade.sudoku!!.solution()[finalCell],false));check(arcade.sudokuWins==1&&"sudoku-no-hints" !in arcade.achievements)}
 val credited=arcade.sudoku!!;arcade.save(credited.undo());val sudoku=arcade.sudoku!!.encode();check(credited.completedOnce)
 backupSpace.getSharedPreferences("nova_arcade_v1",0).edit().putString("save:2048",tiles).putString("save:snake",snake).putString("save:sudoku",sudoku).putString("recent","sudoku,2048,snake").putBoolean("sound",true).commit()
 val values=backupSpace.backupValues();check(values.keys.none{it.contains("credentials")});check(values.values.none{it.toString(Charsets.ISO_8859_1).contains("FOREIGN_NOTE")});check(values.values.any{it.toString(Charsets.ISO_8859_1).contains("PORTABLE_NOTE")})
 val missingWallpaper=UUID.randomUUID().toString();backupSpace.getSharedPreferences("nova_preview_v1",0).edit().putString("wallpaper-applied-v1","{\"image\":\"$missingWallpaper\"}").commit()
 var missingRefused=false;try{backupSpace.backupValues()}catch(_:IllegalStateException){missingRefused=true};check(missingRefused){"Missing referenced wallpaper was silently skipped"}
 backupSpace.getSharedPreferences("nova_preview_v1",0).edit().remove("wallpaper-applied-v1").commit()
 val restoreAccess=EvidenceAccess(File(root,"backup-destination"),EvidenceAccess.Owner.PRIMARY);val restored=SpaceContext(base,restoreAccess)
 restored.getSharedPreferences("credentials",0).edit().putString("token","existing-token").commit()
 restored.restoreBackupValues(values);check(restored.getSharedPreferences("nova_mind_v1",0).getString("primary",null)=="[]");check(restored.getSharedPreferences("credentials",0).getString("token",null)=="existing-token")
 check(restored.getSharedPreferences("nova_widgets_v1",0).getString("layout:primary",null)!!.contains("PORTABLE_NOTE"))
 val bad=values.mapValues{it.value.copyOf()};bad.values.first()[0]=99
 try{restored.restoreBackupValues(bad);error("Invalid settings accepted")}catch(_:IllegalArgumentException){}
 check(restored.getSharedPreferences("nova_mind_v1",0).getString("primary",null)=="[]")
 check(restored.getSharedPreferences("nova_arcade_v1",0).getString("save:2048",null)==tiles)
 check(restored.getSharedPreferences("nova_arcade_v1",0).getString("save:snake",null)==snake)
 check(restored.getSharedPreferences("nova_arcade_v1",0).getString("save:sudoku",null)==sudoku)
 val restoredSudoku=uk.co.traynor.privategallery.core.arcade.SudokuGame.decode(restored.getSharedPreferences("nova_arcade_v1",0).getString("save:sudoku",null)!!);check(restoredSudoku.completedOnce&&!restoredSudoku.complete())
 val restoredModel=uk.co.traynor.privategallery.home2.ArcadeData(restored);restoredModel.save(restoredModel.sudoku!!.pause(false));restoredModel.save(restoredModel.sudoku!!.enter(finalCell,restoredModel.sudoku!!.solution()[finalCell],false));check(restoredModel.sudokuWins==1)
 val finishedReopen=uk.co.traynor.privategallery.home2.ArcadeData(restored);check(finishedReopen.sudoku!!.complete()&&!finishedReopen.sudoku!!.paused)
 restoredModel.start("sudoku",0);check(!restoredModel.sudoku!!.completedOnce);for(i in 0..80)if(restoredModel.sudoku!!.given(i)==0)restoredModel.save(restoredModel.sudoku!!.enter(i,restoredModel.sudoku!!.solution()[i],false));check(restoredModel.sudokuWins==2&&"sudoku-no-hints" in restoredModel.achievements)
 check(restored.getSharedPreferences("nova_arcade_v1",0).getBoolean("sound",false))
 // Known-key malformed data must fail before any encrypted staging or swap.
 fun stringRecord(key:String,value:String):ByteArray {val out=java.io.ByteArrayOutputStream();java.io.DataOutputStream(out).use{d->fun text(s:String){val b=s.toByteArray();d.writeInt(b.size);d.write(b)};d.writeInt(1);d.writeInt(1);text(key);d.writeByte(1);text(value)};return out.toByteArray()}
 val baseline=File(restoreAccess.root,"home2").listFiles()!!.associate{it.name to it.readBytes()}
 val invalidCredit=java.util.Base64.getDecoder().decode(credited.encode()).also{it[it.lastIndex]=0}
 val malformed=listOf("nova_arcade_v1" to stringRecord("save:sudoku",java.util.Base64.getEncoder().encodeToString(invalidCredit)),"nova_arcade_v1" to stringRecord("sound","true"),"nova_arcade_v1" to stringRecord("save:2048","bad"),"nova_arcade_v1" to stringRecord("save:snake","bad"),"nova_arcade_v1" to stringRecord("save:sudoku","bad"),"nova_widgets_v1" to stringRecord("reduced","true"),"nova_preview_v1" to stringRecord("state","{\"schema\":1,\"current\":\"primary\",\"theme\":\"dark\",\"profile\":{\"installed\":[],\"pages\":[],\"dock\":[],\"folders\":[],\"wallpaper\":{}}}"))
 malformed.forEach{(name,data)->try{restored.restoreBackupValues(mapOf("preferences:$name" to data));error("Malformed schema accepted")}catch(_:IllegalArgumentException){};val after=File(restoreAccess.root,"home2").listFiles()!!.associate{it.name to it.readBytes()};check(after.keys==baseline.keys&&after.all{(name,bytes)->bytes.contentEquals(baseline[name])});data.fill(0)}
 restoreAccess.retireOnCommit=true;refused{restored.restoreBackupValues(values)}
 val reopened=SpaceContext(base,EvidenceAccess(restoreAccess.root,EvidenceAccess.Owner.PRIMARY));check(reopened.getSharedPreferences("nova_mind_v1",0).getString("primary",null)=="[]");reopened.close()
 val directory=File(restoreAccess.root,"home2");check(directory.renameTo(File(restoreAccess.root,"home2-backup-old")))
 val recovered=SpaceContext(base,EvidenceAccess(restoreAccess.root,EvidenceAccess.Owner.PRIMARY));check(recovered.getSharedPreferences("nova_mind_v1",0).getString("primary",null)=="[]");recovered.close();backupSpace.close();values.values.forEach{it.fill(0)};bad.values.forEach{it.fill(0)}
 println("PASS: portable allowlist, owner-only notes, credential exclusion, atomic settings restore, validation failure preservation, revocation rollback, interrupted directory recovery")
 root.deleteRecursively();println("PASS: encrypted types, copies, merge, listeners, owner/name AAD, corruption preservation, wallpaper, commit revocation, stale handles, lease closure")
}
""")
production=[root/'app/src/main/java/uk/co/traynor/privategallery/home2/ArcadeData.kt',root/'app/src/main/java/uk/co/traynor/privategallery/core/backup/PortablePreferenceSchema.kt',
    root/'app/src/main/java/uk/co/traynor/privategallery/core/backup/SettingsDirectoryTransaction.kt',root/'app/src/main/java/uk/co/traynor/privategallery/core/backup/BackupPolicy.kt',root/'app/src/main/java/uk/co/traynor/privategallery/core/backup/PortableWidgets.kt',root/'app/src/main/java/uk/co/traynor/privategallery/core/crypto/VaultCipher.kt',root/'app/src/main/java/uk/co/traynor/privategallery/core/home2/SpacePreferences.kt']
args=[str(jdk/'java'),'-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',classpath,'-d',str(out),str(compose),str(kt),*[str(x) for x in production]]
subprocess.run(args,check=True)
subprocess.run([str(jdk/'java'),'-cp',classpath,'uk.co.traynor.privategallery.core.home2.ChecksKt',str(work/'data')],check=True)
shutil.rmtree(work)
