package uk.co.traynor.privategallery.core.camera;
/** Monotonic input ownership. Finalisation, not a timer, releases the capture slot. */
public final class CaptureInputGate {
 private boolean busy,revoked; private long last=Long.MIN_VALUE;
 public synchronized boolean begin(long now) {
  if(revoked||busy||(last!=Long.MIN_VALUE&&(now<last||now-last<500)))return false;
  last=now;busy=true;return true;
 }
 public synchronized void finished(){busy=false;}
 public synchronized void revoke(){revoked=true;}
 public static boolean audioAllowed(boolean enabled,boolean granted){return !enabled||granted;}
}
