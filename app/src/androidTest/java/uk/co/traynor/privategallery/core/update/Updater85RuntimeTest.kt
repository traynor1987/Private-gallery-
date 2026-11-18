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
    @Test fun firstCheckAfterUpgradeUsesActualInstalledPackageInsteadOfMissingOldInstallMarker() {
        val prefs=context.getSharedPreferences("private-gallery-settings",Context.MODE_PRIVATE)
        val before=prefs.getString("update-last-checked",null)
        val pending=prefs.getLong("update-install-target",0)
        val actual=context.packageManager.getPackageInfo(context.packageName,0)
        val digest="ab".repeat(32)
        val prefix="https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.39/"
        val response=org.json.JSONObject().put("tag_name","v1.7.39").put("draft",false).put("prerelease",false)
            .put("assets",org.json.JSONArray().apply{
                for(name in listOf("private-gallery-release.apk","private-gallery-release.apk.sha256","private-gallery-release.metadata.json"))
                    put(org.json.JSONObject().put("name",name).put("browser_download_url",prefix+name).apply{if(name.endsWith(".apk"))put("digest","sha256:"+digest)})
            }).toString()
        val identity=org.json.JSONObject().put("package","uk.co.traynor.privategallery").put("versionName","1.7.39")
            .put("versionCode",actual.longVersionCode).put("certificateSha256","94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17").put("apkSha256",digest).toString()
        lateinit var model:NovaUpdateModel
        try {
            prefs.edit().remove("update-install-target").commit()
            instrumentation.runOnMainSync {
                model=NovaUpdateModel(context.applicationContext as Application,GithubReleaseUpdateService{ url ->
                    (if(url.endsWith("latest"))response else if(url.endsWith("metadata.json"))identity else digest).toByteArray()
                })
                assertEquals(UpdatePhase.IDLE,model.state.phase)
                model.check()
            }
            val deadline=System.currentTimeMillis()+10_000
            while(System.currentTimeMillis()<deadline) {
                instrumentation.waitForIdleSync()
                var finished=false
                instrumentation.runOnMainSync{finished=!model.state.busy}
                if(finished)break
                Thread.sleep(25)
            }
            instrumentation.runOnMainSync {
                assertEquals(UpdatePhase.UP_TO_DATE,model.state.phase)
                assertEquals("${actual.versionName} · Up to date",model.state.message)
            }
            assertTrue(prefs.getString("update-last-checked",null)!=null)
        } finally {
            prefs.edit().apply{if(before==null)remove("update-last-checked") else putString("update-last-checked",before);if(pending==0L)remove("update-install-target") else putLong("update-install-target",pending)}.commit()
        }
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
