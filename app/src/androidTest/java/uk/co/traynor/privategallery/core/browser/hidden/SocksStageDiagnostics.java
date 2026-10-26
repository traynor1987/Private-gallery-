package uk.co.traynor.privategallery.core.browser.hidden;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Diagnostic-only probe after an existing failed gate; never supplies an acceptance result. */
public final class SocksStageDiagnostics {
    private static final class AuthorityLost extends IOException {}
    private static void authority(BooleanSupplier active) throws AuthorityLost {
        if (!active.getAsBoolean()) throw new AuthorityLost();
    }
    private static long elapsed(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started); }
    public static List<String> inspect(int port, String hostname, String expected,
                                       BooleanSupplier active, long budgetMs) {
        if(port<1 || port>65535 || budgetMs<1 || budgetMs>30_000 ||
                hostname==null || !hostname.matches("[a-z0-9-]+\\.onion") || hostname.length()>255 ||
                expected==null || expected.isEmpty() || expected.getBytes(StandardCharsets.UTF_8).length>256) {
            throw new IllegalArgumentException("Bounded controlled onion diagnostic only");
        }
        List<String> records=new ArrayList<>();long started=System.nanoTime();String phase="authority";
        AtomicBoolean deadline=new AtomicBoolean();
        try(Socket socket=new Socket()) {
            ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(task->{
                Thread t=new Thread(task,"controlled-socks-diagnostic");t.setDaemon(true);return t;
            });
            ScheduledFuture<?> expiry=timer.schedule(()->{
                deadline.set(true);try{socket.close();}catch(IOException ignored){}
            },budgetMs,TimeUnit.MILLISECONDS);
            try {
                authority(active);phase="tcp_connect";
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),port),(int)Math.min(3000,budgetMs));
                socket.setSoTimeout((int)budgetMs);
                records.add("diagnostic tcp_connected elapsedMs="+elapsed(started));
                DataInputStream input=new DataInputStream(socket.getInputStream());OutputStream output=socket.getOutputStream();
                authority(active);phase="socks_auth";output.write(new byte[]{5,1,0});output.flush();
                int version=input.readUnsignedByte(),auth=input.readUnsignedByte();
                records.add("diagnostic socks_auth code="+auth+" elapsedMs="+elapsed(started));
                if(version!=5)throw new IOException("protocol");if(auth!=0)return records;
                authority(active);phase="socks_connect";byte[] host=hostname.getBytes(StandardCharsets.US_ASCII);
                output.write(new byte[]{5,1,0,3,(byte)host.length});output.write(host);output.write(new byte[]{0,80});output.flush();
                version=input.readUnsignedByte();int reply=input.readUnsignedByte(),reserved=input.readUnsignedByte(),type=input.readUnsignedByte();
                records.add("diagnostic socks_connect code="+reply+" elapsedMs="+elapsed(started));
                if(version!=5 || reserved!=0)throw new IOException("protocol");if(reply!=0)return records;
                int addressBytes=type==1?4:type==4?16:type==3?input.readUnsignedByte():-1;
                if(addressBytes<0)throw new IOException("protocol");input.readFully(new byte[addressBytes+2]);
                authority(active);phase="http_reply";
                output.write(("GET / HTTP/1.0\r\nHost: "+hostname+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));output.flush();
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[256];
                while(bytes.size()<1024) {
                    authority(active);int n=input.read(buffer,0,Math.min(buffer.length,1024-bytes.size()));
                    if(n<0)break;bytes.write(buffer,0,n);
                }
                byte[] all=bytes.toByteArray();String header=new String(all,StandardCharsets.ISO_8859_1);int split=header.indexOf("\r\n\r\n");
                int status=-1;
                if(header.length()>=12 && (header.startsWith("HTTP/1.0 ") || header.startsWith("HTTP/1.1 ")) && header.charAt(8)==' ' && header.substring(9,12).matches("[0-9]{3}"))status=Integer.parseInt(header.substring(9,12));
                byte[] body=split>=0?Arrays.copyOfRange(all,split+4,all.length):new byte[0];
                records.add("diagnostic http_status="+status+" bodyBytes="+body.length+" exactBody="+(all.length<1024 && Arrays.equals(expected.getBytes(StandardCharsets.UTF_8),body))+" elapsedMs="+elapsed(started));
            } finally {expiry.cancel(false);timer.shutdownNow();}
        } catch(Exception e) {
            records.add("diagnostic phase="+phase+" failure="+(e instanceof AuthorityLost?"authority_lost":e.getClass().getSimpleName())+" deadline="+deadline.get()+" elapsedMs="+elapsed(started));
        }
        return records;
    }
}
