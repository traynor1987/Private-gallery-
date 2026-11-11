package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaBrainPhoneShieldTest {
    @Test fun exactNumbersSelectIndependentModes() {
        val rules = GuardianCallMode.entries.mapIndexed { index, mode ->
            GuardianPhoneRule("+44770090000$index", mode)
        }
        rules.forEach { rule ->
            assertEquals(rule.mode, guardianPhoneAction(true, true, rule.number, rules))
        }
        assertEquals(GuardianCallMode.SILENCE, guardianPhoneAction(true, true, "+44 7700 900001", rules))
    }

    @Test fun unavailablePermissionAndUnknownNumbersPassThrough() {
        val rules = listOf(GuardianPhoneRule("+447700900001", GuardianCallMode.BLOCK))
        assertEquals(GuardianCallMode.ALLOW, guardianPhoneAction(false, true, rules.single().number, rules))
        assertEquals(GuardianCallMode.ALLOW, guardianPhoneAction(true, false, rules.single().number, rules))
        listOf(null, "999", "07700900001", "+447700900002", "anonymous").forEach {
            assertEquals(GuardianCallMode.ALLOW, guardianPhoneAction(true, true, it, rules))
        }
    }
}
