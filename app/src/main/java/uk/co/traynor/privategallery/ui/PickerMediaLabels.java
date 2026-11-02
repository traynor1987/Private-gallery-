package uk.co.traynor.privategallery.ui;

import java.util.Locale;

/** Presentation only: unknown duration never masquerades as a zero-length video. */
public final class PickerMediaLabels {
    private PickerMediaLabels() {}
    public static String duration(Long milliseconds) {
        if (milliseconds == null || milliseconds < 0) return "Duration unavailable";
        long seconds = milliseconds / 1000;
        if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
