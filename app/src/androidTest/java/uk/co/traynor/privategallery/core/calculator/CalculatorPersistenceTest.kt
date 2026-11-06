package uk.co.traynor.privategallery.core.calculator
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.home2.CalculatorData

class CalculatorPersistenceTest {
 @Test fun historyMemoryPreferencesAreEncryptedIsolatedPortableAndRetired(){
  val base=InstrumentationRegistry.getInstrumentation().targetContext;val root=File(base.cacheDir,"calculator-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"hidden").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val identity=Any();val primary=EvidenceAccess.primary(context,identity,{identity},{true},{ByteArray(32){31}},Any())
  val session=HiddenSession{android.os.SystemClock.elapsedRealtime()};check(session.complete(session.beginAuthentication(),ByteArray(32){73}));val hidden=EvidenceAccess.hidden(context,session){true}
  try{
   SpaceContext(context,primary).use{p->SpaceContext(context,hidden).use{h->
    val a=CalculatorData(p);val b=CalculatorData(h)
    for((expression,expected) in listOf("-(1)+sqrt(4)" to "1","-(1)+(2)" to "1","-((2+3)*4)" to "-20","2+(3*(-4))" to "-10")){
     a.input(expression);a.sign();assertTrue(a.error,a.evaluate());assertEquals(0,a.result!!.compareTo(java.math.BigDecimal(expected).negate()))
     a.sign();assertTrue(a.error,a.evaluate());assertEquals(0,a.result!!.compareTo(java.math.BigDecimal(expected)))
    }
    a.clearHistory()
    a.input("0.1+0.2");assertTrue(a.evaluate());a.memory("MS");a.settings(scientific=true)
    b.input("987654+123456");assertTrue(b.evaluate());b.memory("MS");b.settings(radians=true)
    assertEquals("0.3",a.history.single().result());assertEquals("1111110",b.history.single().result())
    val before=p.backupValues();p.restoreBackupValues(before);before.values.forEach{it.fill(0)}
    val reopened=CalculatorData(p);assertEquals("0.3",reopened.memory!!.toPlainString());assertTrue(reopened.scientific);assertFalse(reopened.radians);assertEquals(1,reopened.history.size)
    val hiddenAgain=CalculatorData(h);assertEquals("1111110",hiddenAgain.history.single().result());assertTrue(hiddenAgain.radians)
    root.walkTopDown().filter{it.isFile}.forEach{file->val raw=file.readBytes().toString(Charsets.ISO_8859_1);assertFalse(raw.contains("987654+123456"));assertFalse(raw.contains("0.1+0.2"))}
    reopened.clearHistory();assertTrue(CalculatorData(p).history.isEmpty());reopened.input("2+2");assertTrue(reopened.evaluate());reopened.settings(historyEnabled=false);reopened.input("4+4");assertTrue(reopened.evaluate());assertTrue(CalculatorData(p).history.isEmpty());assertFalse(CalculatorData(p).historyEnabled)
    val ciphertext=File(primary.root,"home2").listFiles()!!.associate{it.name to it.readBytes()};primary.close();a.input("secret");a.memory("MC");a.clearHistory();assertFalse(a.evaluate());assertFalse(a.current());ciphertext.forEach{(name,bytes)->assertArrayEquals(bytes,File(File(primary.root,"home2"),name).readBytes())}
   }}
  }finally{primary.close();hidden.close();session.lock();root.deleteRecursively()}
 }
}
