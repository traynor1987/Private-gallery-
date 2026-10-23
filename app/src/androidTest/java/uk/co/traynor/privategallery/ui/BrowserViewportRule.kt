package uk.co.traynor.privategallery.ui

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Resize before Activity launch: Compose Dialogs have their own windows, outside parent modifiers. */
internal class BrowserViewportRule(private val viewport: (String) -> Pair<Int, Int>?) : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val size = viewport(description.methodName) ?: return base.evaluate()
            val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
            try {
                shell("wm density 160")
                shell("wm size ${size.first}x${size.second}")
                base.evaluate()
            } finally {
                // Activity teardown runs inside base; later test classes retain the original device setup.
                try { shell("wm size ${oldSize ?: "reset"}") }
                finally { shell("wm density ${oldDensity ?: "reset"}") }
            }
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }
}
