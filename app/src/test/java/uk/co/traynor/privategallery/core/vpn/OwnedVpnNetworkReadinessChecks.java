package uk.co.traynor.privategallery.core.vpn;

/** Framework event ordering only; never substitutes for real DNS/HTTPS and route proofs. */
public final class OwnedVpnNetworkReadinessChecks {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static void ready(OwnedVpnNetworkReadinessGate<String> gate,long epoch,String network){
  gate.available(epoch,network);gate.capabilities(epoch,network,true);
  gate.linkProperties(epoch,network,true);gate.blocked(epoch,network,false);
 }
 public static void main(String[] args){
  OwnedVpnNetworkReadinessGate<String> gate=new OwnedVpnNetworkReadinessGate<>();
  long first=gate.beginRegistration();
  gate.capabilities(first,"early",true);gate.linkProperties(first,"early",true);gate.blocked(first,"early",false);
  check(!gate.isReady("early"),"getAllNetworks/handshake or pre-available callbacks cannot admit");
  gate.available(first,"a");check(!gate.isReady("a"),"available alone is insufficient");
  gate.blocked(first,"a",false);gate.linkProperties(first,"a",true);
  gate.capabilities(first,"a",true);check(!gate.isReady("a"),"out-of-order initial callbacks cannot admit");
  gate.linkProperties(first,"a",true);check(!gate.isReady("a"),"explicit ordered unblock required");
  gate.blocked(first,"a",true);check(!gate.isReady("a"),"blocked network denied");
  gate.blocked(first,"a",false);check(gate.isReady("a"),"complete own unblocked network accepted");
  gate.capabilities(first,"a",false);check(!gate.isReady("a"),"ownership/capability loss revokes immediately");
  gate.capabilities(first,"a",true);check(gate.isReady("a"),"current capability recovery observed");
  gate.linkProperties(first,"a",false);check(!gate.isReady("a"),"invalid interface/DNS revokes immediately");
  gate.linkProperties(first,"a",true);check(gate.isReady("a"),"current valid link update observed");
  gate.blocked(first,"a",true);check(!gate.isReady("a"),"later blocking revokes immediately");
  gate.blocked(first,"a",false);check(gate.isReady("a"),"later explicit unblock observed");
  ready(gate,first,"b");gate.lost(first,"a");ready(gate,first,"a");
  check(!gate.isReady("a") && gate.isReady("b"),"lost network tombstone cannot revive or affect distinct network");
  gate.lost(first,"late");ready(gate,first,"late");check(!gate.isReady("late"),"lost before available stays retired");
  long second=gate.beginRegistration();check(!gate.isReady("b"),"registration replacement clears prior readiness");
  ready(gate,first,"old");check(!gate.isReady("old"),"old registration events ignored");
  ready(gate,second,"b");gate.lost(first,"b");check(gate.isReady("b"),"old registration loss cannot mutate current generation");
  gate.registrationFailed(second);ready(gate,second,"failed");check(!gate.isReady("b") && !gate.isReady("failed"),"registration failure denies late callbacks");
  long third=gate.beginRegistration();ready(gate,third,"c");check(gate.isReady("c"),"new registration has independent readiness");
  gate.close();ready(gate,third,"c");check(!gate.isReady("c"),"closed observer ignores all late callbacks");
  try {gate.beginRegistration();throw new AssertionError("closed observer reopened");}catch(IllegalStateException expected){}
  System.out.println("Owned VPN framework readiness: 20 checks passed");
 }
}
