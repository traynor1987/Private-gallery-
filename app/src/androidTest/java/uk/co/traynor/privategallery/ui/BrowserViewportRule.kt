package uk.co.traynor.privategallery.ui

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.math.abs

/** Configure before Activity launch: Dialog/Popup windows obtain Android resource configuration. */
internal class BrowserViewportRule(
    private val fontScale:Float?=null,
    private val viewport:(String)->Pair<Int,Int>?,
):TestRule {
    init {require(fontScale==null || fontScale.isFinite() && fontScale in 0.5f..3f)}
    override fun apply(base:Statement,description:Description):Statement=object:Statement() {
        override fun evaluate() {
            val size=viewport(description.methodName)
            if(size==null && fontScale==null)return base.evaluate()
            val oldSize=if(size!=null)Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1) else null
            val oldDensity=if(size!=null)Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1) else null
            val oldFontSetting=if(fontScale!=null)shell("settings get system font_scale").trim() else null
            val oldFontConfiguration=if(fontScale!=null)configuredFontScale() else null
            require(oldFontSetting==null || oldFontSetting=="null" || oldFontSetting.matches(Regex("[0-9]+(?:\\.[0-9]+)?")))
            try {
                if(size!=null) {
                    shell("wm density 160")
                    shell("wm size ${size.first}x${size.second}")
                }
                fontScale?.let {
                    shell("settings put system font_scale $it")
                    awaitFontScale(it)
                }
                base.evaluate()
            } finally {
                // Activity teardown runs inside base. Restore every global setting before later tests.
                try {if(size!=null)shell("wm size ${oldSize ?: "reset"}")}
                finally {
                    try {if(size!=null)shell("wm density ${oldDensity ?: "reset"}")}
                    finally {
                        if(oldFontSetting!=null) {
                            if(oldFontSetting=="null")shell("settings delete system font_scale")
                            else shell("settings put system font_scale $oldFontSetting")
                            awaitFontScale(checkNotNull(oldFontConfiguration))
                        }
                    }
                }
            }
        }
    }

    private fun configuredFontScale():Float {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var actual=Float.NaN
        instrumentation.runOnMainSync {actual=instrumentation.targetContext.resources.configuration.fontScale}
        return actual
    }
    private fun awaitFontScale(expected:Float) {
        val deadline=SystemClock.elapsedRealtime()+10_000
        var actual=configuredFontScale()
        while(abs(actual-expected)>0.001f && SystemClock.elapsedRealtime()<deadline) {
            Thread.sleep(50);actual=configuredFontScale()
        }
        check(actual.isFinite() && abs(actual-expected)<=0.001f) {"Android fontScale was $actual, expected $expected before Activity launch/after teardown"}
    }
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).bufferedReader().use {it.readText()}
}
