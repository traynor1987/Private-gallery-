package uk.co.traynor.privategallery.core.arcade;
import java.io.*;
import java.util.Arrays;
/** Directions: left, down, right, up. Each source tile merges at most once. */
public final class Game2048 {
 private final int[] board;public final long score,seed;private final Game2048 previous;
 public Game2048(int[] board,long score,long seed,Game2048 previous){ArcadeCodec.require(board.length==16&&score>=0&&score<=Long.MAX_VALUE/2);for(int v:board)ArcadeCodec.require(v==0||(v>=2&&v<=1<<29&&(v&(v-1))==0));this.board=board.clone();this.score=score;this.seed=seed;this.previous=previous==null?null:new Game2048(previous.board,previous.score,previous.seed,null);}
 public static Game2048 create(long seed){ArcadeCodec.Random r=new ArcadeCodec.Random(seed);int[] b=new int[16];spawn(b,r);spawn(b,r);return new Game2048(b,0,r.seed,null);}
 public int cell(int i){return board[i];}public int[] cells(){return board.clone();}public boolean canUndo(){return previous!=null;}public Game2048 undo(){return previous==null?this:previous;}
 public boolean won(){for(int v:board)if(v>=2048)return true;return false;}
 public boolean over(){for(int i=0;i<16;i++)if(board[i]==0||(board[i]<(1<<29)&&((i%4<3&&board[i]==board[i+1])||(i<12&&board[i]==board[i+4]))))return false;return true;}
 private static int index(int direction,int line,int at){return switch(direction){case 0->line*4+at;case 1->(3-at)*4+line;case 2->line*4+3-at;default->at*4+line;};}
 public Game2048 move(int direction){ArcadeCodec.require(direction>=0&&direction<4);int[] next=new int[16];long gained=0;for(int line=0;line<4;line++){int[] a=new int[4];int count=0;for(int i=0;i<4;i++){int v=board[index(direction,line,i)];if(v!=0)a[count++]=v;}int dest=0;for(int i=0;i<count;i++){int v=a[i];if(i+1<count&&v==a[i+1]&&v<(1<<29)){v*=2;gained+=v;i++;}next[index(direction,line,dest++)]=v;}}
 if(Arrays.equals(board,next))return this;ArcadeCodec.Random r=new ArcadeCodec.Random(seed);spawn(next,r);return new Game2048(next,Math.min(Long.MAX_VALUE/2,score+gained),r.seed,this);}
 private static void spawn(int[] b,ArcadeCodec.Random r){int empty=0;for(int v:b)if(v==0)empty++;if(empty==0)return;int chosen=r.next(empty),value=r.next(10)==0?4:2;for(int i=0;i<b.length;i++)if(b[i]==0&&chosen--==0){b[i]=value;break;}}
 private void write(DataOutputStream out)throws IOException{ArcadeCodec.ints(out,board);out.writeLong(score);out.writeLong(seed);}
 public String encode(){return ArcadeCodec.encode(1,out->{write(out);out.writeBoolean(previous!=null);if(previous!=null)previous.write(out);});}
 private static Game2048 read(DataInputStream in)throws IOException{return new Game2048(ArcadeCodec.ints(in,16,16),in.readLong(),in.readLong(),null);}
 public static Game2048 decode(String raw){return ArcadeCodec.decode(raw,1,in->{Game2048 s=read(in);Game2048 p=ArcadeCodec.bool(in)?read(in):null;return new Game2048(s.board,s.score,s.seed,p);});}
}
