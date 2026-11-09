package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppIdentityTest {
    @Test fun existingKeysKeepTheirExactSpelling() {
        val key = "com.example.chat/com.example.chat.ChatActivity"
        val identity = LauncherAppIdentity.parse(key)!!
        assertEquals(key, identity.flattenedComponent)
        assertEquals(key, identity.key)
        assertNull(identity.profileSerial)
    }

    @Test fun sameComponentInTwoProfilesHasSeparateLayoutKeys() {
        val component = "com.example.chat/.ChatActivity"
        val personal = LauncherAppIdentity.forComponent(component, null)!!
        val duplicate = LauncherAppIdentity.forComponent(component, 42L)!!
        assertEquals(component, personal.key)
        assertEquals("com.example.chat/.ChatActivity#42", duplicate.key)
        assertEquals(component, duplicate.flattenedComponent)
        assertEquals(duplicate, LauncherAppIdentity.parse(duplicate.key))
    }

    @Test fun keyRejectsAmbiguousProfileSuffixesAndIntentSyntax() {
        val component = "com.example.chat/.ChatActivity"
        listOf("", "-1", "+42", "042", "42#43", " 42", "４２", "9223372036854775808").forEach { serial ->
            assertFalse(LauncherAppIdentity.isValidKey("$component#$serial"))
        }
        assertFalse(LauncherAppIdentity.isValidKey("intent://com.example.chat/.ChatActivity#42"))
        assertNull(LauncherAppIdentity.forComponent(component, -1L))
        assertTrue(LauncherAppIdentity.isValidKey("$component#0"))
    }
}
