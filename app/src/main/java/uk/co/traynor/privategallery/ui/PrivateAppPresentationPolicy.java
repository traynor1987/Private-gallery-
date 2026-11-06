package uk.co.traynor.privategallery.ui;

/** Transport/readiness failures stay in the page surface; App Mode keeps its own chrome. */
public final class PrivateAppPresentationPolicy {
    private PrivateAppPresentationPolicy() {}

    public static boolean showBrowserChrome(boolean appStyle, boolean networkBlocked,
                                            boolean pageFailed, boolean providerFailed) {
        return !appStyle;
    }

    public static boolean showAddressBar(boolean savedApp, boolean appStyle,
                                         boolean addressBar, boolean controlsExpanded) {
        return !savedApp || (!appStyle && addressBar);
    }
}
