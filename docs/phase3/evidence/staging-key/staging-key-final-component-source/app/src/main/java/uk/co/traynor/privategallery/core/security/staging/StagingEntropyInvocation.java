package uk.co.traynor.privategallery.core.security.staging;

/** Immutable code binding only. The exact acknowledged key root owns all target/scratch lifetime.
 * No provider, alternate implementation, key getter or randomness fallback. */
final class StagingEntropyInvocation {
    static { System.loadLibrary("pg_staging_entropy"); }
    private StagingEntropyInvocation() { }
    static int fill(byte[] target) { return fillNative(target); }
    private static native int fillNative(byte[] target);
}
