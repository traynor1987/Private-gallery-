package uk.co.traynor.privategallery.core.backup
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import uk.co.traynor.privategallery.home2.*
import uk.co.traynor.privategallery.core.arcade.*
import uk.co.traynor.privategallery.core.calculator.CalculatorPreferences

class BackupAppsRoundtrip62Test {
 @Test fun actualArchiveReencryptsAllRegisteredGameAndCalculatorSchemasForEachSpace(){
  for(hidden in listOf(false,true)){val root=BackupFixture62.temporary("apps62")
   try{BackupFixture62(File(root,"source"),hidden,ByteArray(32){17}).use{s->BackupFixture62(File(root,"destination"),hidden,ByteArray(32){93}).use{d->
    val games=ArcadeData(s.coordinator.space);games.save(Game2048.create(11).move(0));games.save(SudokuGame.create(0,12));games.save(SnakeGame.create(13));games.visit("2048");games.settings(sound=true,speed=4)
    val calculator=CalculatorData(s.coordinator.space);calculator.input("0.1+0.2");assertTrue(calculator.evaluate());calculator.memory("MS");calculator.settings(scientific=true)
    d.coordinator.space.getSharedPreferences("concealed-entry-excluded",0).edit().putString("secret","destination-local").commit()
    val file=BackupFile62(File(root,"archive"));val key="all registered apps recovery".toCharArray();s.coordinator.export(file,key.copyOf())
    d.coordinator.preview(file,key.copyOf()).use{d.coordinator.restoreSettings(it)}
    assertEquals(s.coordinator.space.getSharedPreferences(ArcadePreferences.NAME,0).all,d.coordinator.space.getSharedPreferences(ArcadePreferences.NAME,0).all)
    val restored=ArcadeData(d.coordinator.space);assertEquals(games.tiles!!.encode(),restored.tiles!!.encode());assertEquals(games.sudoku!!.pause(true).encode(),restored.sudoku!!.encode());assertEquals(games.snake!!.encode(),restored.snake!!.encode());assertEquals(listOf("2048"),restored.recent);assertTrue(restored.sound);assertEquals(4,restored.speed)
    val calc=CalculatorData(d.coordinator.space);assertEquals("0.3",calc.memory!!.toPlainString());assertEquals("0.3",calc.history.single().result());assertTrue(calc.scientific)
    assertEquals("destination-local",d.coordinator.space.getSharedPreferences("concealed-entry-excluded",0).getString("secret",null))
    assertFalse(s.coordinator.space.backupValues().keys.any{it.contains("concealed")})
    val prefs=d.coordinator.space.getSharedPreferences(CalculatorPreferences.NAME,0);CalculatorPreferences.validate(prefs.all)
   }}}finally{root.deleteRecursively()}
  }
 }
}
