package uk.co.traynor.privategallery.core.arcade;
import java.io.*;
import java.util.Base64;
/** Bounded versioned save encoding. No Java object deserialization. */
final class ArcadeCodec {
 interface Writer {void write(DataOutputStream out)throws IOException;}
 interface Reader<T>{T read(DataInputStream in)throws IOException;}
 static String encode(int kind,Writer writer){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x4e415201);out.writeByte(kind);writer.write(out);out.flush();return Base64.getEncoder().encodeToString(bytes.toByteArray());}catch(IOException ex){throw new IllegalArgumentException(ex);}}
 static <T>T decode(String raw,int kind,Reader<T> reader){require(raw!=null&&raw.length()<=32768);byte[] bytes;try{bytes=Base64.getDecoder().decode(raw);}catch(RuntimeException ex){throw new IllegalArgumentException("Invalid save",ex);}try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){require(in.readInt()==0x4e415201&&in.readUnsignedByte()==kind);T result=reader.read(in);require(in.available()==0);return result;}catch(IOException|RuntimeException ex){throw new IllegalArgumentException("Invalid save",ex);}finally{java.util.Arrays.fill(bytes,(byte)0);}}
 static void ints(DataOutputStream out,int[] a)throws IOException{out.writeInt(a.length);for(int v:a)out.writeInt(v);}
 static int[] ints(DataInputStream in,int min,int max)throws IOException{int n=in.readInt();require(n>=min&&n<=max);int[] a=new int[n];for(int i=0;i<n;i++)a[i]=in.readInt();return a;}
 static boolean bool(DataInputStream in)throws IOException{int b=in.readUnsignedByte();require(b<=1);return b==1;}
 static void require(boolean value){if(!value)throw new IllegalArgumentException("Invalid Arcade state");}
 /** SplitMix64 is game randomness only, never a cryptographic key source. */
 static final class Random {long seed;Random(long seed){this.seed=seed;}int next(int bound){require(bound>0);long z=(seed+=0x9e3779b97f4a7c15L);z=(z^(z>>>30))*0xbf58476d1ce4e5b9L;z=(z^(z>>>27))*0x94d049bb133111ebL;return (int)Long.remainderUnsigned(z^(z>>>31),bound);}void shuffle(int[] a){for(int i=a.length-1;i>0;i--){int j=next(i+1),v=a[i];a[i]=a[j];a[j]=v;}}}
}
