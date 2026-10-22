package uk.co.traynor.privategallery.core.browser.v2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Standalone host checks against the exact instrumentation fixture, no Android parser involved. */
public final class IsoBmffCompletenessChecks {
    private IsoBmffCompletenessChecks() { }
    private static int checks;
    private static void validate(byte[] bytes) throws IOException {
        IsoBmffCompleteness.validate(bytes.length, (position, target, offset, length) -> {
            if (position >= bytes.length) return -1;
            int count = Math.min(length, bytes.length - (int) position);
            System.arraycopy(bytes, (int) position, target, offset, count);
            return count;
        }, () -> { });
    }
    private static void rejects(byte[] bytes) throws Exception {
        try { validate(bytes); throw new AssertionError("Accepted incomplete structure of length " + bytes.length); }
        catch (IOException expected) { checks++; }
    }
    private static byte[] box(String type, int size) {
        ByteBuffer b = ByteBuffer.allocate(size);
        b.putInt(size).put(type.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        return b.array();
    }
    private static byte[] concat(byte[]... arrays) {
        int size = Arrays.stream(arrays).mapToInt(a -> a.length).sum();
        ByteBuffer b = ByteBuffer.allocate(size);
        for (byte[] a : arrays) b.put(a);
        return b.array();
    }
    public static void main(String[] args) throws Exception {
        byte[] fixture = Files.readAllBytes(Path.of(args[0]));
        validate(fixture); checks++;
        for (int end = 0; end < fixture.length; end++) rejects(Arrays.copyOf(fixture, end));
        byte[] ftyp = box("ftyp", 16), moov = box("moov", 12), mdat = box("mdat", 12);
        validate(concat(ftyp, moov, mdat)); checks++;
        byte[] extended = ByteBuffer.allocate(20).putInt(1).put("mdat".getBytes()).putLong(20).putInt(42).array();
        validate(concat(ftyp, moov, extended)); checks++;
        byte[] toEof = mdat.clone(); ByteBuffer.wrap(toEof).putInt(0);
        validate(concat(ftyp, moov, toEof)); checks++;
        rejects(concat(ftyp, moov, box("moof", 12), mdat));
        rejects(concat(ftyp, mdat)); rejects(concat(moov, mdat)); rejects(concat(ftyp, moov));
        rejects(concat(ftyp, moov, ByteBuffer.allocate(8).putInt(-1).put("mdat".getBytes()).array()));
        rejects(concat(ftyp, moov, ByteBuffer.allocate(16).putInt(1).put("mdat".getBytes()).putLong(Long.MAX_VALUE).array()));
        rejects(concat(ftyp, moov, ByteBuffer.allocate(16).putInt(1).put("mdat".getBytes()).putLong(Long.MIN_VALUE).array()));
        rejects(concat(ftyp, moov, ByteBuffer.allocate(16).putInt(1).put("mdat".getBytes()).putLong(8).array()));
        rejects(concat(ftyp, moov, box("mdat", 8)));
        for (int invalidSize = 2; invalidSize < 8; invalidSize++) {
            rejects(concat(ftyp, moov, ByteBuffer.allocate(8).putInt(invalidSize).put("mdat".getBytes()).array()));
        }
        rejects(concat(ftyp, moov, ByteBuffer.allocate(16).putInt(1).put("mdat".getBytes()).putLong(15).array()));
        rejects(concat(ftyp, moov, moov, mdat));
        rejects(concat(ftyp, box("uuid", 12), moov, mdat));
        // Partial source reads are legal; reconstruct every header without assuming one read fills it.
        IsoBmffCompleteness.validate(fixture.length, (position, target, offset, length) -> {
            if (position >= fixture.length) return -1;
            target[offset] = fixture[(int) position]; return 1;
        }, () -> { }); checks++;
        final int[] cancellationChecks = {0};
        try {
            IsoBmffCompleteness.validate(fixture.length, (position, target, offset, length) -> {
                System.arraycopy(fixture, (int) position, target, offset, length); return length;
            }, () -> { if (++cancellationChecks[0] == 5) throw new IOException("cancelled during parse"); });
            throw new AssertionError("mid-parse cancellation ignored");
        } catch (IOException expected) { checks++; }

        rejects(concat(ftyp, moov, mdat, new byte[]{1}));
        // Read EOF/stall and cancellation must fail even when the advertised size is complete.
        try { IsoBmffCompleteness.validate(fixture.length, (p,b,o,n) -> -1, () -> { }); throw new AssertionError("EOF accepted"); }
        catch (IOException expected) { checks++; }
        try { IsoBmffCompleteness.validate(fixture.length, (p,b,o,n) -> 0, () -> { }); throw new AssertionError("stalled read accepted"); }
        catch (IOException expected) { checks++; }
        try { IsoBmffCompleteness.validate(fixture.length, (p,b,o,n) -> { throw new AssertionError("read after cancellation"); },
            () -> { throw new IOException("cancelled"); }); throw new AssertionError("cancellation ignored"); }
        catch (IOException expected) { checks++; }
        System.out.println("ISO BMFF completeness checks passed: " + checks + "; all " + fixture.length + " fixture truncations rejected");
    }
}
