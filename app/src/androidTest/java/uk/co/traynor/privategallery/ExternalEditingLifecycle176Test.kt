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
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a));val old=allowed(a,t)
   assertTrue(old());s.previewConcealmentEpoch++
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin",s.hidden.session)
   assertSame(t,ticket(a));assertFalse("Old IO cannot inherit the new presentation",old());assertTrue(allowed(a,t)())
   s.hidden.session.lock();assertFalse(allowedOrFalse(a,t))
  }}
 }
 private fun allowedOrFalse(a:MainActivity,t:Any)=runCatching {allowed(a,t)()}.getOrDefault(false)
 @Test fun hiddenResultNeverFallsBackToPrimaryAndRejectedBytesAreWiped() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9}));s.hidden.select()
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a))
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
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin");val t=checkNotNull(ticket(a))
   var deleteCalled=false;var protectedAtDelete=false
   val file=object:java.io.File(a.cacheDir,"external-edit/${java.util.UUID.randomUUID()}.png") {
    override fun delete():Boolean {deleteCalled=true;protectedAtDelete=a.privacyCoverVisible()&&!s.session.isUnlocked;return true}
   }
   t.javaClass.getDeclaredMethod("setFile",java.io.File::class.java).apply {isAccessible=true}.invoke(t,file)
   a.panicExit();assertNull(ticket(a));assertTrue(deleteCalled)
   assertTrue("Curtain and key revocation must precede external IO",protectedAtDelete)
  }}
 }
 private fun pending(t:Any){t.javaClass.getDeclaredMethod("setResumePending",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(t,true)}
 private fun resume(a:MainActivity,t:Any)=MainActivity::class.java.declaredMethods.single {it.name=="resumeExternalPresentation"}.apply {isAccessible=true}.invoke(a,t) as Boolean
 @Test fun explicitEditorReturnRenewsOnlyUiAndOldHiddenIoStaysRevoked() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9}));s.hidden.select()
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a));val old=allowed(a,t)
   s.hidden.session.onBackground(60_000);s.previewConcealmentEpoch++;s.hidden.session.onForeground();pending(t)
   assertTrue(resume(a,t));assertFalse(old());assertTrue(allowed(a,t)())
  }}
 }
 @Test fun expiredHiddenEditorReturnCannotResumeAgainstPrimaryAuthentication() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {a ->
   val s=state(a);assertTrue(s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9}));s.hidden.select()
   enterExternalEditingMediaFixture(a);a.openExternalEdit("synthetic-origin",s.hidden.session);val t=checkNotNull(ticket(a));pending(t)
   s.hidden.session.onBackground(0);s.hidden.session.onForeground();s.hidden.leave();s.key=ByteArray(32){7};s.session.unlock()
   assertFalse(resume(a,t));assertFalse(allowedOrFalse(a,t));assertSame(t,ticket(a))
   s.key?.fill(0);s.key=null;s.session.lock()
  }}
 }
 private fun setRoute(a:MainActivity,route:Route) {
  MainActivity::class.java.getDeclaredMethod("setRoute",Route::class.java).apply{isAccessible=true}.invoke(a,route)
 }
 private fun current(a:MainActivity,t:Any)=MainActivity::class.java.declaredMethods.single{it.name=="externalCurrent"}.apply{isAccessible=true}.invoke(a,t) as Boolean
 private fun invoke(a:MainActivity,name:String,vararg args:Any?):Any?=MainActivity::class.java.declaredMethods.single{it.name==name}.apply{isAccessible=true}.invoke(a,*args)
 /** Feed a real registered ActivityResult contract without opening an installed external app. */
 private fun result(a:MainActivity,registration:Int,uri:android.net.Uri) {
  val saved=android.os.Bundle();a.activityResultRegistry.onSaveInstanceState(saved)
  val key="activity_rq#"+registration
  val keys=checkNotNull(saved.getStringArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_KEYS"))
  val codes=checkNotNull(saved.getIntegerArrayList("KEY_COMPONENT_ACTIVITY_REGISTERED_RCS"))
  val index=keys.indexOf(key);check(index>=0){"External result registration missing"}
  saved.putStringArrayList("KEY_COMPONENT_ACTIVITY_LAUNCHED_KEYS",arrayListOf(key))
  a.activityResultRegistry.onRestoreInstanceState(saved)
  assertTrue(a.activityResultRegistry.dispatchResult(codes[index],android.app.Activity.RESULT_OK,android.content.Intent().setData(uri)))
 }
 @Test fun browser_and_home_suspend_external_ui_but_retain_the_original_editor_result_and_file() {
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{a->
   val s=state(a);s.key=ByteArray(32){7};s.session.unlock();enterExternalEditingMediaFixture(a)
   a.openExternalEdit("media-context-origin");val t=checkNotNull(ticket(a))
   val file=java.io.File(a.cacheDir,"external-edit/${java.util.UUID.randomUUID()}.png").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1,2,3))}
   t.javaClass.getDeclaredMethod("setFile",java.io.File::class.java).apply{isAccessible=true}.invoke(t,file)
   MainActivity::class.java.getDeclaredField("externalEditorOwner").apply{isAccessible=true}.set(a,t)
   val uri=android.net.Uri.parse("content://external-editor.test/edited.png")
   try {
    assertTrue(current(a,t));setRoute(a,Route.BROWSER)
    assertFalse(current(a,t));result(a,0,uri)
    assertSame(t,ticket(a));assertEquals(uri,t.javaClass.getDeclaredMethod("getResult").apply{isAccessible=true}.invoke(t))
    assertTrue("Ordinary navigation retains the handoff file",file.isFile)
    setRoute(a,Route.VAULT);s.homeVisible=true;assertFalse(current(a,t))
    invoke(a,"recoverExternalReceipt");assertSame(t,ticket(a));assertTrue(file.isFile)
    s.homeVisible=false;assertTrue(current(a,t))
    s.hidden.session.complete(s.hidden.session.beginAuthentication(),ByteArray(32){9});s.hidden.select();assertFalse(current(a,t))
   } finally {invoke(a,"clearExternalEdit");file.delete();s.hidden.leave();s.key?.fill(0);s.key=null;s.session.lock()}
  }}
 }
 @Test fun real_picker_contract_does_not_attach_a_stale_result_to_a_new_media_ticket() {
  ActivityScenario.launch(MainActivity::class.java).use{scenario->scenario.onActivity{a->
   val s=state(a);s.key=ByteArray(32){7};s.session.unlock();enterExternalEditingMediaFixture(a)
   try {
    a.openExternalEdit("first-media");val first=checkNotNull(ticket(a))
    MainActivity::class.java.getDeclaredField("externalPickerOwner").apply{isAccessible=true}.set(a,first)
    a.openExternalEdit("second-media");val second=checkNotNull(ticket(a));assertNotSame(first,second)
    result(a,1,android.net.Uri.parse("content://external-editor.test/stale.png"))
    assertNull(second.javaClass.getDeclaredMethod("getResult").apply{isAccessible=true}.invoke(second))
   } finally {invoke(a,"clearExternalEdit");s.key?.fill(0);s.key=null;s.session.lock()}
  }}
 }

}
