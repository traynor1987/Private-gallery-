package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import uk.co.traynor.privategallery.HiddenPolicySettings
import uk.co.traynor.privategallery.core.security.AutoLockTimeout

class HiddenPolicySettingsTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun hiddenTimeoutChoicesPersistWithoutChangingPrimaryPolicy() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="hidden-policy-test-${UUID.randomUUID()}"
        val names=mutableSetOf<String>()
        val context=object:ContextWrapper(base){override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
            val qualified="$prefix-$name";names.add(qualified);return base.getSharedPreferences(qualified,mode)
        }}
        val primary=base.getSharedPreferences("$prefix-primary",Context.MODE_PRIVATE)
        primary.edit().putString("timeout","IMMEDIATELY").commit()
        try {
            val policy=HiddenPreferences(context)
            val timeout=mutableStateOf(policy.timeout)
            assertEquals(AutoLockTimeout.MINUTE_1,timeout.value)
            compose.setContent {MaterialTheme {HiddenPolicySettings(timeout.value,onTimeoutChanged={policy.timeout=it;timeout.value=it},onUsePrimary={})}}
            compose.onNodeWithText("Hidden Vault — independent settings").assertIsDisplayed()
            compose.onNodeWithText("After 5 minutes").performClick()
            compose.runOnIdle {
                assertEquals(AutoLockTimeout.MINUTES_5,HiddenPreferences(context).timeout)
                assertEquals("IMMEDIATELY",primary.getString("timeout",null))
            }
            listOf("Immediately","After 30 seconds","After 1 minute").forEach {compose.onNodeWithText(it).assertIsDisplayed()}
        } finally {
            names.forEach {base.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()}
            primary.edit().clear().commit()
        }
    }
}
