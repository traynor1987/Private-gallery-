package uk.co.traynor.privategallery.core.ui;

/** Authentication is checked by the caller; this boundary restricts media UI to media destinations. */
public final class EditingContextPolicy {
    private EditingContextPolicy() {}
    public static boolean allowed(String destination, boolean homeVisible, boolean otherFeatureVisible) {
        return !homeVisible && !otherFeatureVisible &&
            ("GALLERY".equals(destination) || "VAULT".equals(destination) || "FAVOURITE".equals(destination));
    }
}
