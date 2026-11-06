package uk.co.traynor.privategallery.core.arcade;
/** Minimum-remaining-values backtracking with a solution-count cutoff. */
public final class SudokuSolver {
 private SudokuSolver(){}
 public static boolean valid(int[] board,boolean complete){if(board==null||board.length!=81)return false;for(int i=0;i<81;i++){int v=board[i];if(v<0||v>9||(complete&&v==0))return false;if(v!=0)for(int j=0;j<i;j++)if(board[j]==v&&(i/9==j/9||i%9==j%9||(i/27==j/27&&i%9/3==j%9/3)))return false;}return true;}
 public static int count(int[] board,int limit){ArcadeCodec.require(limit>=1&&limit<=2);if(!valid(board,false))return 0;return search(board.clone(),limit,new int[]{0});}
 private static int search(int[] b,int limit,int[] visits){if(++visits[0]>2_000_000)throw new IllegalArgumentException("Sudoku verification budget exceeded");int chosen=-1,bits=0,best=10;for(int i=0;i<81;i++)if(b[i]==0){int possible=0;for(int n=1;n<=9;n++){boolean ok=true;for(int j=0;j<81;j++)if(b[j]==n&&(i/9==j/9||i%9==j%9||(i/27==j/27&&i%9/3==j%9/3))){ok=false;break;}if(ok)possible|=1<<n;}int count=Integer.bitCount(possible);if(count==0)return 0;if(count<best){best=count;chosen=i;bits=possible;if(count==1)break;}}if(chosen<0)return 1;int total=0;for(int n=1;n<=9;n++)if((bits&(1<<n))!=0){b[chosen]=n;total+=search(b,limit-total,visits);if(total>=limit)break;}b[chosen]=0;return total;}
}
