import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.Arrays;

/** Native RandomAccessFile cursor diagnostic; not an application authentication test. */
public class VideoTamperCursorDiagnostic {
    public static void main(String[] args) throws Exception {
        var path = Files.createTempFile("synthetic-tamper-cursor", ".bin");
        byte[] before = {0x12, 0x13};
        try {
            Files.write(path,before);
            try (var file = new RandomAccessFile(path.toFile(),"rw")) {
                file.seek(0);
                file.writeByte(file.readByte() ^ 1);
            }
            if (!Arrays.equals(before,Files.readAllBytes(path))) throw new AssertionError("expected native no-op collision");
            System.out.println("PRIOR_EXPRESSION_CAN_LEAVE_BYTES_UNCHANGED=true");
            try (var file = new RandomAccessFile(path.toFile(),"rw")) {
                file.seek(0);
                int original = file.readUnsignedByte();
                file.seek(0);
                file.writeByte(original ^ 1);
            }
            if (!Arrays.equals(new byte[]{0x13,0x13},Files.readAllBytes(path))) throw new AssertionError("same-offset mutation changed neighbor");
            System.out.println("SAME_OFFSET_XOR_CHANGES_TARGET_AND_PRESERVES_NEIGHBOR=true");
        } finally { Files.deleteIfExists(path); }
    }
}
