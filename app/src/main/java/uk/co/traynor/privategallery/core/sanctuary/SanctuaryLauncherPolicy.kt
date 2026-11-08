package uk.co.traynor.privategallery.core.sanctuary

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.content.Context
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Android app policy is shared with Guardian. Launcher layout state never supplies approvals. */
internal object SanctuaryLauncherPolicy {
    enum class Decision {
        APPROVED, UNAPPROVED, ESSENTIAL, TEMPORARILY_PERMITTED, UNRESTRICTED;

        val canLaunch: Boolean get() = this != UNAPPROVED
    }

    private const val OUTSIDE = "protect-outside-approved"
    private val temporary = SanctuaryTemporaryAccess70(SystemClock::elapsedRealtime)
    private val revision = MutableStateFlow(0L)
    private val updates = revision.asStateFlow()
    @Volatile private var visible = false

    @Suppress("UNUSED_PARAMETER")
    fun state(context: Context): StateFlow<Long> = updates

    fun decision(context: Context, packageName: String): Decision =
        decisions(context, listOf(packageName)).getValue(packageName)

    fun decisions(context: Context, packageNames: Collection<String>): Map<String, Decision> {
        val essential = SanctuaryGuardian.essentialPackages(context)
        val session = SanctuaryStore.get(context).refresh()
        val approved = session?.let(::approvedAndroidApps).orEmpty()
        val permits = session?.let(::temporaryPackages).orEmpty()
        val paused = session?.let(::isPaused) == true
        return packageNames.associateWith { name ->
            when {
                name in essential -> Decision.ESSENTIAL
                session == null -> Decision.UNRESTRICTED
                name in approved -> Decision.APPROVED
                paused || name in permits -> Decision.TEMPORARILY_PERMITTED
                else -> Decision.UNAPPROVED
            }
        }
    }

    fun launcherProtectionActive(context: Context): Boolean =
        SanctuaryStore.get(context).refresh()?.let { !isPaused(it) } == true

    fun outsideProtectionEnabled(context: Context): Boolean = preferences(context).getBoolean(OUTSIDE, false)

    /** Standard Guardian coverage remains active even when this optional shield is off. */
    fun setOutsideProtectionEnabled(context: Context, enabled: Boolean): Boolean {
        val changed = preferences(context).edit().putBoolean(OUTSIDE, enabled).commit()
        if (changed) signalPolicyChange()
        return changed
    }

    fun openHomeSettings(context: Context): Boolean = SanctuaryGuardian.openHomeSettings(context)

    fun openEmergencyPhone(context: Context): Boolean = SanctuaryGuardian.openPhone(context)

    @Suppress("UNUSED_PARAMETER")
    fun launcherResumed(context: Context) {
        visible = true
        SanctuaryAccessibility.signalPolicyChange()
    }

    fun launcherPaused() {
        visible = false
        SanctuaryAccessibility.signalPolicyChange()
    }

    internal fun launcherVisible(): Boolean = visible

    fun authenticateTemporaryAccess(
        activity: Activity,
        packageName: String?,
        onResult: (Boolean) -> Unit,
    ): CancellationSignal? {
        val store = SanctuaryStore.get(activity)
        val session = store.refresh() ?: return null
        if (activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true) return null
        if (packageName != null && decision(activity, packageName) != Decision.UNAPPROVED) return null

        val request = temporary.request(session.id, packageName)
        val cancellation = CancellationSignal()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val confirmed = !cancellation.isCanceled && !activity.isFinishing && !activity.isDestroyed &&
                    result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
                val granted = temporary.complete(request, store.refresh()?.id, confirmed)
                if (granted) {
                    signalPolicyChange()
                    Handler(Looper.getMainLooper()).postDelayed(::signalPolicyChange, SanctuaryTemporaryAccess70.ACCESS_DURATION)
                }
                onResult(granted)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                temporary.complete(request, null, confirmed = false)
                if (!activity.isDestroyed) onResult(false)
            }
        }
        try {
            BiometricPrompt.Builder(activity)
                .setTitle(if (packageName == null) "Pause Sanctuary for five minutes" else "Allow this app for five minutes")
                .setSubtitle("Confirm your Android screen lock")
                .setAllowedAuthenticators(DEVICE_CREDENTIAL)
                .build()
                .authenticate(cancellation, activity.mainExecutor, callback)
        } catch (_: IllegalArgumentException) {
            temporary.complete(request, null, confirmed = false)
            return null
        } catch (_: IllegalStateException) {
            temporary.complete(request, null, confirmed = false)
            return null
        } catch (_: SecurityException) {
            temporary.complete(request, null, confirmed = false)
            return null
        }
        return cancellation
    }

    internal fun temporaryPackages(session: SanctuarySession): Set<String> = temporary.packages(session.id)

    internal fun isPaused(session: SanctuarySession): Boolean = temporary.paused(session.id)

    internal fun signalPolicyChange() {
        revision.update { it + 1 }
        SanctuaryAccessibility.signalPolicyChange()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences("sanctuary-launcher-policy", Context.MODE_PRIVATE)
}

internal fun approvedAndroidApps(session: SanctuarySession): Set<String> =
    if (session.allowExternalApps) session.androidApps else emptySet()
