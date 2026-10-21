package uk.co.traynor.privategallery.core.vpn;
public final class WireGuardReadinessChecks {
 public static void main(String[] args) {
  check(!WireGuardReadiness.peerVerified(1_000_000,0),"UP without handshake must stay blocked");
  check(WireGuardReadiness.peerVerified(1_000_000,999_000),"fresh authenticated handshake opens gate");
  check(!WireGuardReadiness.peerVerified(1_000_000,800_000),"stale handshake closes gate");
  check(!WireGuardReadiness.peerVerified(1_000_000,1_010_000),"future clock anomaly fails closed");
  check(WireGuardReadiness.peerVerified(1_000_000,820_000),"maximum valid age accepted");
  System.out.println("WireGuard readiness: 5 checks passed");
 }
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
