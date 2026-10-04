package uk.co.traynor.privategallery.core.security.staging;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

/** One bounded synchronous primitive call. Not authority, nonce allocation or a public media format.
 * Owning adapters must preclaim, immediately bind and exclusively borrow all caller arrays.
 * No original/descriptor/DIRECT acceptance is supplied by this package-private prerequisite. */
final class FixedStagingChunkCipher {
    private final StagingChaCha20Poly1305 engine = new StagingChaCha20Poly1305();
    private final KeyParameter key = new KeyParameter(new byte[32]);
    private final ParametersWithIV parameters = new ParametersWithIV(key, new byte[12]);
    private final AtomicBoolean claimed = new AtomicBoolean();

    int perform(boolean seal, byte[] sourceKey, byte[] nonce, byte[] aad,
                byte[] input, int length, byte[] output) throws InvalidCipherTextException {
        if (sourceKey == null || nonce == null || aad == null || input == null || output == null)
            throw new IllegalArgumentException("Fixed cipher arrays required");
        if (sourceKey == nonce || sourceKey == aad || sourceKey == input || sourceKey == output ||
            nonce == aad || nonce == input || nonce == output || aad == input || aad == output || input == output)
            throw new IllegalArgumentException("Distinct cipher arrays required");
        if (sourceKey.length != 32 || nonce.length != 12 || aad.length > 64 ||
            input.length > 65552 || length < (seal ? 0 : 16) || length > (seal ? 65536 : 65552) ||
            length > input.length || output.length > 65552 || output.length < length + (seal ? 16 : -16))
            throw new IllegalArgumentException("Fixed chunk bounds unavailable");
        if (!claimed.compareAndSet(false, true)) throw new IllegalStateException("Original cipher already consumed");
        Arrays.fill(output, (byte)0);
        try {
            System.arraycopy(sourceKey, 0, key.getKey(), 0, 32);
            System.arraycopy(nonce, 0, parameters.getIV(), 0, 12);
            engine.init(seal, parameters);
            engine.processAADBytes(aad, 0, aad.length);
            int count = engine.processBytes(input, 0, length, output, 0);
            count += engine.doFinal(output, count);
            return count;
        } catch (Throwable failure) {
            Arrays.fill(output, (byte)0);
            throw failure;
        } finally {
            try { engine.wipe(); } finally {
                try { Arrays.fill(key.getKey(), (byte)0); } finally { Arrays.fill(parameters.getIV(), (byte)0); }
            }
        }
    }
}
