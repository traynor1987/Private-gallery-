package uk.co.traynor.privategallery.core.browser.hidden;

/** Bounded retirement observation; unknown inventory and exhausted cleanup never admit reuse. */
public final class HiddenVpnRetirement {
    public enum Decision { STALE, SWEEP, RELEASE, EXHAUSTED }
    private final long deadline;
    private long absentSince=-1;
    private boolean exhausted;
    public HiddenVpnRetirement(long deadline) { this.deadline=deadline; }
    public Decision observe(long now, boolean owner, boolean known, boolean pending) {
        if(!owner)return Decision.STALE;
        if(exhausted || now>deadline) {exhausted=true;return Decision.EXHAUSTED;}
        if(!known || pending)absentSince=-1;
        else if(absentSince<0)absentSince=now;
        if(known && !pending && absentSince>=0 && now-absentSince>=1000)return Decision.RELEASE;
        if(now>=deadline) {exhausted=true;return Decision.EXHAUSTED;}
        return Decision.SWEEP;
    }
    public static boolean ownsProcess(int uid,int ownUid,int pid,int parentPid,String name,String expected) {
        return uid==ownUid && pid>0 && pid!=parentPid && expected.equals(name);
    }
    /** A retained recent-task shell is known empty only when all public activity facts agree. */
    public static boolean knownEmptyTask(boolean known,int activities,boolean running,String base,String top) {
        return known && activities==0 && !running && base==null && top==null;
    }
    public static boolean knownTask(boolean known,int activities,boolean running,String base,String top,String original) {
        return knownEmptyTask(known,activities,running,base,top) ? original!=null
                : known && activities>0 && running && base!=null && top!=null;
    }
    public static boolean removableTask(boolean known,int activities,boolean running,String base,String top,String original,String child) {
        return child.equals(base) || knownEmptyTask(known,activities,running,base,top) && child.equals(original);
    }
    public static boolean pendingTask(boolean known,int activities,boolean running,String base,String top,String original,String child) {
        if(!knownTask(known,activities,running,base,top,original))return true;
        if(knownEmptyTask(known,activities,running,base,top))return child.equals(original);
        return child.equals(base) || child.equals(top);
    }
}
