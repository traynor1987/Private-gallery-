package uk.co.traynor.privategallery.core.security.staging;

/** Per-instance frame claims only. The owned adapter must bind one sequence to one fresh key,
 * retire it on every post-claim failure, and authenticate before private output publication.
 * Constructing another sequence never grants permission to reuse a key or nonce. */
final class StagingSealSequence {
    private final StagingChunkLayout layout;
    private long next;
    private boolean retired;
    StagingSealSequence(StagingChunkLayout layout) {
        if (layout == null) throw new IllegalArgumentException("Temporary layout required");
        this.layout = layout;
    }
    synchronized void claim(long index, int length, byte[] nonce, byte[] aad) {
        layout.requireFrame(index, length, nonce, aad);
        if (retired || index != next) throw new IllegalStateException("Temporary frame already consumed or unavailable");
        // Consume before any cipher/key use. The only following operation writes public fixed bytes.
        next++;
        try { layout.writeFraming(index, nonce, aad); }
        catch (Throwable failure) { retired = true; throw failure; }
    }
    synchronized void retire() { retired = true; }
}
