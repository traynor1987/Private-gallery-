package uk.co.traynor.privategallery.core.ui.status;

/** Device status only. Never carries a profile, app/session identifier, SSID or SIM details. */
public final class SystemStatusSnapshot {
    public enum Transport { UNKNOWN, DISCONNECTED, WIFI, MOBILE, ETHERNET, OTHER }
    public final Integer batteryPercent;
    public final boolean chargerConnected;
    public final boolean charging;
    public final boolean powerSaver;
    public final boolean airplaneMode;
    public final Transport transport;
    public final boolean internetValidated;
    /** Android-reported display technology, null when unavailable/restricted. */
    public final String mobileTechnology;

    public SystemStatusSnapshot(Integer batteryPercent, boolean chargerConnected, boolean charging,
            boolean powerSaver, boolean airplaneMode, Transport transport,
            boolean internetValidated, String mobileTechnology) {
        this.batteryPercent = batteryPercent != null && batteryPercent >= 0 && batteryPercent <= 100
                ? batteryPercent : null;
        this.chargerConnected = chargerConnected;
        this.charging = charging;
        this.powerSaver = powerSaver;
        this.airplaneMode = airplaneMode;
        this.transport = transport == null ? Transport.UNKNOWN : transport;
        this.internetValidated = internetValidated;
        this.mobileTechnology = mobileTechnology;
    }

    public static Integer batteryPercent(int level, int scale) {
        if (scale <= 0 || level < 0 || level > scale) return null;
        return (int) Math.round(level * 100.0 / scale);
    }

    /** Interior fill for the battery glyph; callers omit the glyph for an unknown reading. */
    public float batteryFillFraction() {
        return batteryPercent == null ? 0f : batteryPercent / 100f;
    }

    public String networkLabel() {
        String connection;
        switch (transport) {
            case WIFI: connection = "Wi-Fi"; break;
            case MOBILE: connection = mobileTechnology == null ? "Mobile" : mobileTechnology; break;
            case ETHERNET: connection = "Ethernet"; break;
            case OTHER: return internetValidated ? "Connected" : "No internet";
            case DISCONNECTED: return airplaneMode ? "Airplane mode" : "Offline";
            default: return "";
        }
        return internetValidated ? connection : connection + " · No internet";
    }
}
