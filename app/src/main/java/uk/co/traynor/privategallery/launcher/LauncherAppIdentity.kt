package uk.co.traynor.privategallery.launcher

/** The profile serial is device-local. A bare component retains its legacy current-profile meaning. */
@ConsistentCopyVisibility
internal data class LauncherAppIdentity private constructor(
    val flattenedComponent: String,
    val profileSerial: Long?,
) {
    val key: String
        get() = profileSerial?.let { "$flattenedComponent#$it" } ?: flattenedComponent

    companion object {
        private val componentPattern = Regex(
            "[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*/\\.?[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*",
        )

        fun forComponent(flattenedComponent: String, profileSerial: Long?): LauncherAppIdentity? {
            if (flattenedComponent.length !in 3..512 || !componentPattern.matches(flattenedComponent)) return null
            if (profileSerial != null && profileSerial < 0) return null
            return LauncherAppIdentity(flattenedComponent, profileSerial)
        }

        fun parse(key: String): LauncherAppIdentity? {
            if (key.length !in 3..532) return null
            val separator = key.indexOf('#')
            if (separator < 0) return forComponent(key, null)
            val suffix = key.substring(separator + 1)
            val serial = suffix.toLongOrNull() ?: return null
            if (serial.toString() != suffix) return null
            return forComponent(key.substring(0, separator), serial)
        }

        fun isValidKey(key: String): Boolean = parse(key) != null
    }
}
