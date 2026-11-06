package uk.co.traynor.privategallery.core.arcade
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ArcadeRulesTest {
 @Test fun mergesOnlyOnceAndUndoReplaysRandomPlacement(){
  val cells=IntArray(16);cells[0]=2;cells[1]=2;cells[2]=4
  val game=Game2048(cells,0,71,null);val moved=game.move(0)
  assertEquals(4,moved.cell(0));assertEquals(4,moved.cell(1));assertEquals(4L,moved.score)
  assertArrayEquals(cells,moved.undo().cells());assertEquals(moved.encode(),moved.undo().move(0).encode())
  for(direction in 0..3){val pair=IntArray(16);pair[5]=2;pair[if(direction==0||direction==2)6 else 9]=2;assertEquals(4L,Game2048(pair,0,4,null).move(direction).score)}
 }
 @Test fun restoredMaximumCountersStayWithinTheirPortableSchemaAfterPlaying(){
  val board=IntArray(16);board[0]=2;board[1]=2
  assertEquals(Long.MAX_VALUE/2,Game2048(board,Long.MAX_VALUE/2,1,null).move(0).score)
  val game=SudokuGame.create(0,1);val raw=Base64.getDecoder().decode(game.encode());java.nio.ByteBuffer.wrap(raw).putInt(1330,1_000_000).putInt(1334,1_000_000)
  val restored=SudokuGame.decode(Base64.getEncoder().encodeToString(raw));val at=(0..80).first{restored.given(it)==0}
  assertEquals(1_000_000,restored.enter(at,restored.solution()[at]%9+1,false).mistakes);assertEquals(1_000_000,restored.hint(at).hints)
 }
 @Test fun seededGenerationAndEveryLevelHaveOneSolution(){
  for(level in 0..3)for(seed in 1L..8L){val game=SudokuGame.create(level,seed);assertEquals(1,SudokuSolver.count(game.givens(),2));assertTrue(SudokuSolver.valid(game.solution(),true));assertEquals(game.encode(),SudokuGame.create(level,seed).encode())}
  assertEquals(2,SudokuSolver.count(IntArray(81),2));assertEquals(0,SudokuSolver.count(IntArray(81){1},2))
 }
 @Test fun notesEraseHintUndoPauseAndValidCompletion(){
  var game=SudokuGame.create(2,78);val at=(0..80).first{game.given(it)==0}
  assertEquals(8,game.enter(at,3,true).note(at));assertEquals(0,game.enter(at,3,true).undo().note(at))
  assertEquals(1,game.enter(at,game.solution()[at]%9+1,false).mistakes)
  assertEquals(game.solution()[at],game.hint(at).cell(at));assertEquals(0,game.hint(at).enter(at,0,false).cell(at))
  assertEquals(game.pause(true).encode(),game.pause(true).tick().encode())
  for(i in 0..80)if(game.given(i)==0)game=game.enter(i,game.solution()[i],false)
  assertTrue(game.complete());assertFalse(game.undo().complete())
 }
 @Test fun completionCreditSurvivesUndoRecompletionSaveAndPauseButNewPuzzleResets(){
  var game=SudokuGame.create(1,101);var finalCell=0
  for(i in 0..80)if(game.given(i)==0){game=game.enter(i,game.solution()[i],false);finalCell=i}
  assertTrue(game.complete());assertTrue(game.completedOnce);assertFalse(game.pause(true).paused)
  val undone=SudokuGame.decode(game.undo().encode());assertFalse(undone.complete());assertTrue(undone.completedOnce)
  val completed=undone.enter(finalCell,undone.solution()[finalCell],false);assertTrue(completed.complete());assertTrue(completed.completedOnce)
  assertTrue(SudokuGame.decode(completed.encode()).completedOnce);assertFalse(SudokuGame.create(1,102).completedOnce)
  val missingCredit=Base64.getDecoder().decode(completed.encode());missingCredit[missingCredit.lastIndex]=0
  assertThrows(IllegalArgumentException::class.java){SudokuGame.decode(Base64.getEncoder().encodeToString(missingCredit))}
  missingCredit[missingCredit.lastIndex]=2
  assertThrows(IllegalArgumentException::class.java){SudokuGame.decode(Base64.getEncoder().encodeToString(missingCredit))}
 }
 @Test fun snakeQueueRejectsImmediateAndQueuedReversal(){
  val game=SnakeGame.create(88).pause(false)
  assertArrayEquals(intArrayOf(),game.turn(2).queue())
  val queued=game.turn(1).turn(3).turn(2).turn(0)
  assertArrayEquals(intArrayOf(1,2),queued.queue());assertEquals(1,queued.tick().direction);assertEquals(2,queued.tick().tick().direction)
  assertArrayEquals(intArrayOf(),queued.pause(true).queue());assertEquals(queued.pause(true).encode(),queued.pause(true).tick().encode())
  assertTrue(SnakeGame(intArrayOf(15,14,13),0,intArrayOf(),40,0,1,false,false).tick().over)
  assertFalse(SnakeGame(intArrayOf(17,18,34,33),2,intArrayOf(),80,1,1,false,false).turn(1).tick().over)
 }
 @Test fun completeSaveSchemaRejectsMalformedTypesBoardsAndTrailingBytes(){
  val t=Game2048.create(6).move(1);val s=SudokuGame.create(3,7).hint(0);val n=SnakeGame.create(8).pause(false).turn(1)
  val valid=mapOf("save:2048" to t.encode(),"save:sudoku" to s.encode(),"save:snake" to n.encode(),"sound" to true,"speed" to 3,"recent" to "snake,2048,sudoku","achievements" to setOf("snake-10"))
  ArcadePreferences.validate(valid)
  for((key,value) in listOf("sound" to "true","speed" to 6,"recent" to "snake,snake","achievements" to setOf("foreign"),"save:2048" to "bad","save:sudoku" to "bad","save:snake" to "bad"))assertThrows(IllegalArgumentException::class.java){ArcadePreferences.validate(valid+mapOf(key to value))}
  val encoded=Base64.getDecoder().decode(t.encode());assertThrows(IllegalArgumentException::class.java){Game2048.decode(Base64.getEncoder().encodeToString(encoded.copyOf(encoded.size+1)))}
  assertThrows(IllegalArgumentException::class.java){SnakeGame(intArrayOf(34,33,32),2,intArrayOf(),50,0,1,false,false)}
 }
}
