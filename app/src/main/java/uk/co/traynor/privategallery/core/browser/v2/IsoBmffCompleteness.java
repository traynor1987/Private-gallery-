package uk.co.traynor.privategallery.core.browser.v2;

import java.io.IOException;
import java.util.Arrays;

/** Strict boundary preflight for self-contained progressive ISO BMFF media; not a codec decoder. */
public final class IsoBmffCompleteness {
    private IsoBmffCompleteness() { }
    @FunctionalInterface public interface Reader {
        int readAt(long position, byte[] target, int offset, int length) throws IOException;
    }
    @FunctionalInterface public interface Cancellation { void check() throws IOException; }

    /** Source length is authenticated by the caller. Never allocates a box-sized buffer. */
    public static void validate(long size, Reader reader, Cancellation cancellation) throws IOException {
        cancellation.check();
        if (size < 32) throw invalid();
        byte[] header = new byte[16];
        long position = 0;
        boolean ftyp = false, moov = false, mdat = false;
        int boxes = 0;
        try {
            while (position < size) {
                cancellation.check();
                if (++boxes > 1_000_000 || size - position < 8) throw invalid();
                readFully(reader, cancellation, position, header, 8);
                long boxSize = uint32(header, 0);
                int headerSize = 8;
                String type = new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if (boxSize == 1) {
                    if (size - position < 16) throw invalid();
                    readFully(reader, cancellation, position + 8, header, 8);
                    // ISO's unsigned 64-bit lengths outside Java's positive range cannot fit this source.
                    if ((header[0] & 0x80) != 0) throw invalid();
                    boxSize = (uint32(header, 0) << 32) | uint32(header, 4);
                    headerSize = 16;
                } else if (boxSize == 0) {
                    boxSize = size - position;
                }
                // Subtraction avoids overflow from attacker-controlled lengths.
                if (boxSize < headerSize || boxSize > size - position) throw invalid();
                if ("uuid".equals(type) && boxSize - headerSize < 16) throw invalid();
                if ("moof".equals(type) || "mfra".equals(type)) throw invalid();
                if (position == 0 && !"ftyp".equals(type)) throw invalid();
                switch (type) {
                    case "ftyp":
                        if (ftyp || position != 0 || boxSize - headerSize < 8 || (boxSize - headerSize) % 4 != 0) throw invalid();
                        ftyp = true;
                        break;
                    case "moov":
                        if (moov || boxSize <= headerSize) throw invalid();
                        moov = true;
                        break;
                    case "mdat":
                        if (boxSize <= headerSize) throw invalid();
                        mdat = true;
                        break;
                    default: break;
                }
                position += boxSize; // bounded by size-position above
            }
            cancellation.check();
            if (!ftyp || !moov || !mdat || position != size) throw invalid();
        } finally { Arrays.fill(header, (byte) 0); }
    }
    private static void readFully(Reader reader, Cancellation cancellation, long position, byte[] target, int length) throws IOException {
        int read = 0;
        while (read < length) {
            cancellation.check();
            int count = reader.readAt(position + read, target, read, length - read);
            if (count <= 0 || count > length - read) throw invalid();
            read += count;
        }
    }
    private static long uint32(byte[] bytes, int offset) {
        return ((bytes[offset] & 255L) << 24) | ((bytes[offset + 1] & 255L) << 16)
            | ((bytes[offset + 2] & 255L) << 8) | (bytes[offset + 3] & 255L);
    }
    private static IOException invalid() { return new IOException("Incomplete or unsupported ISO BMFF structure"); }
}
