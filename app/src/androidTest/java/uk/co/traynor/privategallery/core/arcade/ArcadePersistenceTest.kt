package uk.co.traynor.privategallery.core.arcade
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
import uk.co.traynor.privategallery.home2.ArcadeData

class ArcadePersistenceTest {
 @Test fun bothSpacesKeepIndependentEncryptedGamesRecordsAndSettingsAfterReopen(){
  val base=InstrumentationRegistry.getInstrumentation().targetContext;val root=File(base.cacheDir,"arcade-${UUID.randomUUID()}")
  val context=object:ContextWrapper(base){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"hidden").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val identity=Any();val primary=EvidenceAccess.primary(context,identity,{identity},{true},{ByteArray(32){31}},Any())
  val session=HiddenSession{android.os.SystemClock.elapsedRealtime()};check(session.complete(session.beginAuthentication(),ByteArray(32){73}));val hidden=EvidenceAccess.hidden(context,session){true}
  try{
   val p=SpaceContext(context,primary);val h=SpaceContext(context,hidden)
   val a=ArcadeData(p);val b=ArcadeData(h)
   a.save(Game2048.create(11).move(0));a.save(SudokuGame.create(3,12));a.save(SnakeGame.create(13).pause(false).turn(1));a.visit("2048");a.settings(sound=true,speed=5)
   b.save(Game2048.create(99).move(1));b.visit("snake");b.settings(haptics=true,speed=1)
   val editable=(0..80).filter{a.sudoku!!.given(it)==0};val last=editable.last()
   editable.dropLast(1).forEach{i->a.save(a.sudoku!!.enter(i,a.sudoku!!.solution()[i],false))};a.save(a.sudoku!!.hint(last))
   assertEquals(1,a.sudokuWins);assertFalse("sudoku-no-hints" in a.achievements)
   repeat(3){a.save(a.sudoku!!.undo());a.save(a.sudoku!!.enter(last,a.sudoku!!.solution()[last],false));assertEquals(1,a.sudokuWins);assertFalse("sudoku-no-hints" in a.achievements)}
   a.save(a.sudoku!!.undo())
   val backedUp=p.backupValues();p.restoreBackupValues(backedUp);backedUp.values.forEach{it.fill(0)}
   val reopenedCredit=ArcadeData(p);assertTrue(reopenedCredit.sudoku!!.completedOnce);reopenedCredit.save(reopenedCredit.sudoku!!.pause(false));reopenedCredit.save(reopenedCredit.sudoku!!.enter(last,reopenedCredit.sudoku!!.solution()[last],false));assertEquals(1,reopenedCredit.sudokuWins)
   assertFalse(ArcadeData(p).sudoku!!.paused);assertTrue(ArcadeData(p).sudoku!!.complete())
   reopenedCredit.start("sudoku",1);assertFalse(reopenedCredit.sudoku!!.completedOnce)
   for(i in 0..80)if(reopenedCredit.sudoku!!.given(i)==0)reopenedCredit.save(reopenedCredit.sudoku!!.enter(i,reopenedCredit.sudoku!!.solution()[i],false))
   assertEquals(2,reopenedCredit.sudokuWins);assertTrue("sudoku-no-hints" in reopenedCredit.achievements)
   val saved=a.tiles!!.encode();val foreign=b.tiles!!.encode();assertNotEquals(saved,foreign)
   SpaceContext(context,primary).use{val reopened=ArcadeData(it);assertEquals(saved,reopened.tiles!!.encode());assertEquals(listOf("2048"),reopened.recent);assertTrue(reopened.sound);assertEquals(5,reopened.speed);assertTrue(reopened.snake!!.paused);assertFalse(reopened.sudoku!!.paused);assertTrue(reopened.sudoku!!.complete())}
   SpaceContext(context,hidden).use{val reopened=ArcadeData(it);assertEquals(foreign,reopened.tiles!!.encode());assertEquals(listOf("snake"),reopened.recent);assertFalse(reopened.sound);assertTrue(reopened.haptics)}
   root.walkTopDown().filter{it.isFile}.forEach{assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains(saved))}
   val before=File(primary.root,"home2").listFiles()!!.associate{it.name to it.readBytes()};primary.close();a.save(Game2048.create(88));a.visit("snake");assertFalse(a.current());val after=File(primary.root,"home2").listFiles()!!.associate{it.name to it.readBytes()};assertEquals(before.keys,after.keys);before.forEach{(name,bytes)->assertArrayEquals(bytes,after[name])}
   p.close();h.close()
  }finally{primary.close();hidden.close();session.lock();root.deleteRecursively()}
 }
}
