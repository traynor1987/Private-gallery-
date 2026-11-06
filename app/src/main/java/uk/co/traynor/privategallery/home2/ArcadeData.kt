package uk.co.traynor.privategallery.home2

import android.content.Context
import androidx.compose.runtime.*
import uk.co.traynor.privategallery.core.arcade.*
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** Saves, records and settings never leave this captured Space capability. */
internal class ArcadeData(private val context:SpaceContext) {
 private val prefs=context.getSharedPreferences(ArcadePreferences.NAME,Context.MODE_PRIVATE)
 private val initial=prefs.all.also { ArcadePreferences.validate(it) }
 var tiles by mutableStateOf((initial["save:2048"] as? String)?.let(Game2048::decode));private set
 var sudoku by mutableStateOf((initial["save:sudoku"] as? String)?.let(SudokuGame::decode)?.pause(true));private set
 var snake by mutableStateOf((initial["save:snake"] as? String)?.let(SnakeGame::decode)?.pause(true));private set
 var sound by mutableStateOf(initial["sound"] as? Boolean?:false);private set
 var haptics by mutableStateOf(initial["haptics"] as? Boolean?:false);private set
 var speed by mutableIntStateOf(initial["speed"] as? Int?:3);private set
 var bestTiles by mutableLongStateOf(initial["best:2048"] as? Long?:0);private set
 var bestSnake by mutableIntStateOf(initial["best:snake"] as? Int?:0);private set
 var sudokuWins by mutableIntStateOf(initial["sudoku-wins"] as? Int?:0);private set
 var recent by mutableStateOf((initial["recent"] as? String).orEmpty().split(',').filter{it in ArcadePreferences.GAMES});private set
 var achievements by mutableStateOf((initial["achievements"] as? Set<*>)?.filterIsInstance<String>()?.toSet()?:emptySet());private set
 var error by mutableStateOf<String?>(null);private set
 fun current()=context.current()
 private fun write(action:android.content.SharedPreferences.Editor.()->Unit):Boolean {
  if(!current())return false
  return runCatching{check(prefs.edit().apply(action).commit());true}.getOrElse{error="Game could not be saved. Your last saved progress is preserved. Return to Arcade and try again.";false}
 }
 fun settings(sound:Boolean=this.sound,haptics:Boolean=this.haptics,speed:Int=this.speed){require(speed in 1..5);if(write{putBoolean("sound",sound);putBoolean("haptics",haptics);putInt("speed",speed)}){this.sound=sound;this.haptics=haptics;this.speed=speed;error=null}}
 fun visit(id:String){require(id in ArcadePreferences.GAMES);val next=(listOf(id)+recent).distinct().take(3);if(write{putString("recent",next.joinToString(","))})recent=next}
 fun save(value:Game2048){val best=maxOf(bestTiles,value.score);val awards=achievements+if(value.won())setOf("tile-2048")else emptySet();if(write{putString("save:2048",value.encode());putLong("best:2048",best);putStringSet("achievements",awards)}){tiles=value;bestTiles=best;achievements=awards;error=null}}
 fun save(value:SudokuGame){val newlyWon=value.complete()&&sudoku?.completedOnce!=true;val wins=(sudokuWins+if(newlyWon)1 else 0).coerceAtMost(1_000_000);val awards=achievements+(if(newlyWon)setOf("sudoku-solved")+(if(value.hints==0)setOf("sudoku-no-hints")else emptySet())else emptySet());if(write{putString("save:sudoku",value.encode());putInt("sudoku-wins",wins);putStringSet("achievements",awards)}){sudoku=value;sudokuWins=wins;achievements=awards;error=null}}
 fun save(value:SnakeGame){val best=maxOf(bestSnake,value.score);val awards=achievements+(if(best>=10)setOf("snake-10")else emptySet())+(if(best>=25)setOf("snake-25")else emptySet());if(write{putString("save:snake",value.encode());putInt("best:snake",best);putStringSet("achievements",awards)}){snake=value;bestSnake=best;achievements=awards;error=null}}
 fun pause(){if(!current())return;sudoku?.takeIf{!it.paused}?.let{save(it.pause(true))};snake?.takeIf{!it.paused}?.let{save(it.pause(true))}}
 fun start(id:String,level:Int=0){if(!current())return;val seed=System.nanoTime();when(id){"2048"->save(Game2048.create(seed));"sudoku"->save(SudokuGame.create(level,seed));"snake"->save(SnakeGame.create(seed));else->error("Unknown game")}}
}
