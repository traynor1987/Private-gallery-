package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.security.MessageDigest
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** Foreground transfer bound to one independently authenticated destination. Never deletes a source. */
internal class PhoneVaultTransfer(
    private val context: Context,
    primaryKey: ByteArray,
    hidden: HiddenSession?,
    private val publish: ((() -> Unit)->Unit) = {it()},
    private val authorized: () -> Boolean,
) {
    private val primary = if(hidden == null) AndroidVaultRepository(context,primaryKey) else null
    private val target = hidden?.let { HiddenRepository(context,it,publishIfAllowed={ publish -> check(authorized());this.publish(publish) }) }

    private val verifiedCopies=java.util.IdentityHashMap<VaultItem,Uri>()

    fun copy(uri: Uri): VaultItem {
        check(authorized())
        val item=target?.import(uri) ?: when(val result=VaultImportCoordinator(primary!!).acquire(VaultImportSource(
            displayName=context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst())it.getString(0)else null} ?: "Imported media",
            mimeType=context.contentResolver.getType(uri) ?: "application/octet-stream",
            openStream={checkNotNull(context.contentResolver.openInputStream(uri))},sourceReference=uri.toString(),
            isCancelled={!authorized()},publishIfAllowed={ commit -> publish {check(authorized());commit()} },
        ))) {
            is ImportResult.Imported -> result.item
            is ImportResult.Duplicate -> result.existing
        }
        if(target != null) target.verify(item.id) else primary!!.withHiddenCopySource(item.id,{!authorized()}) { _, input ->
            val buffer=ByteArray(64*1024)
            try { while(input.read(buffer)>=0) check(authorized()) } finally {buffer.fill(0)}
        }
        check(authorized())
        verifiedCopies[item]=uri
        return item
    }

    /** The exact selected URI must still contain the verified payload. Unknown providers remain copy-only. */
    fun deletionSource(uri: Uri, verified: VaultItem): Uri? {
        check(authorized())
        check(verifiedCopies[verified]==uri) {"No verified copy for this source"}
        if(uri.scheme!="content" || uri.authority!=MediaStore.AUTHORITY) return null
        val digest=MessageDigest.getInstance("SHA-256")
        var size=0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer=ByteArray(64*1024)
            try { while(true) {
                check(authorized());val n=input.read(buffer);if(n<0)break
                size+=n;check(size<=verified.plaintextSize);digest.update(buffer,0,n)
            } } finally {buffer.fill(0)}
        } ?: error("Source unavailable")
        check(authorized())
        check(size==verified.plaintextSize && MessageDigest.isEqual(digest.digest(),verified.plaintextSha256))
        return uri
    }
}
