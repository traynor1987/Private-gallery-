package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import uk.co.traynor.privategallery.MainActivity

/** Tracks only this launch's privacy handoff; Android still decides every permission grant. */
internal class PrivacyPermissionContract(private val owner:MainActivity?) : ActivityResultContract<Array<String>,Map<String,Boolean>>() {
    private val delegate=ActivityResultContracts.RequestMultiplePermissions()
    private val pending=java.util.ArrayDeque<Long>()
    override fun getSynchronousResult(context:Context,input:Array<String>):ActivityResultContract.SynchronousResult<Map<String,Boolean>>? =
        delegate.getSynchronousResult(context,input)
    override fun createIntent(context:Context,input:Array<String>):Intent {
        val intent=delegate.createIntent(context,input)
        owner?.beginPermissionWindow()?.let {pending.addLast(it)}
        return intent
    }
    override fun parseResult(resultCode:Int,intent:Intent?):Map<String,Boolean> {
        pending.pollFirst()?.let {owner?.endPermissionWindow(it)}
        return delegate.parseResult(resultCode,intent)
    }
    fun launch(launcher:ActivityResultLauncher<Array<String>>,permissions:Array<String>) {
        val previous=pending.toSet()
        try {launcher.launch(permissions)}
        catch(failure:Exception) {
            pending.toList().filterNot {it in previous}.forEach {token->
                pending.remove(token)
                owner?.endPermissionWindow(token)
            }
            throw failure
        }
    }
    fun close() {
        while(pending.isNotEmpty()) {
            val token=pending.removeFirst()
            owner?.endPermissionWindow(token)
        }
    }
}
