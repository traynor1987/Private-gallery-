package uk.co.traynor.privategallery.core.vpn;

import java.util.HashMap;
import java.util.Map;

/** Additional framework admission gate, never a replacement for owner/peer/route verification. */
public final class OwnedVpnNetworkReadinessGate<T> implements AutoCloseable {
 private static final class State {
  boolean capabilitiesSeen,linksSeen,blockedSeen,owned,linksValid,blocked=true,lost;
 }
 private final Map<T,State> networks=new HashMap<>();
 private long epoch;
 private boolean registered,closed;
 public synchronized long beginRegistration(){
  if(closed)throw new IllegalStateException("Readiness observer closed");
  networks.clear();registered=true;return ++epoch;
 }
 private boolean current(long value){return !closed && registered && value==epoch;}
 private State live(long value,T network){
  if(!current(value))return null;
  State state=networks.get(network);return state!=null && !state.lost?state:null;
 }
 public synchronized void available(long value,T network){
  if(current(value))networks.putIfAbsent(network,new State());
 }
 public synchronized void capabilities(long value,T network,boolean owned){
  State state=live(value,network);if(state==null)return;
  state.capabilitiesSeen=true;state.owned=owned;
 }
 public synchronized void linkProperties(long value,T network,boolean valid){
  State state=live(value,network);if(state==null || !state.capabilitiesSeen)return;
  state.linksSeen=true;state.linksValid=valid;
 }
 public synchronized void blocked(long value,T network,boolean blocked){
  State state=live(value,network);if(state==null || !state.capabilitiesSeen || !state.linksSeen)return;
  state.blockedSeen=true;state.blocked=blocked;
 }
 public synchronized void lost(long value,T network){
  if(current(value))networks.computeIfAbsent(network,key->new State()).lost=true;
 }
 public synchronized boolean isReady(T network){
  State state=live(epoch,network);
  return state!=null && state.capabilitiesSeen && state.linksSeen && state.blockedSeen &&
    state.owned && state.linksValid && !state.blocked;
 }
 public synchronized void registrationFailed(long value){
  if(current(value)){registered=false;networks.clear();}
 }
 @Override public synchronized void close(){closed=true;registered=false;networks.clear();}
}
