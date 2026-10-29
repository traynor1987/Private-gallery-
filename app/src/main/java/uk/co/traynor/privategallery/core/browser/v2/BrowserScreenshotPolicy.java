package uk.co.traynor.privategallery.core.browser.v2;

/** Conservative viewport policy. Unknown/protected embedded surfaces fail closed. */
public final class BrowserScreenshotPolicy {
    private BrowserScreenshotPolicy() {}
    public static final long MAX_PIXELS = 4_194_304L;
    public static final int MAX_PNG_BYTES = 16 * 1024 * 1024;
    public static final String REFUSAL = "Screenshot unavailable: allow screenshots in this Vault's settings, keep this page visible, and use a page without video or embedded content. Protected content is never captured.";
    public static boolean allowed(boolean confirmed, boolean authorized, boolean secure, boolean focused,
                                  boolean attached, boolean safeDocument, int width, int height) {
        return confirmed && authorized && !secure && focused && attached && safeDocument &&
                width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }
    public static boolean pngHeader(byte[] header, long bytes) {
        if (header.length < 24 || bytes < 45 || bytes > MAX_PNG_BYTES) return false;
        byte[] signature = {(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82};
        for (int i=0;i<signature.length;i++) if (header[i]!=signature[i]) return false;
        long width=0,height=0;
        for (int i=16;i<20;i++) width=(width<<8)|(header[i]&255);
        for (int i=20;i<24;i++) height=(height<<8)|(header[i]&255);
        return width>0 && height>0 && width<=MAX_PIXELS && height<=MAX_PIXELS && width*height<=MAX_PIXELS;
    }
    public static boolean pngTrailer(byte[] trailer) {
        return java.util.Arrays.equals(trailer,new byte[]{0,0,0,0,73,69,78,68,(byte)174,66,96,(byte)130});
    }
}
