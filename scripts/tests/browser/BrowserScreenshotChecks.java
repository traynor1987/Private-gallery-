import java.io.InputStream;
import uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy;
import uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotBuffer;

public class BrowserScreenshotChecks {
    private static void check(boolean ok) {if(!ok)throw new AssertionError();}
    public static void main(String[] args) throws Exception {
        check(BrowserScreenshotPolicy.allowed(true,true,false,true,true,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(false,true,false,true,true,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,false,false,true,true,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,true,true,true,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,false,false,true,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,false,true,false,true,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,false,true,true,false,1080,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,false,true,true,true,0,1920));
        check(!BrowserScreenshotPolicy.allowed(true,true,false,true,true,true,Integer.MAX_VALUE,Integer.MAX_VALUE));
        BrowserScreenshotBuffer buffer=new BrowserScreenshotBuffer(3);
        buffer.write(new byte[]{42,43,44});
        InputStream stream=buffer.input();check(stream.read()==42);
        try {buffer.write(1);throw new AssertionError();}catch(IllegalStateException expected){}
        buffer.wipe();check(stream.read()==0 && stream.read()==0);
        try {buffer.input();throw new AssertionError();}catch(IllegalStateException expected){}
        buffer.wipe();
        byte[] png={(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82,0,0,4,56,0,0,7,(byte)128};
        check(BrowserScreenshotPolicy.pngHeader(png,100));
        check(!BrowserScreenshotPolicy.pngHeader(png,17*1024*1024));
        png[16]=(byte)255;check(!BrowserScreenshotPolicy.pngHeader(png,100));
        png[0]=0;check(!BrowserScreenshotPolicy.pngHeader(png,100));
        check(BrowserScreenshotPolicy.pngTrailer(new byte[]{0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130}));
        check(!BrowserScreenshotPolicy.pngTrailer(new byte[12]));
        System.out.println("Browser screenshot policy, PNG bounds and memory wipe checks passed");
    }
}
