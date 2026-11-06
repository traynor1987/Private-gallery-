import uk.co.traynor.privategallery.core.arcade.*;
import java.util.*;
public final class ArcadeChecks {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void rejects(Runnable action){try{action.run();throw new AssertionError("Malformed state accepted");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  int[] row={2,2,2,2,0,0,0,0,0,0,0,0,0,0,0,0};
  Game2048 g=new Game2048(row,0,19,null);Game2048 m=g.move(0);
  check(m.cell(0)==4&&m.cell(1)==4&&m.score==8,"2048 merges once per tile");
  check(Arrays.equals(m.undo().cells(),row)&&m.undo().seed==19,"Undo restores board and random stream");
  check(m.encode().equals(g.move(0).encode()),"Placement is deterministic");
  check(Game2048.decode(m.encode()).encode().equals(m.encode()),"2048 codec roundtrip");
  int[] blocked={2,4,2,4,4,2,4,2,2,4,2,4,4,2,4,2};
  check(new Game2048(blocked,0,3,null).over(),"No legal move is game over");
  check(new Game2048(blocked,0,3,null).move(0).seed==3,"No-op does not consume random stream");
  int[] win=new int[16];win[0]=1024;win[1]=1024;check(new Game2048(win,0,1,null).move(0).won(),"2048 victory detected");
  rejects(()->Game2048.decode("garbage"));
  for(int level=0;level<4;level++)for(int seed=1;seed<=3;seed++){
   SudokuGame s=SudokuGame.create(level,seed);check(SudokuSolver.count(s.givens(),2)==1,"Unique Sudoku level "+level);check(SudokuSolver.valid(s.solution(),true),"Valid solution");
   int at=0;while(s.given(at)!=0)at++;int cell=at;SudokuGame n=s.enter(cell,3,true);
   check(n.note(cell)==(1<<3)&&n.undo().note(cell)==0,"Pencil notes undo");
   int wrong=s.solution()[cell]%9+1;check(s.enter(cell,wrong,false).mistakes==1,"Wrong entry counts mistake");
   check(s.hint(cell).cell(cell)==s.solution()[cell],"Hint fills validated solution");
   check(SudokuGame.decode(n.encode()).encode().equals(n.encode()),"Sudoku codec roundtrip");
   check(!s.complete(),"Incomplete puzzle is not won");
  }
  int[] invalid=new int[81];Arrays.fill(invalid,1);check(!SudokuSolver.valid(invalid,true)&&SudokuSolver.count(invalid,2)==0,"Invalid completed grid rejected");
  check(SudokuSolver.count(new int[81],2)==2,"Multiple solutions detected with cutoff");
  SnakeGame snake=SnakeGame.create(17).pause(false);check(snake.turn(2).direction==0&&snake.turn(2).queue().length==0,"Immediate reversal refused");
  SnakeGame queued=snake.turn(1).turn(2).turn(0);check(Arrays.equals(queued.queue(),new int[]{1,2}),"Queue caps and validates against queued direction");
  check(queued.tick().direction==1&&queued.tick().tick().direction==2,"Turns apply once per tick");
  check(snake.pause(true).tick().encode().equals(snake.pause(true).encode()),"Pause freezes snake");
  check(SnakeGame.decode(queued.encode()).encode().equals(queued.encode()),"Snake roundtrip");
  SnakeGame wall=new SnakeGame(new int[]{15,14,13},0,new int[0],30,0,1,false,false);check(wall.tick().over,"Wall collision");
  SnakeGame food=new SnakeGame(new int[]{34,33,32},0,new int[0],35,0,1,false,false).tick();check(food.score==1&&food.body().length==4&&!contains(food.body(),food.food),"Food grows and respawns safely");
  rejects(()->new SnakeGame(new int[]{34,33,32},2,new int[0],50,0,1,false,false));
  rejects(()->SnakeGame.decode("bad"));rejects(()->SudokuGame.decode("bad"));
  Map<String,Object> prefs=new HashMap<>();prefs.put("save:2048",m.encode());prefs.put("save:sudoku",SudokuGame.create(0,4).encode());prefs.put("save:snake",queued.encode());prefs.put("sound",false);ArcadePreferences.validate(prefs);
  prefs.put("sound","false");rejects(()->ArcadePreferences.validate(prefs));prefs.remove("sound");prefs.put("save:snake","bad");rejects(()->ArcadePreferences.validate(prefs));
  int[] maxed=new int[16];Arrays.fill(maxed,1<<29);check(new Game2048(maxed,0,1,null).over(),"Bounded maximum tiles cannot merge");
  for(int direction=0;direction<4;direction++){int[] pair=new int[16];pair[5]=2;pair[6]=2;if(direction==1||direction==3){pair[6]=0;pair[9]=2;}Game2048 moved=new Game2048(pair,0,7,null).move(direction);check(moved.score==4,"All directions merge");}
  int[] triple=new int[16];triple[0]=2;triple[1]=2;triple[2]=4;Game2048 noChain=new Game2048(triple,0,6,null).move(0);check(noChain.cell(0)==4&&noChain.cell(1)==4&&noChain.score==4,"Merged tile never merges twice");
  SudokuGame solve=SudokuGame.create(3,9);for(int i=0;i<81;i++)if(solve.given(i)==0)solve=solve.enter(i,solve.solution()[i],false);check(solve.complete(),"Only correct filled Sudoku completes");check(solve.completedOnce,"First completion is credited");SudokuGame undone=solve.undo();check(undone.completedOnce&&!undone.complete(),"Undo preserves completion credit");check(SudokuGame.decode(undone.encode()).completedOnce,"Save retains completion credit after undo");check(!SudokuGame.create(3,10).completedOnce,"New puzzle resets completion credit");
  SudokuGame wrong=SudokuGame.create(2,8);for(int i=0;i<81;i++)if(wrong.given(i)==0)wrong=wrong.enter(i,1,false);check(!wrong.complete(),"Invalid filled Sudoku never completes");
  SnakeGame tail=new SnakeGame(new int[]{17,18,34,33},2,new int[0],80,1,1,false,false);check(!tail.turn(1).tick().over,"Moving into vacated tail is legal");
  SnakeGame self=new SnakeGame(new int[]{17,18,34,33,32,16},2,new int[0],80,3,1,false,false);check(self.turn(1).tick().over,"Body collision detected");
  int[] almostFull=new int[255];int nextCell=0;for(int y=0;y<16;y++)for(int x=0;x<16;x++){int at=y*16+(y%2==0?x:15-x);if(at!=0)almostFull[nextCell++]=at;}SnakeGame filled=new SnakeGame(almostFull,2,new int[0],0,252,9,false,false).tick();check(filled.over&&filled.food==-1&&filled.score==253,"Full board completes without food retry loop");
  String encoded=m.encode();byte[] bytes=Base64.getDecoder().decode(encoded);rejects(()->Game2048.decode(Base64.getEncoder().encodeToString(Arrays.copyOf(bytes,bytes.length-1))));rejects(()->Game2048.decode(Base64.getEncoder().encodeToString(Arrays.copyOf(bytes,bytes.length+1))));
  Game2048 capped=new Game2048(row,Long.MAX_VALUE/2,19,null).move(0);check(capped.score==Long.MAX_VALUE/2,"Imported maximum score stays in save bounds after play");
  SudokuGame cappedSudoku=SudokuGame.create(0,1);byte[] sud=Base64.getDecoder().decode(cappedSudoku.encode());java.nio.ByteBuffer.wrap(sud).putInt(1330,1_000_000).putInt(1334,1_000_000);cappedSudoku=SudokuGame.decode(Base64.getEncoder().encodeToString(sud));int empty=0;while(cappedSudoku.given(empty)!=0)empty++;check(cappedSudoku.enter(empty,cappedSudoku.solution()[empty]%9+1,false).mistakes==1_000_000,"Imported maximum mistake count remains usable");check(cappedSudoku.hint(empty).hints==1_000_000,"Imported maximum hint count remains usable");
  System.out.println("PASS Arcade: "+checks+" behavioral checks");
 }
 static boolean contains(int[] a,int v){for(int i:a)if(i==v)return true;return false;}
}
