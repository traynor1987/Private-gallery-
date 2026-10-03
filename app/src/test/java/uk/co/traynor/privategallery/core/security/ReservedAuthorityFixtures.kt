package uk.co.traynor.privategallery.core.security

import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

/** Failed release tests retain their obligation forever; never reset the production process pools. */
internal fun testPrimaryAuthority(): PrimarySessionAuthority = PrimarySessionAuthority().also {
    isolatedPool(it, "ioReleasePool", ReleasePool(16))
    isolatedPool(it, "presentationReleasePool", ReleasePool(16))
}
internal fun testSecondaryAuthority(clock: () -> Long): SecondarySessionAuthority = SecondarySessionAuthority(clock).also {
    isolatedPool(it, "ioReleasePool", ReleasePool(16))
    isolatedPool(it, "presentationReleasePool", ReleasePool(16))
}
private fun isolatedPool(authority: Any, field: String, pool: ReleasePool) {
    authority.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(authority, pool)
}
