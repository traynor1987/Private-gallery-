package uk.co.traynor.privategallery.ui;

/** App Mode reserves page space only for an actionable failure, never for ordinary chrome. */
public final class PrivateAppPresentationPolicy {
    private PrivateAppPresentationPolicy() {}

    public static boolean showBrowserChrome(boolean appStyle, boolean networkBlocked,
                                            boolean pageFailed, boolean providerFailed) {
        return !appStyle || networkBlocked || pageFailed || providerFailed;
    }

    public static boolean showAddressBar(boolean savedApp, boolean appStyle,
                                         boolean addressBar, boolean controlsExpanded) {
        return !savedApp || (!appStyle && addressBar);
    }
}
