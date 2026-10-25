package uk.co.traynor.privategallery
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.Assert.*

/** No credentials/media created. Only disposable in-memory authorities. */
class ExternalEditingLifecycle176Test {
 private fun state(a:MainActivity)=ViewModelProvider(a)[ProtectedSessionState::class.java]
 private fun ticket(a:MainActivity)=MainActivity::class.java.getDeclaredMethod("getExternalTicket").apply {isAccessible=true}.invoke(a)
 @Suppress("UNCHECKED_CAST") private fun allowed(a:MainActivity,t:Any)=MainActivity::class.java.declaredMethods.single {it.name=="externalAuthority"}.apply {isAccessible=true}.invoke(a,t) as ()->Boolean
 @Test fun oldHiddenOperationCannotReviveAfterConcealmentAndExplicitReopen() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9}));s.hidden.select()
   a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a));val old=allowed(a,t)
   assertTrue(old());s.previewConcealmentEpoch++
   a.openExternalEdit("synthetic-origin",s.hidden.session)
   assertSame(t,ticket(a));assertFalse("Old IO cannot inherit the new presentation",old());assertTrue(allowed(a,t)())
   s.hidden.session.lock();assertFalse(allowedOrFalse(a,t))
  }}
 }
 private fun allowedOrFalse(a:MainActivity,t:Any)=runCatching {allowed(a,t)()}.getOrDefault(false)
 @Test fun hiddenResultNeverFallsBackToPrimaryAndRejectedBytesAreWiped() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9}));s.hidden.select()
   a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a))
   s.hidden.leave();s.key=ByteArray(32){7};s.session.unlock()
   val bytes=byteArrayOf(1,2,3);var callback=false
   val done:(Result<Unit>)->Unit={assertTrue(it.isFailure);callback=true}
   MainActivity::class.java.declaredMethods.single {it.name=="saveExternalResult"}.apply {isAccessible=true}.invoke(a,t,bytes,false,done)
   assertTrue(callback);assertTrue(bytes.all {it==0.toByte()});assertFalse(allowedOrFalse(a,t))
   s.key?.fill(0);s.key=null;s.session.lock()
  }}
 }
 @Test fun panicRevokesPendingExternalTicket() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);s.key=ByteArray(32){7};s.session.unlock()
   a.openExternalEdit("synthetic-origin");assertNotNull(ticket(a));a.panicExit();assertNull(ticket(a))
  }}
 }
}
