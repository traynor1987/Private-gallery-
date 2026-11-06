package uk.co.traynor.privategallery.core.hidden;
import java.io.*;
/** Small immutable policy; wall time is used only when monotonic time's boot identity changes. */
public final class HiddenPinThrottle {
    public final int failures;
    public final long elapsed, wall, boot, duration;
    private HiddenPinThrottle(int failures, long elapsed, long wall, long boot, long duration) {
        this.failures = failures; this.elapsed = elapsed; this.wall = wall; this.boot = boot; this.duration = duration;
    }
    public static HiddenPinThrottle empty() { return new HiddenPinThrottle(0, 0, 0, -1, 0); }
    public static HiddenPinThrottle recovery(long elapsed, long wall, long boot) { return new HiddenPinThrottle(9, elapsed, wall, boot, 300000); }
    public long remaining(long now) {
        if (now < elapsed) return duration;
        return Math.max(0, duration - (now - elapsed));
    }
    public HiddenPinThrottle rebase(long now, long wallNow, long bootNow) {
        if (boot == bootNow && now >= elapsed) return this;
        // Clock rollback cannot produce an indefinite deadline. Rebase once and persist it.
        long passed = wallNow >= wall ? (wallNow - wall < 0 ? Long.MAX_VALUE : wallNow - wall) : 0;
        return new HiddenPinThrottle(failures, now, wallNow, bootNow, Math.max(0, Math.min(300000, duration - Math.min(duration, passed))));
    }
    public HiddenPinThrottle failed(long now, long wallNow, long bootNow) {
        int next = Math.min(30, failures + 1);
        long delay = next < 5 ? 0 : Math.min(300000, 30000L << Math.min(4, next - 5));
        return new HiddenPinThrottle(next, now, wallNow, bootNow, delay);
    }
    public byte[] encode() {
        try { var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes); out.writeInt(1); out.writeInt(failures); out.writeLong(elapsed); out.writeLong(wall); out.writeLong(boot); out.writeLong(duration); return bytes.toByteArray(); }
        catch (IOException impossible) { throw new AssertionError(impossible); }
    }
    public static HiddenPinThrottle decode(byte[] bytes) {
        try {
            if (bytes.length != 40) throw new IllegalArgumentException("Invalid attempt record");
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            int version = in.readInt(), failures = in.readInt(); long elapsed = in.readLong(), wall = in.readLong(), boot = in.readLong(), duration = in.readLong();
            if (version != 1 || failures < 0 || failures > 30 || elapsed < 0 || wall < 0 || boot < -1 || duration < 0 || duration > 300000 || (failures < 5 && duration != 0)) throw new IllegalArgumentException("Invalid attempt record");
            return new HiddenPinThrottle(failures, elapsed, wall, boot, duration);
        } catch (IOException e) { throw new IllegalArgumentException("Invalid attempt record", e); }
    }
}
