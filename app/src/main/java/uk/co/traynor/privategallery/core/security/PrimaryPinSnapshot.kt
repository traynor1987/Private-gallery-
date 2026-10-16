package uk.co.traynor.privategallery.core.security

import android.content.SharedPreferences
import uk.co.traynor.privategallery.core.crypto.PinWrappedKey

/** Read one encrypted preference record, then unwrap outside the application's
 * preference monitor. Returned collections are read only. Android's inspected
 * SharedPreferencesImpl copies getAll under its internal update lock; this does
 * not claim a public detached-map guarantee or complete KDF/provider ownership.
 * The caller retains the original authentication/temporary-key result owner. */
internal fun unlockPrimaryPinSnapshot(
    preferences: SharedPreferences,
    pin: CharArray,
    decode: (String?) -> ByteArray,
    unwrap: (CharArray, PinWrappedKey) -> ByteArray,
): ByteArray = try {
    check(!Thread.holdsLock(preferences)) { "PIN read under preference monitor" }
    val snapshot = preferences.all
    val envelope = PinWrappedKey(
        decode(snapshot["salt"] as? String),
        decode(snapshot["nonce"] as? String),
        decode(snapshot["ciphertext"] as? String),
    )
    unwrap(pin, envelope)
} finally { pin.fill('\u0000') }
