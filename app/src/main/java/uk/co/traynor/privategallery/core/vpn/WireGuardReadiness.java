package uk.co.traynor.privategallery.core.vpn;
public final class WireGuardReadiness {
 private WireGuardReadiness() {}
 public static boolean peerVerified(long nowEpochMillis,long handshakeEpochMillis) { return handshakeEpochMillis > 0 && nowEpochMillis >= handshakeEpochMillis && nowEpochMillis - handshakeEpochMillis <= 180_000; }
}
