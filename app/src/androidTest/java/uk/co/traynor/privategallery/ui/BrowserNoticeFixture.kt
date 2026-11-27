package uk.co.traynor.privategallery.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Drive the actual first-use notice before testing browser controls. Restore only its nonsecret revision. */
internal class BrowserNoticeFixture(private val hidden: Boolean = false) : ExternalResource() {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences(
        if (hidden) "nova-hidden-browser-beta" else "nova-browser-beta", Context.MODE_PRIVATE
    )
    private var previous: Int? = null

    override fun before() {
        previous = if (preferences.contains("notice-revision")) preferences.getInt("notice-revision", 0) else null
    }

    fun enter(compose: ComposeTestRule) {
        val acknowledgement = BrowserBetaAcknowledgement(context, hidden)
        if (!acknowledgement.accepted()) {
            compose.onNodeWithTag("browser-beta-continue").performScrollTo().assertIsDisplayed().performClick()
            compose.waitForIdle()
        }
        check(acknowledgement.accepted())
    }

    override fun after() {
        val edit = preferences.edit()
        previous?.let { edit.putInt("notice-revision", it) } ?: edit.remove("notice-revision")
        check(edit.commit())
    }
}
