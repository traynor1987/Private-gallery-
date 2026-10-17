package uk.co.traynor.privategallery.core.hidden;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** Dependency-free behavioral checks; also invoked by the JVM JUnit bridge. Synthetic data only. */
public final class HiddenFoundationChecks {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    private static void rejected(Runnable action) {
        try { action.run(); } catch (SecurityException | IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args) {
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 91);
        char[] pin = "728491".toCharArray();
        byte[] envelope = HiddenPinEnvelope.wrap(pin, key);
        check(Arrays.equals(pin, new char[6]));
        check(envelope.length == HiddenPinEnvelope.RECORD_BYTES);
        check(Arrays.equals(key, HiddenPinEnvelope.unwrap("728491".toCharArray(), envelope)));
        rejected(() -> HiddenPinEnvelope.unwrap("123456".toCharArray(), envelope));
        byte[] corrupt = envelope.clone(); corrupt[corrupt.length - 1] ^= 1;
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), corrupt));
        byte[] wrongVersion = envelope.clone(); wrongVersion[7] ^= 1;
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), wrongVersion));
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), Arrays.copyOf(envelope, envelope.length + 1)));
        rejected(() -> HiddenPinEnvelope.wrap("123".toCharArray(), key));
        byte[] other = HiddenPinEnvelope.wrap("728491".toCharArray(), key);
        check(!Arrays.equals(envelope, other));
        AtomicLong now = new AtomicLong(100);
        HiddenSession session = new HiddenSession(now::get);
        HiddenSession.Attempt old = session.beginAuthentication();
        session.lock();
        byte[] stale = key.clone();
        check(!session.complete(old, stale)); check(Arrays.equals(stale, new byte[32]));
        HiddenSession.Attempt current = session.beginAuthentication();
        byte[] owned = key.clone();
        check(session.complete(current, owned)); check(Arrays.equals(owned, new byte[32]));
        check(session.isUnlocked());
        check(!session.complete(current, key.clone())); // one-shot completion
        check(session.isUnlocked()); // rejected duplicate must not replace valid session
        now.addAndGet(HiddenSession.TIMEOUT_MILLIS);
        check(!session.isUnlocked());
        HiddenSession.Attempt late = session.beginAuthentication();
        now.addAndGet(HiddenSession.AUTH_TIMEOUT_MILLIS);
        check(!session.complete(late, key.clone()));
        current = session.beginAuthentication(); check(session.complete(current, key.clone()));
        now.decrementAndGet(); check(!session.isUnlocked());
        HiddenEntrySequence sequence = new HiddenEntrySequence(now::get);
        for (int i = 0; i < 5; i++) check(!sequence.tap(false));
        check(!sequence.tap(true));
        for (int i = 0; i < 3; i++) check(!sequence.tap(false));
        check(sequence.tap(false)); check(!sequence.tap(false));
        sequence.reset();
        for (int i = 0; i < 5; i++) sequence.tap(false);
        now.addAndGet(30_000); check(!sequence.tap(true));
        for (int i = 0; i < 20; i++) check(!sequence.tap(false));
        byte[] saltTamper = envelope.clone(); saltTamper[8] ^= 1;
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), saltTamper));
        byte[] nonceTamper = envelope.clone(); nonceTamper[24] ^= 1;
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), nonceTamper));
        rejected(() -> HiddenPinEnvelope.unwrap("728491".toCharArray(), Arrays.copyOf(envelope, envelope.length - 1)));
        char[] invalid = "12345x".toCharArray();
        rejected(() -> HiddenPinEnvelope.wrap(invalid, key)); check(Arrays.equals(invalid, new char[6]));
        rejected(() -> HiddenPinEnvelope.wrap("1234567890123".toCharArray(), key));
        HiddenSession.Attempt superseded = session.beginAuthentication();
        HiddenSession.Attempt newest = session.beginAuthentication();
        check(!session.complete(superseded, key.clone()));
        now.addAndGet(HiddenSession.AUTH_TIMEOUT_MILLIS - 1);
        check(session.complete(newest, key.clone()));
        now.addAndGet(HiddenSession.TIMEOUT_MILLIS - 1); check(session.isUnlocked());
        now.incrementAndGet(); check(!session.isUnlocked());
        newest = session.beginAuthentication();
        byte[] shortKey = new byte[31]; Arrays.fill(shortKey, (byte) 1);
        check(!session.complete(newest, shortKey)); check(Arrays.equals(shortKey, new byte[31]));
        check(!session.complete(newest, key.clone()));
        check(!new HiddenSession(now::get).complete(session.beginAuthentication(), key.clone()));
        sequence.reset(); sequence.tap(false); now.decrementAndGet(); check(!sequence.tap(false));
        sequence.reset(); sequence.tap(false); check(!sequence.tap(true));
        for (int i = 0; i < 5; i++) sequence.tap(false);
        now.addAndGet(29_999); sequence.tap(true);
        for (int i = 0; i < 3; i++) check(!sequence.tap(false));
        check(sequence.tap(false));
        for (int i = 0; i < 100; i++) {
            HiddenSession.Attempt cycle = session.beginAuthentication();
            check(session.complete(cycle, key.clone())); check(session.isUnlocked());
            session.lock(); check(!session.isUnlocked());
        }
        System.out.println("Hidden foundation: " + checks + " checks passed");
    }
}
