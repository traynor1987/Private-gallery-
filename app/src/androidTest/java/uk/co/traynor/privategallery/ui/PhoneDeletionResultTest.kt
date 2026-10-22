package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity

/** Real MediaStore queries and Activity result handling; system prompt UI remains a physical acceptance gate. */
class PhoneDeletionResultTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun finish(code:Int) = MainActivity::class.java.getDeclaredMethod("finishPhoneSourceDeletion",Int::class.javaPrimitiveType)
        .apply {isAccessible=true}.invoke(compose.activity,code)
    private fun field(name:String,value:Any) {MainActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.set(compose.activity,value)}
    private fun disposable(block:(Uri)->Unit) {
        val resolver=compose.activity.contentResolver
        val uri=checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"disposable-deletion-${UUID.randomUUID()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
        }))
        try {resolver.openOutputStream(uri)!!.use {it.write(byteArrayOf(1,2,3))};block(uri)}
        // A test may already remove this disposable row. MediaStore can revoke
        // ownership immediately and reject a second delete; cleanup must not mask assertions.
        finally {runCatching {resolver.delete(uri,null,null)}}
    }
    private fun result(uri:Uri,code:Int):String {
        val message=AtomicReference<String>()
        compose.runOnIdle {
            field("pendingSourceDeletion",listOf(uri));field("pendingVerifiedPhoneCopies",1)
            field("pendingSourceDeletionCompletion",{value:String -> message.set(value)})
            finish(code)
        }
        compose.waitUntil(10_000) {message.get()!=null}
        return message.get()
    }
    @Test fun deniedDeletionRetainsSourceAndReportsCopyOnly() = disposable {uri ->
        assertTrue(result(uri,Activity.RESULT_CANCELED).contains("0 originals removed"))
        assertArrayEquals(byteArrayOf(1,2,3),compose.activity.contentResolver.openInputStream(uri)!!.use {it.readBytes()})
    }
    @Test fun approvedResultAloneDoesNotClaimOrPerformDeletion() = disposable {uri ->
        assertTrue(result(uri,Activity.RESULT_OK).contains("0 originals removed"))
        assertNotNull(compose.activity.contentResolver.openInputStream(uri)?.use {it.read()})
        compose.activity.contentResolver.delete(uri,null,null) // Simulate confirmed platform removal of disposable media.
        assertTrue(result(uri,Activity.RESULT_OK).contains("1 originals removed"))
    }
    @Test fun resultWithoutReceiptAfterRecreationDoesNotInventRetentionOrResumeDeletion() = disposable {uri ->
        compose.runOnIdle {field("pendingSourceDeletion",listOf(uri));field("pendingVerifiedPhoneCopies",1)}
        compose.activityRule.scenario.recreate()
        compose.runOnIdle {
            finish(Activity.RESULT_OK)
            @Suppress("UNCHECKED_CAST")
            val status=MainActivity::class.java.getDeclaredField("phoneMoveStatus\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<String>
            assertTrue(status.value.contains("Counts are unavailable"))
            assertTrue(status.value.contains("No further deletion was requested"))
        }
    }
}
