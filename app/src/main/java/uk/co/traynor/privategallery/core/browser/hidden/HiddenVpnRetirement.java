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
    public static boolean removableTask(String base,String child) { return child.equals(base); }
    public static boolean pendingTask(boolean known,String base,String top,String child) {
        return !known || base==null || top==null || child.equals(base) || child.equals(top);
    }
}
