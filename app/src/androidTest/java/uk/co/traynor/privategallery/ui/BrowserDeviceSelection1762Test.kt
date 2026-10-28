package uk.co.traynor.privategallery.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BrowserDeviceSelection1762Test {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val fixture=Uri.parse("content://uk.co.traynor.privategallery.test.browser-selection-fixture")
    private fun granted():Uri=checkNotNull(context.contentResolver.call(fixture,"grant",null,null)).getParcelable("uri",Uri::class.java)!!
    private fun revoke(uri:Uri){context.contentResolver.call(fixture,"revoke",null,Bundle().apply{putParcelable("uri",uri)})}
    @Test fun actualExternalReadGrantAndDescriptorAcceptedThenRevocationDenied() {
        val uri=granted()
        try {
            @Suppress("DEPRECATION") val provider=checkNotNull(context.packageManager.resolveContentProvider(uri.authority!!,0))
            assertNotEquals(Process.myUid(),provider.applicationInfo.uid)
            assertEquals(PackageManager.PERMISSION_GRANTED,context.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION))
            assertEquals(PackageManager.PERMISSION_DENIED,context.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            val result=validateBrowserDeviceSelectionBlocking(context,listOf(uri),arrayOf(".png"))
            assertEquals(listOf(uri),result.uris);assertNull(result.rejection)
            revoke(uri)
            assertNull(validateBrowserDeviceSelectionBlocking(context,listOf(uri),arrayOf("image/png")).uris)
        }finally{runCatching{revoke(uri)}}
    }
    @Test fun privateFilesOwnProviderAliasesAndMixedResultsNeverBecomeDeviceUploads() {
        val privateFile=File(context.filesDir,"private-uri-fixture-${UUID.randomUUID()}").apply{writeText("Synthetic private marker")}
        val external=granted()
        try {
            val forbidden=listOf(Uri.fromFile(privateFile),Uri.parse("data:text/plain,private"),Uri.parse("https://example.com/file"),
                Uri.parse("content://${context.packageName}.fileprovider/external-edit/private.png"),Uri.parse("content://${context.packageName}.hiddenbrowser/opaque/private"))
            for(uri in forbidden){val result=validateBrowserDeviceSelectionBlocking(context,listOf(uri),emptyArray());assertNull(uri.toString(),result.uris);assertNotNull(result.rejection)}
            val mixed=validateBrowserDeviceSelectionBlocking(context,listOf(external,forbidden[3]),arrayOf("image/png"))
            assertNull("No partial delivery from a mixed private URI list",mixed.uris)
            assertEquals("Synthetic private marker",privateFile.readText())
        }finally{privateFile.delete();revoke(external)}
    }
    @Test fun trustedDocumentIntentIsExplicitReadOnlyAndPreservesAllMimeAlternatives() {
        val intent=BrowserDeviceDocumentContract(true).createIntent(context,arrayOf("image/png,application/pdf",".jpg","video/*"))
        assertEquals(Intent.ACTION_OPEN_DOCUMENT,intent.action);assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));assertNotNull(intent.component)
        @Suppress("DEPRECATION") val activity=context.packageManager.getActivityInfo(intent.component!!,0)
        assertTrue(activity.exported);assertTrue((activity.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)
        assertEquals(0,intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        assertEquals(setOf("image/png","application/pdf","image/jpeg","video/*"),intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)!!.toSet())
        val uri=granted();try{assertNotNull(validateBrowserDeviceSelectionBlocking(context,listOf(uri),arrayOf("video/*")).rejection)}finally{revoke(uri)}
    }
}
