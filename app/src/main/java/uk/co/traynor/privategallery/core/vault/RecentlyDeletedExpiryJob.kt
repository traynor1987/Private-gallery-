package uk.co.traynor.privategallery.core.vault

import android.app.job.*
import android.content.ComponentName
import java.lang.ref.WeakReference
import uk.co.traynor.privategallery.MainActivity

/** No persisted key, media identifier or metadata. Locked/process-dead vaults reconcile on unlock. */
class RecentlyDeletedExpiryJob:JobService() {
    override fun onStartJob(params:JobParameters):Boolean {
        val activity=owner.get() ?: return false
        activity.runOnUiThread {activity.reconcileTrashExpiry {jobFinished(params,false)}}
        return true
    }
    override fun onStopJob(params:JobParameters)=true
    companion object {
        private var owner=WeakReference<MainActivity>(null)
        fun register(activity:MainActivity) {
            owner=WeakReference(activity)
            val scheduler=activity.getSystemService(JobScheduler::class.java)
            scheduler.schedule(JobInfo.Builder(17746,ComponentName(activity,RecentlyDeletedExpiryJob::class.java))
                .setPeriodic(6*60*60*1000L).setPersisted(true).build())
        }
    }
}
