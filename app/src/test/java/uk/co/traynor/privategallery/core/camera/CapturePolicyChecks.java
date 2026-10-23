package uk.co.traynor.privategallery.core.camera;
public final class CapturePolicyChecks {
 private static int checks;
 private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check "+checks); }
 public static void main(String[] args) {
  CaptureInputGate gate=new CaptureInputGate();
  check(gate.begin(1000)); check(!gate.begin(1001)); gate.finished();
  check(!gate.begin(1100)); check(gate.begin(1500)); gate.revoke(); gate.finished(); check(!gate.begin(5000));
  CaptureInputGate video=new CaptureInputGate();check(video.begin(0));check(!video.begin(900000));video.finished();check(video.begin(900001));
  check(CaptureInputGate.audioAllowed(false,false));check(!CaptureInputGate.audioAllowed(true,false));check(CaptureInputGate.audioAllowed(true,true));
  System.out.println(checks+" capture policy checks passed");
 }
}
