package uk.co.traynor.privategallery.core.privateapps;

import java.io.*;
import java.util.*;

/** Bounded immutable catalogue. Contains definitions, never login credentials or transport authority. */
public final class PrivateAppCatalog {
 public static final int MAX_APPS=32, MAX_BYTES=384*1024;
 public record App(String id,String name,String startUrl,String scope,String display,String icon,
                   boolean pinned,boolean addressBar,boolean requireAuthentication) {
  public App withName(String value){return new App(id,value,startUrl,scope,display,icon,pinned,addressBar,requireAuthentication);}
 }
 public final List<App> apps;
 public final boolean tor;
 public PrivateAppCatalog(boolean tor,List<App> apps) {
  if(apps.size()>MAX_APPS)throw new IllegalArgumentException("Private app limit reached");
  HashSet<String> seen=new HashSet<>();
  for(App app:apps) {
   if(app==null || !uuid(app.id()) || !seen.add(app.id()) || !PrivateAppPolicy.validName(app.name()) || !PrivateAppPolicy.safeUrl(app.startUrl(),tor)
     || app.scope()==null || (!app.scope().isEmpty() && !PrivateAppPolicy.inScope(app.startUrl(),app.scope()))
     || !Set.of("browser","standalone","minimal-ui","fullscreen").contains(app.display())
     || !validIcon(app.icon()))throw new IllegalArgumentException("Invalid private app definition");
  }
  this.tor=tor;this.apps=List.copyOf(apps);
 }
 private static boolean validIcon(String value) {
  if(value==null || value.length()>8192)return false;
  if(value.isEmpty())return true;
  try {
   byte[] bytes=Base64.getDecoder().decode(value);
   try{return bytes.length>0 && Base64.getEncoder().encodeToString(bytes).equals(value);}finally{Arrays.fill(bytes,(byte)0);}
  }catch(IllegalArgumentException e){return false;}
 }
 private static final class SensitiveBuffer extends ByteArrayOutputStream {
  @Override public void close(){Arrays.fill(buf,(byte)0);reset();}
 }
 private static boolean uuid(String id){return id!=null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
 public PrivateAppCatalog upsert(App app) {
  ArrayList<App> values=new ArrayList<>(apps);int at=-1;
  for(int i=0;i<values.size();i++)if(values.get(i).id().equals(app.id()))at=i;
  if(at<0)values.add(app);else values.set(at,app);
  return new PrivateAppCatalog(tor,values);
 }
 public PrivateAppCatalog remove(String id){return new PrivateAppCatalog(tor,apps.stream().filter(a->!a.id().equals(id)).toList());}
 public PrivateAppCatalog move(String id,int delta) {
  if(delta!=-1 && delta!=1)throw new IllegalArgumentException("Invalid move");
  ArrayList<App> values=new ArrayList<>(apps);
  for(int i=0;i<values.size();i++)if(values.get(i).id().equals(id)){int to=i+delta;if(to>=0 && to<values.size())Collections.swap(values,i,to);break;}
  return new PrivateAppCatalog(tor,values);
 }
 public byte[] encode() {
  try(SensitiveBuffer bytes=new SensitiveBuffer();DataOutputStream output=new DataOutputStream(bytes)) {
   output.writeInt(1);output.writeInt(apps.size());
   for(App app:apps){output.writeUTF(app.id());output.writeUTF(app.name());output.writeUTF(app.startUrl());output.writeUTF(app.scope());output.writeUTF(app.display());output.writeUTF(app.icon());output.writeBoolean(app.pinned());output.writeBoolean(app.addressBar());output.writeBoolean(app.requireAuthentication());}
   output.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Private app catalogue too large");return bytes.toByteArray();
  }catch(IOException e){throw new IllegalArgumentException("Invalid private app catalogue",e);}
 }
 public static PrivateAppCatalog decode(byte[] bytes,boolean tor) {
  if(bytes==null || bytes.length>MAX_BYTES)throw new IllegalArgumentException("Private app catalogue too large");
  try(DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes))) {
   if(input.readInt()!=1)throw new IOException("Unsupported catalogue");
   int count=input.readInt();if(count<0 || count>MAX_APPS)throw new IOException("Invalid catalogue size");
   ArrayList<App> values=new ArrayList<>();
   for(int i=0;i<count;i++)values.add(new App(input.readUTF(),input.readUTF(),input.readUTF(),input.readUTF(),input.readUTF(),input.readUTF(),input.readBoolean(),input.readBoolean(),input.readBoolean()));
   if(input.read()!=-1)throw new IOException("Trailing catalogue data");
   return new PrivateAppCatalog(tor,values);
  }catch(IOException e){throw new IllegalArgumentException("Invalid private app catalogue",e);}
 }
}
