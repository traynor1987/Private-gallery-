package uk.co.traynor.privategallery.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy
import uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPreference

class BrowserUploadPolicyTest {
    @Test fun vaultOnlyIsDefaultAndDevicePickerRequiresExplicitSetting() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val prefs = context.getSharedPreferences("browser_upload_policy", 0)
        try {
            prefs.edit().clear().commit()
            assertEquals(BrowserUploadPolicy.VAULT_ONLY, BrowserUploadPreference.read(context))
            BrowserUploadPreference.write(context, BrowserUploadPolicy.VAULT_AND_DEVICE)
            assertEquals(BrowserUploadPolicy.VAULT_AND_DEVICE, BrowserUploadPreference.read(context))
            BrowserUploadPreference.write(context, BrowserUploadPolicy.BLOCKED)
            assertEquals(BrowserUploadPolicy.BLOCKED, BrowserUploadPreference.read(context))
        } finally { prefs.edit().clear().commit() }
    }
}
