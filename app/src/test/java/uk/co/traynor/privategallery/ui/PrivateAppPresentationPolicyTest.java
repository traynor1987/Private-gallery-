package uk.co.traynor.privategallery.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrivateAppPresentationPolicyTest {
    @Test public void ordinaryAppContentKeepsTheWholePageEvenWhenBrowserChromeIsPinned() {
        assertFalse(PrivateAppPresentationPolicy.showBrowserChrome(true, false, false, false));
        assertFalse(PrivateAppPresentationPolicy.showAddressBar(true, true, true, true));
        assertFalse(PrivateAppPresentationPolicy.showAddressBar(true, true, true, false));
    }

    @Test public void appModeKeepsItsPresentationDuringConnectionPageAndProviderFailures() {
        for (boolean blocked : new boolean[]{false, true}) {
            for (boolean pageFailed : new boolean[]{false, true}) {
                for (boolean providerFailed : new boolean[]{false, true}) {
                    assertFalse(PrivateAppPresentationPolicy.showBrowserChrome(true, blocked, pageFailed, providerFailed));
                    assertTrue(PrivateAppPresentationPolicy.showBrowserChrome(false, blocked, pageFailed, providerFailed));
                }
            }
        }
    }

    @Test public void standaloneBrowserAndSavedBrowserStyleKeepTheirAddressPreferences() {
        assertTrue(PrivateAppPresentationPolicy.showBrowserChrome(false, false, false, false));
        assertTrue(PrivateAppPresentationPolicy.showAddressBar(false, false, false, false));
        assertTrue(PrivateAppPresentationPolicy.showAddressBar(true, false, true, false));
        assertFalse(PrivateAppPresentationPolicy.showAddressBar(true, false, false, true));
    }
}
