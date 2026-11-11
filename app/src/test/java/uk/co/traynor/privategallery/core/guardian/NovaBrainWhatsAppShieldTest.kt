package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaBrainWhatsAppShieldTest {
    @Test fun accountsAreIndependentAndUnknownCallersPassThrough() {
        val rules = listOf(GuardianPhoneRule("+447700900001", GuardianCallMode.ALLOW,
            GuardianCallMode.SILENCE, GuardianCallMode.BLOCK))
        val caller = "tel:+447700900001"
        fun action(account: ProbeAccount, uri: String? = caller, incoming: Boolean = true) =
            guardianWhatsAppCallAction(true, account, incoming, uri, rules)
        assertEquals(GuardianCallMode.SILENCE, action(ProbeAccount.MAIN))
        assertEquals(GuardianCallMode.BLOCK, action(ProbeAccount.FRIENDS))
        assertEquals(GuardianCallMode.ALLOW, action(ProbeAccount.UNRESOLVED))
        assertEquals(GuardianCallMode.ALLOW, action(ProbeAccount.MAIN, "Contact name"))
        assertEquals(GuardianCallMode.ALLOW, action(ProbeAccount.MAIN, incoming = false))
    }
}
