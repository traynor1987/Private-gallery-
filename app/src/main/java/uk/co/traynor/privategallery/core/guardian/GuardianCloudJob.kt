package uk.co.traynor.privategallery.core.guardian

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.CloudFailure
import uk.co.traynor.privategallery.core.brain.CloudLesson
import uk.co.traynor.privategallery.core.brain.CloudTeacherClient
import uk.co.traynor.privategallery.core.brain.CloudTeacherException
import uk.co.traynor.privategallery.core.brain.CloudTeacherTransport
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.brain.cloudRequest

internal data class GuardianCloudWork(
    val evidenceId: String,
    val requestId: String,
    val revision: String,
    val createdAt: Long,
    val configuration: NovaBrainConfiguration,
) {
    override fun toString() = "GuardianCloudWork(redacted)"
}

internal data class GuardianCloudAdvisory(val lesson: CloudLesson, val model: String, val at: Long)

/** The encrypted evidence row owns the queue entry. Android persists only a wake-up. */
class GuardianCloudJob : JobService() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            val wake = withContext(Dispatchers.IO) {
                val store = GuardianCaptureStore(applicationContext)
                try {
                    for (index in 0 until GuardianAutomaticCloudPolicy.MAX_PENDING) {
                        val item = store.nextAutomaticCloud(System.currentTimeMillis()) ?: break
                        analyze(store, item)
                    }
                    store.nextAutomaticCloudWake(System.currentTimeMillis())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Absence of readable encrypted state never authorizes a paid request.
                    null
                }
            }
            jobFinished(params, false)
            if (wake != null) schedule(applicationContext, wake)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true // The persisted queue rejects expired and already claimed entries.
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun analyze(store: GuardianCaptureStore, work: GuardianCloudWork) {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork
        if (connectivity == null || network == null || !validated(connectivity, network)) {
            store.deferAutomaticCloud(work, System.currentTimeMillis(), retry = true)
            return
        }
        val client = CloudTeacherClient(CloudTeacherTransport { endpoint, token, payload ->
            cloudRequest(endpoint, token, payload, network::openConnection)
        })
        val status = try {
            val initial = client.status(work.configuration)
            if (!initial.canAnalyze || !initial.automaticAllowed) {
                store.deferAutomaticCloud(work, System.currentTimeMillis(), retry = false)
                return
            }
            val first = SystemClock.elapsedRealtime()
            delay(GuardianAutomaticCloudPolicy.STABILITY_MS)
            if (!GuardianAutomaticCloudPolicy.ready(first, SystemClock.elapsedRealtime(), validated(connectivity, network))) {
                store.deferAutomaticCloud(work, System.currentTimeMillis(), retry = true)
                return
            }
            client.status(work.configuration)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CloudTeacherException) {
            store.deferAutomaticCloud(work, System.currentTimeMillis(), retry = error.failure == CloudFailure.OFFLINE)
            return
        }
        if (!status.canAnalyze || !status.automaticAllowed || !validated(connectivity, network)) {
            store.deferAutomaticCloud(work, System.currentTimeMillis(), retry = false)
            return
        }
        val parent = kotlinx.coroutines.currentCoroutineContext()
        if (!parent.isActive) return
        val excerpt = store.claimAutomaticCloud(work, System.currentTimeMillis()) ?: return
        try {
            val reply = client.analyze(work.configuration, excerpt, approved = true, requestId = work.requestId,
                status = status, permission = {
                    validated(connectivity, network) && store.automaticCloudMaySend(work)
                }, cancelled = { !parent.isActive }, automatic = true)
            store.finishAutomaticCloud(work, GuardianCloudState.COMPLETE,
                GuardianCloudAdvisory(reply.lesson, status.model, System.currentTimeMillis()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: CloudTeacherException) {
            store.finishAutomaticCloud(work,
                if (error.failure.mayHaveCharged) GuardianCloudState.UNKNOWN else GuardianCloudState.UNAVAILABLE, null)
        }
    }

    private fun validated(manager: ConnectivityManager, network: Network): Boolean {
        if (manager.activeNetwork != network) return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    internal companion object {
        private const val ID = 0x4e434790

        fun schedule(context: Context, wakeAt: Long? = null) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(ID) != null) return
            val now = System.currentTimeMillis()
            val wake = wakeAt ?: GuardianCaptureStore(context).nextAutomaticCloudWake(now) ?: return
            val job = JobInfo.Builder(ID, ComponentName(context, GuardianCloudJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency((wake - now).coerceAtLeast(0))
                .setPersisted(true)
                .build()
            check(scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(ID)
        }
    }
}
