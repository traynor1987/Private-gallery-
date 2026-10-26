import uk.co.traynor.privategallery.core.browser.hidden.TorControlEventSummary;
public class TorControlEventChecks {
 static int checks;
 static void same(String input,String expected){String actual=TorControlEventSummary.summarize(input);if(!expected.equals(actual))throw new AssertionError(actual);checks++;}
 public static void main(String[] args){
  same("650 STREAM 8 NEW 0 privatefixture.onion:80 SOURCE_ADDR=127.0.0.1:53000 PURPOSE=USER SOCKS_PASSWORD=secret", "STREAM id=8 status=NEW circuit=0 PURPOSE=USER");
  same("650 CIRC 14 FAILED $ABCDEF~relay PURPOSE=HS_CLIENT_REND HS_STATE=HSCR_ESTABLISHED_WAITING REASON=TIMEOUT REND_QUERY=privatefixture SOCKS_USERNAME=secret", "CIRC id=14 status=FAILED PURPOSE=HS_CLIENT_REND HS_STATE=HSCR_ESTABLISHED_WAITING REASON=TIMEOUT");
  same("650 HS_DESC FAILED privatefixture NO_AUTH $fingerprint REASON=NOT_FOUND", "HS_DESC status=FAILED REASON=NOT_FOUND");
  same("650 HS_DESC RECEIVED privatefixture NO_AUTH $fingerprint descriptorid HSDIR_INDEX=secret", "HS_DESC status=RECEIVED");
  same("650 CIRC 3 BUILT $relay PURPOSE=UNRECOGNISED SECRET=plaintext", "CIRC id=3 status=BUILT PURPOSE=OTHER");
  same("650 STREAM 8 FAILED 14 target REASON=RESOLVEFAILED REMOTE_REASON=TIMEOUT", "STREAM id=8 status=FAILED circuit=14 REASON=RESOLVEFAILED REMOTE_REASON=TIMEOUT");
  same("650-STREAM 8 CLOSED 14 target REASON=DONE", "STREAM id=8 status=CLOSED circuit=14 REASON=DONE");
  for(String raw:new String[]{"650 HS_DESC_CONTENT privatefixture", "privatefixture descriptor contents", "250 OK", "650 CIRC privatefixture FAILED", "650 STREAM 8 secret 0 privatefixture", "650 HS_DESC secret privatefixture", "650 STREAM 8 NEW privatefixture target", "650 STATUS_CLIENT NOTICE privatefixture", "650 CIRC 3 BUILT $relay PURPOSE=USER\nsecret"})same(raw, "");
  System.out.println(checks+" Tor control redaction checks PASS");
 }
}
