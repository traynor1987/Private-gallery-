package uk.co.traynor.privategallery.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDefaultHomeStore73Test {
    @Test fun reminderDefaultsOnAndOptOutSurvivesReopeningAndLayoutRestore() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val preferenceName = "test-default-home-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(preferenceName, mode)
        }
        try {
            val store = NovaLauncherStore(context)
            assertTrue(store.save(NovaLauncherState(configured = true)))
            val layout = store.exportLayout()
            assertTrue(store.askForDefaultHome())

            assertTrue(store.setAskForDefaultHome(false))
            val reopened = NovaLauncherStore(context)
            assertFalse(reopened.askForDefaultHome())
            assertArrayEquals(layout, reopened.exportLayout())
            assertTrue(reopened.restoreLayout(layout))
            assertFalse(reopened.askForDefaultHome())

            assertTrue(reopened.setAskForDefaultHome(true))
            assertTrue(NovaLauncherStore(context).askForDefaultHome())
        } finally {
            base.deleteSharedPreferences(preferenceName)
        }
    }
}
