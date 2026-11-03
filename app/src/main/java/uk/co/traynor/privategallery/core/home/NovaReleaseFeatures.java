package uk.co.traynor.privategallery.core.home;

/** Code55 shipping policy. Preferences, saved routes and intents cannot enable Tor. */
public final class NovaReleaseFeatures {
    private NovaReleaseFeatures() {}
    public static final boolean TOR_ENABLED = false;
    public static boolean browserModeAllowed(String mode) {
        return "VPN_ONLY".equals(mode) || (TOR_ENABLED && "TOR_ONLY".equals(mode));
    }
    public static void requireTorEnabled() {
        if (!TOR_ENABLED) throw new SecurityException("Tor is deferred in this release");
    }
}
