package uk.co.traynor.privategallery.core.guardian

import android.app.role.RoleManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

class GuardianPhoneScreeningService : CallScreeningService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        var answered = false
        fun respond(mode: GuardianCallMode) {
            if (answered) return
            answered = true
            val response = CallResponse.Builder().apply {
                when (mode) {
                    GuardianCallMode.ALLOW -> Unit
                    GuardianCallMode.SILENCE -> setSilenceCall(true)
                    GuardianCallMode.BLOCK -> {
                        setDisallowCall(true)
                        setRejectCall(true)
                        setSkipNotification(true)
                    }
                }
            }.build()
            respondToCall(details, response)
        }

        val fallback = Runnable { respond(GuardianCallMode.ALLOW) }
        main.postDelayed(fallback, 2_000L)
        worker.execute {
            val mode = runCatching {
                val number = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
                val emergency = number == null ||
                    getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) != false
                val roleHeld = getSystemService(RoleManager::class.java)
                    ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
                if (emergency || !roleHeld) GuardianCallMode.ALLOW
                else {
                    val archive = GuardianCaptureStore(this)
                    val (enabled, rules) = archive.phoneShieldSettings()
                    val saved = guardianPhoneAction(enabled, roleHeld, number, rules)
                    val overrides = archive.phoneShieldSanctuaryModes()
                    val session = if (saved == GuardianCallMode.SILENCE && overrides.isNotEmpty())
                        SanctuaryStore.get(this).refresh() else null
                    strengthenGuardianCallMode(saved, session?.mode?.name, overrides)
                }
            }.getOrDefault(GuardianCallMode.ALLOW)
            main.post {
                main.removeCallbacks(fallback)
                respond(mode)
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
