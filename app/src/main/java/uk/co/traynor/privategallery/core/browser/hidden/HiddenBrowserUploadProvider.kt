package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns

/** Not exported. Only authenticated, explicitly selected non-vaultOnly Hidden media is readable. */
class HiddenBrowserUploadProvider:ContentProvider() {
    override fun onCreate()=true
    private fun access(uri:Uri):Pair<HiddenVpnBrowserBroker,String> {
        check(Binder.getCallingUid()==Process.myUid())
        require(uri.pathSegments.size==2)
        return HiddenVpnBrowserBroker.find(uri.pathSegments[0]) to uri.pathSegments[1]
    }
    override fun getType(uri:Uri):String {val (broker,grant)=access(uri);return broker.uploadInfo(grant).mimeType}
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {require(mode=="r");val (broker,grant)=access(uri);return broker.openUpload(grant)}
    override fun openAssetFile(uri:Uri,mode:String):android.content.res.AssetFileDescriptor {
        require(mode=="r");val (broker,grant)=access(uri)
        val descriptor=broker.openUpload(grant)
        return try {android.content.res.AssetFileDescriptor(descriptor,0,descriptor.statSize.also {require(it>=0)})}
        catch(failure:Throwable) {descriptor.close();throw failure}
    }
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor {
        val (broker,grant)=access(uri);val item=broker.uploadInfo(grant)
        val columns=projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)
        return MatrixCursor(columns).apply {addRow(columns.map {when(it){OpenableColumns.DISPLAY_NAME -> item.displayName;OpenableColumns.SIZE -> item.plaintextSize;else -> null}})}
    }
    override fun insert(uri:Uri,values:ContentValues?):Uri?=throw UnsupportedOperationException()
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?):Int=throw UnsupportedOperationException()
}
