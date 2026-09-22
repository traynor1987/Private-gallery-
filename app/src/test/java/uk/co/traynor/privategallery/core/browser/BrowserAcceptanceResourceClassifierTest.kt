package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserAcceptanceResourceClassifierTest {
    @Test fun `classifies safe file extensions without retaining the resource URL`() {
        assertEquals("script", BrowserAcceptanceResourceClassifier.classify(null, "/assets/application.mjs", false))
        assertEquals("stylesheet", BrowserAcceptanceResourceClassifier.classify(null, "/assets/application.css", false))
        assertEquals("font", BrowserAcceptanceResourceClassifier.classify(null, "/fonts/interface.woff2", false))
        assertEquals("image", BrowserAcceptanceResourceClassifier.classify(null, "/images/logo.avif", false))
        assertEquals("media", BrowserAcceptanceResourceClassifier.classify(null, "/media/clip.webm", false))
    }

    @Test fun `keeps opaque subresources as other rather than claiming success or fetch type`() {
        assertEquals("document", BrowserAcceptanceResourceClassifier.classify(null, null, true))
        assertEquals("other", BrowserAcceptanceResourceClassifier.classify(null, "/runtime/opaque", false))
    }
}
