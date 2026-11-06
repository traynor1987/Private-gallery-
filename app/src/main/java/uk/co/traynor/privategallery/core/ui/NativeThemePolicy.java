package uk.co.traynor.privategallery.core.ui;

/** Initial bridge migration only; subsequent choices live in the originating encrypted Home model. */
public final class NativeThemePolicy {
    private NativeThemePolicy() {}
    public static String initial(String nativePreference, String homePreference, boolean hidden) {
        if (!hidden && valid(nativePreference)) return nativePreference;
        return valid(homePreference) ? homePreference : "system";
    }
    public static String next(String theme) {
        if ("dark".equals(theme)) return "light";
        if ("light".equals(theme)) return "system";
        return "dark";
    }
    private static boolean valid(String theme) {
        return "dark".equals(theme) || "light".equals(theme) || "system".equals(theme);
    }
}
