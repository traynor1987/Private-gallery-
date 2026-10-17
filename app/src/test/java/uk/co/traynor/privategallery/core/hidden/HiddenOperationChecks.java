package uk.co.traynor.privategallery.core.hidden;

import java.util.Arrays;

public final class HiddenOperationChecks {
    public static void main(String[] args) throws Exception {
        long[] clock = {1};
        HiddenSession session = new HiddenSession(() -> clock[0]);
        expectFailure(session::beginOperation);
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 7);
        session.complete(session.beginAuthentication(), key);
        HiddenSession.Authority original = session.authority();
        HiddenSession.Operation op = session.beginOperation(original);
        if (op.key()[0] != 7 || !session.isCurrent(op)) throw new AssertionError("Authenticated operation");
        int[] commits = {0};
        session.commit(op, () -> commits[0]++);
        session.lock();
        if (op.key()[0] != 7) throw new AssertionError("Lock changed an in-flight cryptographic key");
        if (session.isCurrent(op)) throw new AssertionError("Locked operation retained authority");
        expectFailure(() -> session.commit(op, () -> commits[0]++));
        session.complete(session.beginAuthentication(), new byte[32]);
        expectFailure(() -> session.commit(op, () -> commits[0]++));
        expectFailure(() -> session.beginOperation(original));
        op.close();
        if (Arrays.stream(new int[]{op.key()[0]}).anyMatch(x -> x != 0)) throw new AssertionError("Operation key not wiped");
        HiddenSession.Operation next = session.beginOperation();
        clock[0] += HiddenSession.TIMEOUT_MILLIS;
        if (session.isCurrent(next)) throw new AssertionError("Expired operation");
        next.close();
        if (commits[0] != 1) throw new AssertionError("Stale commit executed");
        System.out.println("Hidden operation revocation checks passed");
    }
    static void expectFailure(Runnable r) { try { r.run(); } catch (IllegalStateException expected) {return;} throw new AssertionError("Expected rejection"); }
}
