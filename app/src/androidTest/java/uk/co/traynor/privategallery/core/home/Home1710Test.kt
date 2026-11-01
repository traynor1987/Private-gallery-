package uk.co.traynor.privategallery.core.home

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession

class Home1710Test {
 @Test fun ownerIsolationEncryptedRoundTripRevocationAndStartupMigration() {
  val base=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(base.cacheDir,"home1710-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():Context=this
  }
  val valid=AtomicBoolean(true);val key=ByteArray(32){42};val identity=Any()
  val primary=EvidenceAccess.primary(context,identity,{identity},{valid.get()},{key.copyOf()},Any())
  val hidden=HiddenSession{android.os.SystemClock.elapsedRealtime()}
  assertTrue(hidden.complete(hidden.beginAuthentication(),ByteArray(32){73}))
  val secret=EvidenceAccess.hidden(context,hidden){valid.get()}
  try {
   val p=HomePreferencesStore(primary,true);val h=HomePreferencesStore(secret,false)
   assertFalse(p.read().startupHome);assertTrue(h.read().startupHome)
   val changed=HomePreferences(layout=HomeLayout.defaults().hide(HomeLayout.Shortcut.BROWSER).pin(HomeLayout.Shortcut.CAMERA),compact=true,recent=listOf(HomeLayout.Shortcut.EVIDENCE_LOCKER))
   p.write(changed);assertEquals(changed,p.read());assertEquals(HomeLayout.defaults(),h.read().layout)
   h.write(HomePreferences(layout=HomeLayout.defaults().move(HomeLayout.Shortcut.SETTINGS,0)))
   assertEquals(changed,p.read());assertEquals(HomeLayout.Shortcut.SETTINGS,h.read().layout.visible().first())
   root.walkTopDown().filter{it.isFile}.forEach {file->val data=file.readBytes();try{val raw=String(data,Charsets.ISO_8859_1);assertFalse(raw.contains("EVIDENCE_LOCKER"));assertFalse(raw.contains("v1|GALLERY"))}finally{data.fill(0)}}
   hidden.revokeOperations()
   try{h.read();fail("stale Hidden capability accepted")}catch(expected:Exception){}
   assertEquals(changed,p.read())
   valid.set(false)
   try{p.write(HomePreferences());fail("revoked save accepted")}catch(expected:Exception){}
   try{p.read();fail("revoked read accepted")}catch(expected:Exception){}
  }finally{primary.close();secret.close();hidden.lock();key.fill(0);root.deleteRecursively()}
 }
}
