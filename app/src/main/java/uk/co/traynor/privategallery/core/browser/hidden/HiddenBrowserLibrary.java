package uk.co.traynor.privategallery.core.browser.hidden;

import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore;
import uk.co.traynor.privategallery.core.hidden.HiddenSession;
import java.io.*;
import java.net.URI;
import java.util.*;

/** Explicit encrypted library only; never restores navigation, cookies, tabs or Primary data. */
public final class HiddenBrowserLibrary {
 private static final Object GATE = new Object();
 private static final int VERSION=1, MAX_URL=2048, MAX_HISTORY=100, MAX_BOOKMARKS=50;
 private final HiddenSession session;
 private final HiddenSession.Authority authority;
 private final HiddenRecordStore records;
 private final String id;
 private final boolean defaultHistory;
 public HiddenBrowserLibrary(File noBackup, HiddenSession session, HiddenSession.Authority authority, boolean vpn) {
  this(session,authority,vpn,new HiddenRecordStore(new File(noBackup,"hidden-browser-library-v1")));
 }
 HiddenBrowserLibrary(HiddenSession session, HiddenSession.Authority authority, boolean vpn, HiddenRecordStore records) {
  this.session=session;this.authority=Objects.requireNonNull(authority);this.records=records;
  // Distinct authenticated record identities prevent swapping ciphertext between modes.
  id=vpn?"d39228ad-dc03-4875-9493-ea64192a3111":"d39228ad-dc03-4875-9493-ea64192a3222";
  defaultHistory=vpn;
 }
 private static final class State {
  boolean save;
  final ArrayList<String> bookmarks=new ArrayList<>(), history=new ArrayList<>();
  State(boolean save) {this.save=save;}
 }
 private interface Action<T> {T run(State state);}
 private <T> T access(boolean write, Action<T> action) {
  synchronized(GATE) {
   try(HiddenSession.Operation op=session.beginOperation(authority)) {
    State state=read(op.key());
    T result=action.run(state);
    if(write) {
     byte[] bytes=encode(state); File staged=null;
     try {
      staged=records.stage("metadata",id,bytes,op.key());
      File ready=staged;
      session.commit(op,()-> {try {records.promote(ready,"metadata",id);}catch(IOException e){throw new UncheckedIOException(e);}});
      records.syncPromotions();
     } finally {Arrays.fill(bytes,(byte)0);if(staged!=null)staged.delete();}
    }
    if(!session.isCurrent(op))throw new IllegalStateException("Hidden library revoked");
    return result;
   } catch(RuntimeException e) {throw e;} catch(Exception e) {throw new IllegalStateException("Hidden library unavailable",e);}
  }
 }
 private State read(byte[] key)throws Exception {
  if(!records.file("metadata",id).exists())return new State(defaultHistory);
  byte[] bytes=records.read("metadata",id,key);
  try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes))) {
   if(input.readInt()!=VERSION)throw new IOException("Unsupported Hidden library");
   State state=new State(input.readBoolean());
   readList(input,state.bookmarks,MAX_BOOKMARKS);readList(input,state.history,MAX_HISTORY);
   if(input.read()!=-1 || (!state.save && !state.history.isEmpty()))throw new IOException("Invalid Hidden library");
   return state;
  } finally {Arrays.fill(bytes,(byte)0);}
 }
 private static void readList(DataInputStream input,ArrayList<String> list,int max)throws IOException {
  int count=input.readInt();if(count<0 || count>max)throw new IOException("Invalid Hidden library");
  for(int i=0;i<count;i++) {String url=input.readUTF();if(!allowed(url)||list.contains(url))throw new IOException("Invalid Hidden library");list.add(url);}
 }
 private static final class SensitiveBuffer extends ByteArrayOutputStream {
  @Override public void close() {Arrays.fill(buf,(byte)0);reset();}
 }
 private static byte[] encode(State state)throws IOException {
  try(SensitiveBuffer buffer=new SensitiveBuffer();DataOutputStream output=new DataOutputStream(buffer)) {
   output.writeInt(VERSION);output.writeBoolean(state.save);
   for(List<String> list:List.of(state.bookmarks,state.history)) {output.writeInt(list.size());for(String url:list)output.writeUTF(url);}
   output.flush();return buffer.toByteArray();
  }
 }
 private static boolean allowed(String url) {
  if(url==null || url.length()>MAX_URL || url.chars().anyMatch(c->c<32 || c==127))return false;
  try {URI uri=URI.create(url);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())) && uri.getHost()!=null && uri.getRawUserInfo()==null;}catch(IllegalArgumentException e){return false;}
 }
 private static void add(ArrayList<String> list,String url,int max) {if(!allowed(url))throw new IllegalArgumentException("Unsupported page address");list.remove(url);list.add(0,url);while(list.size()>max)list.remove(list.size()-1);}
 public void visit(String url) {if(!allowed(url))return;access(true,s->{if(s.save)add(s.history,url,MAX_HISTORY);return null;});}
 public void bookmark(String url) {access(true,s->{add(s.bookmarks,url,MAX_BOOKMARKS);return null;});}
 public void removeBookmark(String url) {access(true,s->{s.bookmarks.remove(url);return null;});}
 public List<String> bookmarks() {return access(false,s->List.copyOf(s.bookmarks));}
 public List<String> history() {return access(false,s->List.copyOf(s.history));}
 public boolean savesHistory() {return access(false,s->s.save);}
 /** Disabling history also removes previously saved history in this mode. */
 public void saveHistory(boolean enabled) {access(true,s->{s.save=enabled;if(!enabled)s.history.clear();return null;});}
 public void clearHistory() {access(true,s->{s.history.clear();return null;});}
 /** Explicit browsing-data clear retains intentional bookmarks and the independent preference. */
 public void clearBrowsingData() {clearHistory();}
}
