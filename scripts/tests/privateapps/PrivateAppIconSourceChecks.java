import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import uk.co.traynor.privategallery.core.privateapps.PrivateAppIconSource;

public final class PrivateAppIconSourceChecks {
 static void check(boolean value) {if(!value)throw new AssertionError();}
 static void rejects(Throwing action) throws Exception {try{action.run();}catch(IOException|IllegalStateException expected){return;}throw new AssertionError("Expected rejection");}
 interface Throwing {void run() throws Exception;}
 public static void main(String[] args) throws Exception {
  AtomicBoolean live=new AtomicBoolean(true);
  var requests=new PrivateAppIconSource.Requests(live::get);
  long first=requests.begin();check(requests.current(first));
  long second=requests.begin();check(!requests.current(first));check(requests.current(second));
  requests.cancel();check(!requests.current(second));
  long third=requests.begin();live.set(false);check(!requests.current(third));
  live.set(true);requests.close();check(!requests.current(third));rejects(requests::begin);
  byte[] input=new byte[]{1,2,3};
  check(Arrays.equals(input,PrivateAppIconSource.read(new ByteArrayInputStream(input),()->true)));
  AtomicBoolean closed=new AtomicBoolean(false);
  var oversize=new InputStream() {
   int supplied;
   public int read(){supplied++;return 1;}
   public int read(byte[] bytes,int offset,int length){supplied+=length;return length;}
   public void close(){closed.set(true);check(supplied<=PrivateAppIconSource.MAX_SOURCE_BYTES+1);}
  };
  rejects(()->PrivateAppIconSource.read(oversize,()->true));check(closed.get());
  rejects(()->PrivateAppIconSource.read(new ByteArrayInputStream(input),()->false));
  rejects(()->PrivateAppIconSource.read(new ByteArrayInputStream(new byte[0]),()->true));
  System.out.println("PrivateAppIconSourceChecks passed");
 }
}
