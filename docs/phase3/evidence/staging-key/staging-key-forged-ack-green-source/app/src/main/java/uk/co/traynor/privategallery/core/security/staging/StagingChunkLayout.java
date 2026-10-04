package uk.co.traynor.privategallery.core.security.staging;

/** Fixed private temporary-file geometry, never a durable media format or key authority.
 * A future original must pin this immutable length before any private key is created. */
final class StagingChunkLayout {
    static final int CHUNK_BYTES = 65536;
    static final int TAG_BYTES = 16;
    static final long MAX_PLAINTEXT_BYTES = 8L * 1024 * 1024 * 1024;
    private final long plaintextBytes;
    private final long chunks;

    StagingChunkLayout(long plaintextBytes) {
        if (plaintextBytes < 1 || plaintextBytes > MAX_PLAINTEXT_BYTES)
            throw new IllegalArgumentException("Temporary media length unavailable");
        this.plaintextBytes = plaintextBytes;
        this.chunks = (plaintextBytes - 1) / CHUNK_BYTES + 1;
    }
    long plaintextBytes() { return plaintextBytes; }
    long chunkCount() { return chunks; }
    long ciphertextBytes() { return Math.addExact(plaintextBytes, Math.multiplyExact(chunks, TAG_BYTES)); }
    int chunkPlaintextBytes(long index) {
        requireIndex(index);
        return (int)Math.min(CHUNK_BYTES, plaintextBytes - Math.multiplyExact(index, CHUNK_BYTES));
    }
    long chunkCiphertextOffset(long index) {
        requireIndex(index);
        return Math.multiplyExact(index, CHUNK_BYTES + TAG_BYTES);
    }
    void requireFrame(long index, int length, byte[] nonce, byte[] aad) {
        requireBuffers(nonce, aad);
        if (length != chunkPlaintextBytes(index))
            throw new IllegalArgumentException("Temporary chunk length unavailable");
    }
    void writeFraming(long index, byte[] nonce, byte[] aad) {
        requireBuffers(nonce, aad);
        int length = chunkPlaintextBytes(index);
        // Public fixed framing only; every byte is assigned without a temporary allocation.
        String nonceDomain = "PGT1", aadDomain = "PGTMP001";
        for (int i = 0; i < 4; i++) nonce[i] = (byte)nonceDomain.charAt(i);
        putLittleEndian(index, nonce, 4, 8);
        for (int i = 0; i < 8; i++) aad[i] = (byte)aadDomain.charAt(i);
        putLittleEndian(plaintextBytes, aad, 8, 8);
        putLittleEndian(index, aad, 16, 8);
        putLittleEndian(length, aad, 24, 4);
        putLittleEndian(CHUNK_BYTES, aad, 28, 4);
    }
    private void requireIndex(long index) {
        if (index < 0 || index >= chunks) throw new IllegalArgumentException("Temporary chunk index unavailable");
    }
    private static void requireBuffers(byte[] nonce, byte[] aad) {
        if (nonce == null || aad == null || nonce.length != 12 || aad.length != 32 || nonce == aad)
            throw new IllegalArgumentException("Fixed temporary framing arrays required");
    }
    private static void putLittleEndian(long value, byte[] target, int offset, int bytes) {
        for (int i = 0; i < bytes; i++) target[offset + i] = (byte)(value >>> (8 * i));
    }
}
