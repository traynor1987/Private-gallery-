package uk.co.traynor.novaos.preview;
import java.util.*;
public final class WidgetRules {
 public static final int COLUMNS=4,ROWS=24;
 public static boolean overlap(int[] a,int[] b){return a[0]<b[0]+b[2]&&a[0]+a[2]>b[0]&&a[1]<b[1]+b[3]&&a[1]+a[3]>b[1];}
 public static boolean fits(int[] r,List<int[]> occupied,int ignore){if(r.length!=4||r[0]<0||r[1]<0||r[2]<1||r[3]<1||r[0]+r[2]>COLUMNS||r[1]+r[3]>ROWS)return false;for(int i=0;i<occupied.size();i++)if(i!=ignore&&overlap(r,occupied.get(i)))return false;return true;}
 public static int[] firstFree(int w,int h,List<int[]> occupied){for(int y=0;y<=ROWS-h;y++)for(int x=0;x<=COLUMNS-w;x++){int[] r={x,y,w,h};if(fits(r,occupied,-1))return r;}return null;}
 public static boolean compatible(int w,int h,int otherW,int otherH){return w==otherW&&h==otherH;}
 public static boolean pipEligible(boolean nativeVideo,boolean prepared,boolean locked){return nativeVideo&&prepared&&!locked;}
 public static int active(int i,int count){return Math.max(0,Math.min(i,Math.max(0,count-1)));}
}
