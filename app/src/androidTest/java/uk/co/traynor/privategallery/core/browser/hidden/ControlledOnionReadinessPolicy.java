package uk.co.traynor.privategallery.core.browser.hidden;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Test-only controlled-fixture admission; never changes production request policy. */
public final class ControlledOnionReadinessPolicy {
    public static final long BUDGET_MS = 90_000L;
    public enum Result { READY, WAIT }
    private final byte[] expected;

    public ControlledOnionReadinessPolicy(String expected) {
        this.expected = expected.getBytes(StandardCharsets.UTF_8);
        if (this.expected.length == 0 || this.expected.length > 256) {
            throw new IllegalArgumentException("Bounded unique fixture response required");
        }
    }

    public Result observe(boolean authorized, long elapsedMs, String localKind,
                          String failure, int status, byte[] body) {
        if (!authorized || elapsedMs < 0 || elapsedMs > BUDGET_MS) {
            throw new AssertionError("Controlled onion readiness authority/budget exhausted");
        }
        if (localKind == null) {
            if (status != 200 || !Arrays.equals(expected, body)) {
                throw new AssertionError("Controlled onion readiness requires exact upstream HTTP200 bytes");
            }
            return Result.READY;
        }
        if ("transport".equals(localKind) && "timeout".equals(failure)
                && status == 503 && elapsedMs < BUDGET_MS) {
            return Result.WAIT;
        }
        throw new AssertionError("Controlled onion readiness rejected non-timeout local result");
    }
}
