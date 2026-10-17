package uk.co.traynor.privategallery.core.hidden;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Hidden-only v1 envelope. Never parses or writes a Primary/V2 envelope. Consumes PIN arrays. */
public final class HiddenPinEnvelope {
    private static final byte[] HEADER = new byte[] {'P', 'G', 'H', 'P', 'I', 'N', 0, 1};
    private static final byte[] AAD = "uk.co.traynor.privategallery/hidden/pin-envelope/v1".getBytes(StandardCharsets.US_ASCII);
    private static final int ITERATIONS = 600_000;
    public static final int RECORD_BYTES = 8 + 16 + 12 + 48;
    private HiddenPinEnvelope() {}
    public static final class InvalidPin extends SecurityException {
        private static final long serialVersionUID = 1L;
        private InvalidPin() { super("Hidden PIN did not authenticate"); }
    }

    public static byte[] wrap(char[] pin, byte[] key) {
        try {
            validatePin(pin);
            if (key.length != 32) throw new IllegalArgumentException("Invalid Hidden key");
            byte[] salt = new byte[16]; byte[] nonce = new byte[12];
            SecureRandom random = new SecureRandom(); random.nextBytes(salt); random.nextBytes(nonce);
            byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, pin, salt, nonce, key);
            return ByteBuffer.allocate(RECORD_BYTES).put(HEADER).put(salt).put(nonce).put(encrypted).array();
        } finally { Arrays.fill(pin, '\0'); }
    }

    public static byte[] unwrap(char[] pin, byte[] record) {
        try {
            validatePin(pin);
            if (record.length != RECORD_BYTES || !Arrays.equals(HEADER, Arrays.copyOf(record, HEADER.length)))
                throw new SecurityException("Invalid Hidden credential record");
            return crypt(Cipher.DECRYPT_MODE, pin, Arrays.copyOfRange(record, 8, 24),
                    Arrays.copyOfRange(record, 24, 36), Arrays.copyOfRange(record, 36, RECORD_BYTES));
        } finally { Arrays.fill(pin, '\0'); }
    }

    private static void validatePin(char[] pin) {
        if (pin.length < 6 || pin.length > 12) throw new IllegalArgumentException("Use 6 to 12 digits");
        for (char c : pin) if (c < '0' || c > '9') throw new IllegalArgumentException("Use digits only");
    }

    private static byte[] crypt(int mode, char[] pin, byte[] salt, byte[] nonce, byte[] input) {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, 256);
        byte[] wrapping = null;
        try {
            wrapping = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(wrapping, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD); cipher.updateAAD(HEADER);
            return cipher.doFinal(input);
        } catch (AEADBadTagException failure) {
            throw new InvalidPin();
        } catch (GeneralSecurityException failure) {
            throw new SecurityException("Hidden authentication failed", failure);
        } finally { spec.clearPassword(); if (wrapping != null) Arrays.fill(wrapping, (byte) 0); }
    }
}
