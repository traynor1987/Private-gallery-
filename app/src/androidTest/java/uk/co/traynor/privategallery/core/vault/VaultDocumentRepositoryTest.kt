package uk.co.traynor.privategallery.core.vault

import android.content.ContextWrapper
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VaultDocumentRepositoryTest {
    @Test fun textRoundTripDuplicateTrashAndDownloadsRestore() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"primary-documents-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getFilesDir()=root}
        val key=ByteArray(32){41}
        val bytes="synthetic-private-document-marker\n=literal formula".toByteArray()
        var restored:android.net.Uri?=null
        try {
            val repo=AndroidVaultRepository(context,key)
            val imported=repo.importVerified(VaultImportSource("private-document.txt","text/plain",{bytes.inputStream()})) as ImportResult.Imported
            assertArrayEquals(bytes,repo.readVideoForViewing(imported.item,{false},{}))
            assertTrue(repo.importVerified(VaultImportSource("duplicate.txt","text/plain",{bytes.inputStream()})) is ImportResult.Duplicate)
            root.walkTopDown().filter {it.isFile}.forEach {assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic-private-document-marker"))}
            repo.moveToRecentlyDeleted(imported.item.id);assertTrue(repo.items().isEmpty())
            repo.restoreRecentlyDeleted(imported.item.id)
            restored=repo.restore(imported.item)
            base.contentResolver.openInputStream(checkNotNull(restored)).use {assertArrayEquals(bytes,checkNotNull(it).readBytes())}
            base.contentResolver.query(checkNotNull(restored),arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),null,null,null).use {cursor ->
                assertTrue(checkNotNull(cursor).moveToFirst());assertTrue(cursor.getString(0).startsWith("Download/"))
            }
        }finally {restored?.let {base.contentResolver.delete(it,null,null)};key.fill(0);bytes.fill(0);root.deleteRecursively()}
    }
    @Test fun cancelledOrInvalidDocumentCannotBecomeComplete() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"primary-documents-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getFilesDir()=root}
        val key=ByteArray(32){42}
        try {
            val repo=AndroidVaultRepository(context,key)
            assertTrue(runCatching {repo.importVerified(VaultImportSource("error.pdf","application/pdf",{"<html>not pdf</html>".byteInputStream()}))}.isFailure)
            assertTrue(runCatching {repo.importVerified(VaultImportSource("cancelled.txt","text/plain",{"not saved".byteInputStream()},isCancelled={true}))}.isFailure)
            assertTrue(repo.items().isEmpty());assertTrue(root.walkTopDown().none {it.extension=="part"})
        }finally {key.fill(0);root.deleteRecursively()}
    }
}
