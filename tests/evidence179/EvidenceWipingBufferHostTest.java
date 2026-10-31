package uk.co.traynor.privategallery.core.evidence;
import java.util.*;
public final class EvidenceWipingBufferHostTest {
 static int passed;
 static void check(boolean value){if(!value)throw new AssertionError();passed++;}
 static boolean zero(byte[] b){for(byte v:b)if(v!=0)return false;return true;}
 public static void main(String[] args){
  var allocations=new ArrayList<byte[]>();byte[] copy;
  try(var buffer=new EvidenceWipingBuffer(257,()->true,allocations::add)){
   for(int i=0;i<257;i++)buffer.write(7);
   check(allocations.size()>2);for(int i=0;i<allocations.size()-1;i++)check(zero(allocations.get(i)));
   copy=buffer.toByteArray();check(copy.length==257&&copy[256]==7);
   try{buffer.write(1);throw new AssertionError();}catch(IllegalArgumentException expected){passed++;}
  }
  for(byte[] allocation:allocations)check(zero(allocation));check(copy[0]==7);Arrays.fill(copy,(byte)0);check(zero(copy));
  boolean[] active={true};allocations.clear();var failed=new EvidenceWipingBuffer(1024,()->active[0],allocations::add);
  try(failed){failed.write(new byte[100],0,100);failed.write(42);active[0]=false;try{failed.toByteArray();throw new AssertionError();}catch(IllegalStateException expected){passed++;}}
  for(byte[] allocation:allocations)check(zero(allocation));
  try{failed.write(1);throw new AssertionError();}catch(IllegalStateException expected){passed++;}
  System.out.println("EvidenceWipingBufferHostTest PASS "+passed);
 }
}
