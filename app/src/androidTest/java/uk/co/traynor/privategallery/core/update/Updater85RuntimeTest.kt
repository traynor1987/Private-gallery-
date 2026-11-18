package uk.co.traynor.privategallery.core.update

import android.app.Application
import android.app.Activity
import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real API37 private provider and ViewModel tests; never installs or touches production data. */
class Updater85RuntimeTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun privateUpdateProviderIsReadOnlyAndCannotExposeVaultPaths() {
        val root=File(context.cacheDir,"updates").apply{mkdirs()}
        val file=File.createTempFile("nova-store-",".apk",root).apply{writeText("synthetic update fixture")}
        try {
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
            assertEquals("verified_updates",uri.pathSegments.first())
            assertEquals("synthetic update fixture",context.contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()})
            assertTrue(runCatching{context.contentResolver.openFileDescriptor(uri,"rw")?.close()}.isFailure)
            val privateVault=File(context.filesDir,"vault/update-fixture.bin")
            assertTrue(runCatching{FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",privateVault)}.isFailure)
            val escaped=uri.buildUpon().path("/verified_updates/../vault/update-fixture.bin").build()
            assertTrue(runCatching{context.contentResolver.openInputStream(escaped)?.close()}.isFailure)
        } finally {file.delete()}
    }
    @Test fun failedChecksKeepLastSuccessfulTimestampAndInstallerResultCannotFakeUpgrade() {
        val prefs=context.getSharedPreferences("private-gallery-settings",Context.MODE_PRIVATE)
        val before=prefs.getString("update-last-checked",null)
        val pending=prefs.getLong("update-install-target",0)
        lateinit var model:NovaUpdateModel
        try {
            prefs.edit().putString("update-last-checked","successful fixture check").remove("update-install-target").commit()
            instrumentation.runOnMainSync {
                model=NovaUpdateModel(context.applicationContext as Application,GithubReleaseUpdateService{throw GithubHttpException(403)})
                model.check()
            }
            val deadline=System.currentTimeMillis()+10_000
            while(System.currentTimeMillis()<deadline) {
                instrumentation.waitForIdleSync()
                var finished=false
                instrumentation.runOnMainSync{finished=model.state.phase==UpdatePhase.FAILED}
                if(finished)break
                Thread.sleep(25)
            }
            instrumentation.runOnMainSync {
                assertEquals(UpdatePhase.FAILED,model.state.phase)
                assertEquals("successful fixture check",model.lastChecked)
                model.installResult(Activity.RESULT_OK)
                assertEquals(UpdatePhase.FAILED,model.state.phase)
                model.installResult(Activity.RESULT_CANCELED)
                assertEquals(UpdatePhase.CANCELLED,model.state.phase)
                assertFalse(model.state.busy)
            }
            assertEquals("successful fixture check",prefs.getString("update-last-checked",null))
        } finally {
            prefs.edit().apply{if(before==null)remove("update-last-checked") else putString("update-last-checked",before);if(pending==0L)remove("update-install-target") else putLong("update-install-target",pending)}.commit()
        }
    }
}
