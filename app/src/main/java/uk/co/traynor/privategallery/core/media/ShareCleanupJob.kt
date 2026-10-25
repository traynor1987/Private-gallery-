package uk.co.traynor.privategallery.core.media

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/** Best-effort disk cleanup survives process death; provider expiry never relies on scheduler timing. */
class ShareCleanupJob:JobService() {
 override fun onStartJob(params:JobParameters):Boolean {
  uk.co.traynor.privategallery.ui.cleanupShareCopies(this)
  ExternalEditLease.cleanup(java.io.File(cacheDir,"external-edit"),System.currentTimeMillis())
  jobFinished(params,false)
  return false
 }
 override fun onStopJob(params:JobParameters)=true
 companion object {
  fun schedule(context:Context) {
   val scheduler=context.getSystemService(JobScheduler::class.java)
   scheduler.schedule(JobInfo.Builder(17437,ComponentName(context,ShareCleanupJob::class.java))
    .setMinimumLatency(15*60*1000L).setOverrideDeadline(30*60*1000L).build())
  }
 }
}
