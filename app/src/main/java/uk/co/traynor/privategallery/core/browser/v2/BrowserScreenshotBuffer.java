package uk.co.traynor.privategallery.core.browser.v2;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Arrays;

/** Fixed capacity avoids expansion copies; the stream references the same wipeable allocation. */
public final class BrowserScreenshotBuffer extends OutputStream {
    private final byte[] buffer;
    private int count;
    private boolean wiped;
    public BrowserScreenshotBuffer(int capacity) {buffer=new byte[capacity];}
    private void room(int size) {if(wiped || size<0 || size>buffer.length-count)throw new IllegalStateException("Screenshot buffer unavailable");}
    @Override public void write(int value) {room(1);buffer[count++]=(byte)value;}
    @Override public void write(byte[] bytes,int offset,int length) {room(length);System.arraycopy(bytes,offset,buffer,count,length);count+=length;}
    public ByteArrayInputStream input() {room(0);return new ByteArrayInputStream(buffer,0,count);}
    public void wipe() {Arrays.fill(buffer,(byte)0);count=0;wiped=true;}
}
