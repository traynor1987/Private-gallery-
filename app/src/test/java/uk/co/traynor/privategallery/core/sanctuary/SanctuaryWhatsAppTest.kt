package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctuaryWhatsAppTest {
    @Test fun identifiesDestinationsWithoutRelyingOnDisplayNames() {
        listOf(
            "https://web.whatsapp.com/",
            "https://WEB.WHATSAPP.COM./chat",
            "https://api.whatsapp.com/send",
            "https://web.whatsapp.com:443/",
            "https://static.whatsapp.net/",
            "https://wa.me/123",
            "https://example.org@web.whatsapp.com/",
            "https://%77eb.whatsapp.com/",
            "https://web。whatsapp。com/",
            "https:///web.whatsapp.com/",
            "whatsapp://send?text=hello",
            "intent://send/#Intent;scheme=whatsapp;package=com.whatsapp;end",
        ).forEach { assertTrue(it, SanctuaryWhatsApp.isRelatedUrl(it)) }
        listOf(
            "https://notwhatsapp.com/",
            "https://whatsapp.com.example.org/",
            "https://wa.me.example.org/",
            "https://web.whatsapp.com@example.org/",
            "https://example.org/?link=https://web.whatsapp.com/",
            "https://example.org/whatsapp",
        ).forEach { assertFalse(it, SanctuaryWhatsApp.isRelatedUrl(it)) }
    }

    @Test fun matchesStableIdentityAcrossKnownRouteWrappers() {
        listOf("chat", "chat-inspector", "chat-settings", "nova-chat-lite", "whatsapp",
            "web:whatsapp", "store:web:whatsapp", "resume-internal:chat")
            .forEach { assertTrue(it, SanctuaryWhatsApp.isRelatedApp(it)) }
        listOf("web:installed-id", "whatsapp-help", "mind:chat")
            .forEach { assertFalse(it, SanctuaryWhatsApp.isRelatedApp(it)) }
    }
}
