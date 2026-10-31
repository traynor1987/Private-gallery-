package uk.co.traynor.privategallery.core.evidence;
import java.io.*;
import java.util.*;
import java.util.zip.*;
public final class EvidenceArchiveFormatHostTest {
 static int passed;
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static void reject(Throwing action)throws Exception{try{action.run();throw new AssertionError("accepted invalid archive");}catch(IOException|IllegalArgumentException expected){passed++;}}
 interface Throwing{void run()throws Exception;}
 static byte[] zip(String... names)throws Exception{var out=new ByteArrayOutputStream();try(var z=new ZipOutputStream(out)){for(String name:names){z.putNextEntry(new ZipEntry(name));z.write("hello".getBytes());z.closeEntry();}}return out.toByteArray();}
 static int signature(byte[] b,int value){for(int i=0;i<b.length-3;i++)if(((b[i]&255)|((b[i+1]&255)<<8)|((b[i+2]&255)<<16)|((b[i+3]&255)<<24)) == value)return i;return -1;}
 public static void main(String[] args)throws Exception{
  byte[] salt=new byte[16],nonce=new byte[12];byte[] h=EvidenceArchiveFormat.header(salt,nonce);check(h.length==52);EvidenceArchiveFormat.validateHeader(h);passed++;
  for(int i=0;i<24;i++){byte[] bad=h.clone();bad[i]^=1;reject(()->EvidenceArchiveFormat.validateHeader(bad));}
  reject(()->EvidenceArchiveFormat.validateHeader(Arrays.copyOf(h,51)));
  check(Arrays.equals(EvidenceArchiveFormat.password("  pässphrase unchanged  ".toCharArray()),"  pässphrase unchanged  ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  reject(()->EvidenceArchiveFormat.password(new char[]{'x','\ud800'}));
  byte[] valid=zip("report.pdf","evidence.json");var seen=new ArrayList<String>();EvidenceArchiveFormat.readZip(new ByteArrayInputStream(valid),()->true,(name,bytes)->{seen.add(name);check(Arrays.equals(bytes.readAllBytes(),"hello".getBytes()));});check(seen.size()==2);
  for(String path:List.of("../x","/x","a/../b","a\\b","C:x","a//b","a/./b","a/",""))reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(zip(path)),()->true,(n,in)->{}));
  byte[] symlink=valid.clone();int central=signature(symlink,0x02014b50);symlink[central+5]=3;symlink[central+40]=(byte)0xff;symlink[central+41]=(byte)0xa1;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(symlink),()->true,(n,in)->{}));
  byte[] crc=valid.clone();crc[14]^=1;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(crc),()->true,(n,in)->{}));
  reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(Arrays.copyOf(valid,valid.length-1)),()->true,(n,in)->{}));
  byte[] extra=Arrays.copyOf(valid,valid.length+1);reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(extra),()->true,(n,in)->{}));
  reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(valid),()->false,(n,in)->{}));
  byte[] badMethod=valid.clone();badMethod[8]=99;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(badMethod),()->true,(n,in)->{}));
  byte[] mismatch=valid.clone();mismatch[central+46]='X';reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(mismatch),()->true,(n,in)->{}));
  
  check(java.util.HexFormat.of().formatHex(h).equals("50474556494431370000000100008000000000080000000100000000000000000000000000000000000000000000000000000000"));
  for(int index:List.of(4,6,8,28)){byte[] bad=valid.clone();bad[index]=(byte)255;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(bad),()->true,(n,in)->{}));}
  for(int index:List.of(6,8,10,20,24,30,32,34,42)){byte[] bad=valid.clone();bad[central+index]^=1;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(bad),()->true,(n,in)->{}));}
  byte[] dupe=zip("aaaa","bbbb");for(int i=0;i<dupe.length-3;i++)if(dupe[i]=='b'&&dupe[i+1]=='b'&&dupe[i+2]=='b'&&dupe[i+3]=='b')Arrays.fill(dupe,i,i+4,(byte)'a');reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(dupe),()->true,(n,in)->{}));
  String[] many=new String[257];for(int i=0;i<many.length;i++)many[i]="entry"+i;reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(zip(many)),()->true,(n,in)->{}));
  var stored=new ByteArrayOutputStream();try(var z=new ZipOutputStream(stored)){var e=new ZipEntry("stored");e.setMethod(ZipEntry.STORED);e.setSize(5);e.setCompressedSize(5);var c=new CRC32();c.update("hello".getBytes());e.setCrc(c.getValue());z.putNextEntry(e);z.write("hello".getBytes());z.closeEntry();}EvidenceArchiveFormat.readZip(new ByteArrayInputStream(stored.toByteArray()),()->true,(n,in)->check(Arrays.equals(in.readAllBytes(),"hello".getBytes())));
  var bomb=new ByteArrayOutputStream();byte[] zeros=new byte[1024*1024];try(var z=new ZipOutputStream(bomb)){for(int entry=0;entry<2;entry++){z.putNextEntry(new ZipEntry("bomb"+entry));for(int i=0;i<1024;i++)z.write(zeros);if(entry==1)z.write(0);z.closeEntry();}}reject(()->EvidenceArchiveFormat.readZip(new ByteArrayInputStream(bomb.toByteArray()),()->true,(n,in)->{}));
  System.out.println("EvidenceArchiveFormatHostTest PASS "+passed);
 }
}
