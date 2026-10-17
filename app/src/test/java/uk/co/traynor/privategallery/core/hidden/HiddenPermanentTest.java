package uk.co.traynor.privategallery.core.hidden;
import org.junit.Test;
public final class HiddenPermanentTest {
    @Test public void backgroundTimeoutContract() throws Exception {HiddenTimeoutChecks.main(new String[0]);}
    @Test public void nonBlockingDurabilityContract() throws Exception {HiddenSyncChecks.main(new String[0]);}
    @Test public void encryptedRecordContract() throws Exception {HiddenRecordChecks.main(new String[0]);}
    @Test public void authenticatedOperationContract() throws Exception {HiddenOperationChecks.main(new String[0]);}
}
