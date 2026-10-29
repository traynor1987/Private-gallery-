package uk.co.traynor.privategallery.core.privateapps;

/** Completion is accepted once, from the latest clearing request only. Persistence is caller-owned. */
public final class PrivateAppClearBarrier {
 private long generation;
 private boolean pending;
 public synchronized long begin() {pending=true;return ++generation;}
 public synchronized boolean complete(long ticket,Runnable persistCompleted) {
  if(!pending || ticket!=generation)return false;
  persistCompleted.run();pending=false;return true;
 }
 public synchronized boolean pending() {return pending;}
}
