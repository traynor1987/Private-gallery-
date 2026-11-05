package uk.co.traynor.novaos.preview;
public final class MindRules {
 private static long phase(long ms,int in,int out){if(in<1||out<1)throw new IllegalArgumentException();return Math.max(0,ms)%((in+out)*1000L);}
 public static boolean inhale(long ms,int in,int out){return phase(ms,in,out)<in*1000L;}
 public static float expansion(long ms,int in,int out){long p=phase(ms,in,out);return p<in*1000L?p/(in*1000f):1-(p-in*1000L)/(out*1000f);}
 public static long elapsed(long saved,long start,long now,boolean running){return saved+(running?Math.max(0,now-start):0);}
 public static long remaining(long duration,long elapsed){return Math.max(0,duration-elapsed);}
 public static int groundCount(int step){return Math.max(0,5-Math.max(0,step));}
}
