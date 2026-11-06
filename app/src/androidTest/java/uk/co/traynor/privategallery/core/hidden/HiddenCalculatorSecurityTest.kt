package uk.co.traynor.privategallery.core.hidden
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.util.UUID
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore

class HiddenCalculatorSecurityTest {
 private fun fixture(block:(Context,File)->Unit){
  val base=InstrumentationRegistry.getInstrumentation().targetContext;val id="calculator-${UUID.randomUUID()}";val root=File(base.cacheDir,id)
  val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()};override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getPackageName()="${base.packageName}.$id";override fun getSharedPreferences(name:String,mode:Int)=base.getSharedPreferences("$id-$name",mode);override fun getApplicationContext():Context=this}
  try{block(context,root)}finally{root.deleteRecursively();base.deleteSharedPreferences("$id-vault-key-envelope");val keys=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};listOf("default","other").forEach{keys.deleteEntry("${context.packageName}.hidden.v1.$it")}}
 }
 @Test fun allComparisonPathsThrottleRecreatedStoresAndLeavePrimaryEnvelopesUnchanged()=fixture{context,root->
  val primary=PinVaultKeyStore(context);primary.create("123456".toCharArray()).fill(0)
  val store=HiddenCredentialStore(context);val key=store.create("728491".toCharArray());key.fill(0)
  val credential=File(context.noBackupFilesDir,"hidden-v1-default/credential").readBytes()
  repeat(5){assertFalse(HiddenCredentialStore(context).pinMatches("999999".toCharArray()))}
  val pin="728491".toCharArray();assertThrows(HiddenPinCooldown::class.java){HiddenCredentialStore(context).unlock(pin)};assertArrayEquals(CharArray(6),pin)
  assertThrows(HiddenPinCooldown::class.java){HiddenCredentialStore(context).unlockForComparison("728491".toCharArray())}
  assertThrows(HiddenPinCooldown::class.java){HiddenCredentialStore(context).pinMatches("728491".toCharArray())}
  val oldPrimary=context.getSharedPreferences("vault-key-envelope",0).all.toMap()
  assertThrows(HiddenPinCooldown::class.java){primary.changePin("123456".toCharArray(),"654321".toCharArray())}
  assertThrows(HiddenPinCooldown::class.java){primary.replacePinForRecoveredVault("654321".toCharArray(),ByteArray(32){7})}
  assertEquals(oldPrimary,context.getSharedPreferences("vault-key-envelope",0).all)
  assertArrayEquals(credential,File(context.noBackupFilesDir,"hidden-v1-default/credential").readBytes())
  val independent=HiddenCredentialStore(context,"other");independent.create("847291".toCharArray()).fill(0);assertNotNull(independent.unlockForComparison("847291".toCharArray()).also{it?.fill(0)})
  // Seed an already-expired policy through the real encrypted record, without sleeping or touching credentials.
  HiddenProtectedRecord(context,"default","attempts-v1").write(HiddenPinThrottle.empty().encode())
  assertNotNull(HiddenCredentialStore(context).unlockForComparison("728491".toCharArray()).also{it?.fill(0)})
  assertNull(HiddenCredentialStore(context).unlockForComparison("999999".toCharArray()))
  val before=File(context.noBackupFilesDir,"hidden-v1-default/credential").readBytes()
  KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("${context.packageName}.hidden.v1.default")}
  assertThrows(Exception::class.java){HiddenCredentialStore(context).unlockForComparison("728491".toCharArray())}
  assertArrayEquals(before,File(context.noBackupFilesDir,"hidden-v1-default/credential").readBytes());assertTrue(root.exists())
 }
 @Test fun encryptedCooldownSurvivesReopenCorruptionAndClockRollbackWithBoundedRecovery()=fixture{context,_->
  HiddenCredentialStore(context).create("728491".toCharArray()).fill(0)
  var now=HiddenAttemptTime(100,1000,1)
  repeat(5){HiddenPinAttemptStore(context,"default"){now}.reserve()}
  assertThrows(HiddenPinCooldown::class.java){HiddenPinAttemptStore(context,"default"){now}.reserve()}
  now=HiddenAttemptTime(30100,31000,1);HiddenPinAttemptStore(context,"default"){now}.reserve()
  val next=assertThrows(HiddenPinCooldown::class.java){HiddenPinAttemptStore(context,"default"){now}.reserve()};assertEquals(60000,next.remainingMillis)
  now=HiddenAttemptTime(10,1,2);val rollback=assertThrows(HiddenPinCooldown::class.java){HiddenPinAttemptStore(context,"default"){now}.reserve()};assertTrue(rollback.remainingMillis<=300000)
  now=now.copy(elapsed=300010);HiddenPinAttemptStore(context,"default"){now}.reserve()
  val metadata=File(context.noBackupFilesDir,"hidden-v1-default/attempts-v1");metadata.writeBytes(byteArrayOf(1,2,3))
  val damaged=assertThrows(HiddenPinCooldown::class.java){HiddenPinAttemptStore(context,"default"){now}.reserve()};assertEquals(300000,damaged.remainingMillis)
  now=now.copy(elapsed=now.elapsed+300000);HiddenPinAttemptStore(context,"default"){now}.reserve();HiddenPinAttemptStore(context,"default"){now}.succeeded()
  HiddenPinAttemptStore(context,"default"){now}.reserve()
 }
 @Test fun concealedSelectionAlwaysRevokesRetainedAuthenticationAndNeedsFreshPin(){
  val hidden=uk.co.traynor.privategallery.ui.HiddenVaultState()
  check(hidden.session.complete(hidden.session.beginAuthentication(),ByteArray(32){67}));hidden.authenticated=true
  val old=hidden.session.authentication();hidden.selectLocked()
  assertTrue(hidden.selected);assertTrue(hidden.visible);assertFalse(hidden.authenticated);assertFalse(hidden.session.isUnlocked);assertFalse(hidden.session.isAuthenticationCurrent(old))
  val pending=hidden.session.beginAuthentication();hidden.lock();assertFalse(hidden.session.complete(pending,ByteArray(32){67}));assertFalse(hidden.session.isUnlocked)
  hidden.leave()
 }
 @Test fun concealedConfigurationDefaultsOffRequiresCurrentIndependentAuthenticationAndContainsNoPin()=fixture{context,root->
  val credentials=HiddenCredentialStore(context);val key=credentials.create("728491".toCharArray())
  val store=HiddenCalculatorEntryStore(context);assertFalse(store.enabled())
  val session=HiddenSession{android.os.SystemClock.elapsedRealtime()};check(session.complete(session.beginAuthentication(),key));val identity=session.authentication()
  store.setEnabled(true,session,identity){true};assertTrue(HiddenCalculatorEntryStore(context).enabled())
  session.lock();assertThrows(IllegalStateException::class.java){store.setEnabled(false,session,identity){true}};assertTrue(store.enabled())
  check(session.complete(session.beginAuthentication(),credentials.unlock("728491".toCharArray())))
  assertThrows(IllegalStateException::class.java){store.setEnabled(false,session,identity){true}}
  val fresh=session.authentication();assertThrows(IllegalStateException::class.java){store.setEnabled(false,session,fresh){false}};store.setEnabled(false,session,fresh){true};assertFalse(store.enabled());session.lock()
  root.walkTopDown().filter{it.isFile}.forEach{assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("728491"))}
 }
}
