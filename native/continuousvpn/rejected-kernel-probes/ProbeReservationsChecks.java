import uk.co.traynor.privategallery.core.vpn.continuity.ProbeReservations;
import uk.co.traynor.privategallery.core.vpn.continuity.ProbeReservations.*;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real loopback sockets only; no Android or external network. */
public final class ProbeReservationsChecks {
    static final InetAddress V4;
    static { try { V4 = InetAddress.getByAddress(new byte[]{127,0,0,1}); }
             catch (UnknownHostException e) { throw new AssertionError(e); } }
    static int checks;
    interface Action { void run() throws Exception; }
    static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    static void fails(Action action, String message) throws Exception {
        try { action.run(); } catch (IOException | IllegalStateException | IllegalArgumentException expected) {
            checks++; return;
        }
        throw new AssertionError(message);
    }
    static final class Binder implements NetworkBinder {
        final List<Socket> tcp = new ArrayList<>();
        final List<DatagramSocket> udp = new ArrayList<>();
        public void bind(Socket socket) { check(!socket.isBound(), "TCP network bind before local bind"); tcp.add(socket); }
        public void bind(DatagramSocket socket) { check(!socket.isBound(), "UDP network bind before local bind"); udp.add(socket); }
    }
    static final class Registry implements Registrar {
        final Set<Tuple> active = new HashSet<>();
        final AtomicInteger registrations = new AtomicInteger();
        volatile boolean revoke = true;
        volatile boolean throwRegister;
        volatile boolean throwRevoke;
        volatile Runnable onRevoke = () -> {};
        volatile java.util.function.Consumer<Tuple> onRegister = tuple -> {};
        public synchronized Permit register(Tuple tuple) throws IOException {
            registrations.incrementAndGet(); onRegister.accept(tuple); active.add(tuple);
            if (throwRegister) throw new IOException("injected unknown registration outcome");
            return () -> {
                onRevoke.run();
                if (throwRevoke) throw new IOException("injected unknown revocation outcome");
                if (!revoke) return false;
                synchronized (Registry.this) { active.remove(tuple); }
                return true;
            };
        }
    }
    static ProbeReservations pool(Binder binder, int tcp, int dnsPort) throws Exception {
        return new ProbeReservations(List.of(V4), new InetSocketAddress(V4, dnsPort), binder,
                tcp, 80, 500);
    }
    static void exclusiveTcp(int port) throws Exception {
        try (ServerSocket collision = new ServerSocket()) {
            collision.setReuseAddress(false);
            fails(() -> collision.bind(new InetSocketAddress(V4, port)), "reserved TCP must collide");
        }
    }
    static void exclusiveUdp(int port) throws Exception {
        try (DatagramSocket collision = new DatagramSocket(null)) {
            collision.setReuseAddress(false);
            fails(() -> collision.bind(new InetSocketAddress(V4, port)), "reserved UDP must collide");
        }
    }
    static void freeTcp(int port) throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false); socket.bind(new InetSocketAddress(V4, port)); checks++;
        }
    }
    static void freeUdp(int port) throws Exception {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(false); socket.bind(new InetSocketAddress(V4, port)); checks++;
        }
    }
    static void reservationsAndExhaustion() throws Exception {
        Binder b = new Binder(); ProbeReservations p = pool(b, 2, 53);
        check(b.tcp.size() == 2 && b.udp.size() == 1, "all descriptors precreated");
        for (Socket s : b.tcp) {
            check(s.isBound() && !s.getReuseAddress(), "exclusive TCP binding"); exclusiveTcp(s.getLocalPort());
        }
        int udpPort = b.udp.get(0).getLocalPort(); exclusiveUdp(udpPort);
        Registry r = new Registry();
        fails(() -> p.tcpFactory(V4, r).createSocket(), "claims require child seal");
        p.sealForChildAdmission();
        SocketFactory f = p.tcpFactory(V4, r);
        Socket first = f.createSocket(); first.close();
        Socket second = f.createSocket(); second.close();
        fails(f::createSocket, "no TCP refill after exhaustion");
        check(b.tcp.size() == 2 && b.udp.size() == 1, "no allocations after seal");
        fails(() -> p.disposeAfterRetirement(() -> false), "retirement guard mandatory");
        exclusiveUdp(udpPort);
        p.disposeAfterRetirement(() -> true); freeUdp(udpPort);
        fails(() -> p.udpQuery(V4, r), "retired pool unavailable");
    }
    static void realStreamsAndRegistration() throws Exception {
        Binder b = new Binder(); ProbeReservations p = pool(b, 2, 53); Registry r = new Registry();
        p.sealForChildAdmission();
        try (ServerSocket server = new ServerSocket(0, 8, V4)) {
            Socket client = p.tcpFactory(V4, r).createSocket(); int port = client.getLocalPort();
            r.onRevoke = () -> { try { exclusiveTcp(port); } catch (Exception e) { throw new AssertionError(e); } };
            r.onRegister = tuple -> {
                check(!client.isConnected(), "TCP lease registration precedes actual connect/SYN");
                check(tuple.equals(new Tuple(Protocol.TCP, V4, port, V4, server.getLocalPort())), "registrar sees exact TCP tuple");
            };
            client.connect(new InetSocketAddress(V4, server.getLocalPort()));
            try (Socket peer = server.accept()) {
                Tuple expected = new Tuple(Protocol.TCP, V4, port, V4, server.getLocalPort());
                check(r.active.contains(expected), "exact lease registered before connection");
                client.getOutputStream().write(17); check(peer.getInputStream().read() == 17, "actual socket output stream");
                peer.getOutputStream().write(23); check(client.getInputStream().read() == 23, "actual socket input stream");
                check(client.getChannel() == null, "ordinary Socket compatible with JSSE layering");
            }
            client.close(); client.close(); check(r.active.isEmpty(), "idempotent close revokes");
            r.onRevoke = () -> {}; r.onRegister = tuple -> {};
            Socket byOverload = p.tcpFactory(V4, r).createSocket(V4, server.getLocalPort());
            try (Socket accepted = server.accept()) { check(byOverload.isConnected() && accepted.isConnected(), "factory overload connects"); }
            byOverload.close();
        } finally { p.disposeAfterRetirement(() -> true); }
    }
    static void failedAttemptsAndUnknownRevocation() throws Exception {
        Binder b = new Binder(); ProbeReservations p = pool(b, 4, 53); Registry r = new Registry();
        p.sealForChildAdmission(); SocketFactory f = p.tcpFactory(V4, r);
        Socket bad = f.createSocket(); int failedPort = bad.getLocalPort();
        int closedPort; try (ServerSocket temp = new ServerSocket(0, 8, V4)) { closedPort = temp.getLocalPort(); }
        fails(() -> bad.connect(new InetSocketAddress(V4, closedPort)), "failed connection burns slot");
        check(r.active.isEmpty(), "failed connect revoked"); freeTcp(failedPort);
        Socket unresolved = f.createSocket();
        fails(() -> unresolved.connect(InetSocketAddress.createUnresolved("never-resolve.invalid", 443)), "no default resolver");
        unresolved.close();
        r.throwRegister = true; Socket ambiguous = f.createSocket(); int ambiguousPort = ambiguous.getLocalPort();
        fails(() -> ambiguous.connect(new InetSocketAddress(V4, closedPort)), "unknown register fails closed");
        ambiguous.close(); exclusiveTcp(ambiguousPort); r.throwRegister = false;
        Socket parked = f.createSocket(); int parkedPort = parked.getLocalPort();
        try (ServerSocket server = new ServerSocket(0, 8, V4)) {
            parked.connect(new InetSocketAddress(V4, server.getLocalPort()));
            try (Socket peer = server.accept()) {
                check(peer.isConnected(), "parked test peer connected");
                r.revoke = false; parked.close(); exclusiveTcp(parkedPort);
                check(!parked.isClosed(), "unknown revoke retains actual FD");
                fails(parked::getOutputStream, "parked socket rejects Java I/O");
                fails(() -> parked.sendUrgentData(42), "parked socket rejects urgent transmission");
                fails(parked::shutdownOutput, "parked socket rejects output shutdown transmission");
            }
        }
        fails(f::createSocket, "failures never replenish");
        fails(() -> p.disposeAfterRetirement(() -> false), "parked retirement still guarded");
        exclusiveTcp(ambiguousPort);
        p.disposeAfterRetirement(() -> true); freeTcp(ambiguousPort);
    }
    static void udpLifecycle() throws Exception {
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(V4, 0))) {
            Binder b = new Binder(); ProbeReservations p = pool(b, 1, server.getLocalPort()); Registry r = new Registry();
            int localPort = b.udp.get(0).getLocalPort(); p.sealForChildAdmission();
            Query q = p.udpQuery(V4, r);
            fails(() -> p.udpQuery(V4, r), "one UDP query owner");
            check(r.active.isEmpty(), "query creation has no admission");
            server.setSoTimeout(20);
            r.onRegister = tuple -> {
                try { server.receive(new DatagramPacket(new byte[8],8)); throw new AssertionError("UDP sent before registration"); }
                catch (SocketTimeoutException expected) { checks++; }
                catch (IOException failure) { throw new AssertionError(failure); }
            };
            q.send(new byte[]{1,2,3});
            r.onRegister = tuple -> {}; server.setSoTimeout(1000);
            DatagramPacket received = new DatagramPacket(new byte[8], 8); server.receive(received);
            check(r.active.contains(new Tuple(Protocol.UDP, V4, localPort, V4, server.getLocalPort())), "exact UDP registration before send");
            server.send(new DatagramPacket(new byte[]{4,5}, 2, received.getSocketAddress()));
            check(Arrays.equals(q.receive(32), new byte[]{4,5}), "UDP query receives response");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> pending = executor.submit(() -> {
                    try { q.receive(32); return false; } catch (IOException expected) { return true; }
                });
                long started = System.nanoTime(); q.cancel();
                check(pending.get(2, TimeUnit.SECONDS), "cancelled receive cannot publish");
                check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started) < 1000, "bounded UDP cancellation");
            } finally { executor.shutdownNow(); }
            check(r.active.isEmpty(), "cancel removes admission"); exclusiveUdp(localPort);
            check(!q.publishIfActive(new byte[]{1}, bytes -> { throw new AssertionError("cancelled query published"); }), "cancelled publication suppressed");
            Query next = p.udpQuery(V4, r); q.close();
            next.send(new byte[]{9}); server.receive(received);
            check(!r.active.isEmpty(), "stale close cannot revoke new lease");
            next.close(); check(r.active.isEmpty(), "new lease closes normally");
            r.revoke = false; Query unknown = p.udpQuery(V4, r); unknown.send(new byte[]{8}); unknown.close();
            exclusiveUdp(localPort); fails(() -> p.udpQuery(V4, r), "unknown UDP revoke parks reservation");
            p.disposeAfterRetirement(() -> true); freeUdp(localPort);
        }
    }

    static void jsseLayeringAndStreamClose() throws Exception {
        Binder b = new Binder(); ProbeReservations p = pool(b, 2, 53); Registry r = new Registry();
        p.sealForChildAdmission();
        try (ServerSocket server = new ServerSocket(0, 8, V4)) {
            Socket raw = p.tcpFactory(V4, r).createSocket(V4, server.getLocalPort());
            try (Socket peer = server.accept();
                 SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(raw, "localhost", server.getLocalPort(), true)) {
                check(peer.isConnected() && tls.isConnected(), "JSSE layers on real prebound Socket");
                check(tls.getLocalPort() == raw.getLocalPort(), "JSSE retains source reservation");
            }
            check(r.active.isEmpty(), "JSSE autoClose revokes underlying lease");
            Socket streams = p.tcpFactory(V4, r).createSocket(V4, server.getLocalPort());
            try (Socket peer = server.accept()) {
                OutputStream output = streams.getOutputStream(); output.close();
                check(streams.isClosed() && peer.isConnected(), "stream close routes through owner and releases FD");
                check(r.active.isEmpty(), "stream close revokes lease");
            }
        } finally { p.disposeAfterRetirement(() -> true); }
    }


    static void awaitReceive(Thread thread) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            for (StackTraceElement frame : thread.getStackTrace()) {
                if (frame.getClassName().equals("java.net.DatagramSocket") && frame.getMethodName().equals("receive")) {
                    checks++; return;
                }
            }
            if (!thread.isAlive()) break;
            Thread.sleep(1);
        }
        throw new AssertionError("receive thread never entered actual DatagramSocket.receive");
    }
    static void udpReceiveCancellationBarrier() throws Exception {
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(V4, 0))) {
            server.setSoTimeout(2000);
            Binder binder = new Binder();
            ProbeReservations p = new ProbeReservations(List.of(V4), new InetSocketAddress(V4,server.getLocalPort()), binder,1,500,500);
            Registry oldRegistry = new Registry(); p.sealForChildAdmission(); Query old = p.udpQuery(V4,oldRegistry);
            old.send(new byte[]{1}); DatagramPacket request = new DatagramPacket(new byte[16],16); server.receive(request);
            AtomicReference<Throwable> result = new AtomicReference<>();
            Thread receiver = new Thread(() -> {
                try { old.receive(16); result.set(new AssertionError("old receive published")); }
                catch (IOException expected) { result.set(expected); }
            }, "probe-host-blocked-receive");
            receiver.start(); awaitReceive(receiver);
            fails(() -> old.receive(16), "concurrent receive rejected");
            fails(() -> old.send(new byte[]{2}), "concurrent send rejected");
            oldRegistry.onRevoke = () -> {
                check(receiver.isAlive(), "admission removed before waiting for old receive");
                try { exclusiveUdp(binder.udp.get(0).getLocalPort()); } catch (Exception e) { throw new AssertionError(e); }
            };
            check(old.cancel(), "cancel waits for old operation before reservation reusable");
            receiver.join(2000); check(!receiver.isAlive() && result.get() instanceof IOException, "cancelled receive fully exited");
            Registry newRegistry = new Registry(); Query newer = p.udpQuery(V4,newRegistry);
            old.close(); newer.send(new byte[]{3}); server.receive(request);
            server.send(new DatagramPacket(new byte[]{7},1,request.getSocketAddress()));
            check(Arrays.equals(newer.receive(16),new byte[]{7}), "old reader cannot consume new generation response");
            check(newer.publishIfActive(new byte[]{7}, bytes -> check(bytes[0] == 7, "active publication allowed")), "publication succeeds while active");
            newer.close(); check(newRegistry.active.isEmpty(), "new generation owns independent permit");
            p.disposeAfterRetirement(() -> true);
        }
    }
    static void factoryOverloadsAndUnknownExceptions() throws Exception {
        Binder b = new Binder(); ProbeReservations p = pool(b, 4, 53); Registry registry = new Registry(); p.sealForChildAdmission();
        SocketFactory factory = p.tcpFactory(V4,registry);
        fails(() -> factory.createSocket("external.invalid",443), "hostname overload never calls resolver");
        fails(() -> factory.createSocket("127.000.0.1",443), "ambiguous numeric address rejected");
        fails(() -> factory.createSocket(V4,443, V4,12345), "caller cannot replace pre-reserved binding");
        try (ServerSocket server = new ServerSocket(0,8,V4)) {
            Socket a = factory.createSocket("127.0.0.1",server.getLocalPort());
            try (Socket peer = server.accept()) { check(peer.isConnected(),"numeric hostname overload connects"); }
            a.close();
            Socket second = factory.createSocket(V4,server.getLocalPort(),V4,0);
            try (Socket peer = server.accept()) { check(peer.isConnected(),"address/local overload connects"); }
            second.close();
            Socket third = factory.createSocket("127.0.0.1",server.getLocalPort(),V4,0);
            try (Socket peer = server.accept()) { check(peer.isConnected(),"numeric/local overload connects"); }
            third.close();
            Socket unknown = factory.createSocket(); int port = unknown.getLocalPort();
            unknown.connect(new InetSocketAddress(V4,server.getLocalPort()));
            try (Socket peer = server.accept()) {
                check(peer.isConnected(),"unknown revoke test connected"); registry.throwRevoke = true;
                unknown.close(); exclusiveTcp(port);
                check(!registry.active.isEmpty(),"unknown native removal has no false acknowledgment");
            }
        }
        fails(factory::createSocket,"only four original sockets ever available");
        p.disposeAfterRetirement(() -> true);
        Binder udpBinder = new Binder(); ProbeReservations udpPool = pool(udpBinder,1,53); Registry udpRegistry = new Registry();
        udpPool.sealForChildAdmission(); udpRegistry.throwRegister = true;
        Query unknownQuery = udpPool.udpQuery(V4,udpRegistry);
        fails(() -> unknownQuery.send(new byte[]{1}),"unknown UDP register blocks send"); unknownQuery.close();
        exclusiveUdp(udpBinder.udp.get(0).getLocalPort());
        fails(() -> udpPool.udpQuery(V4,new Registry()),"unknown UDP registration remains unavailable");
        fails(() -> udpPool.disposeAfterRetirement(() -> { throw new IllegalStateException("no proof"); }),"throwing retirement proof cannot release");
        exclusiveUdp(udpBinder.udp.get(0).getLocalPort()); udpPool.disposeAfterRetirement(() -> true);
    }

    static void validationAndBinderFailure() throws Exception {
        fails(() -> new ProbeReservations(List.of(InetAddress.getByAddress(new byte[4])),
                new InetSocketAddress(V4,53), new Binder(),1,80,500), "no wildcard reservations");
        Binder b = new Binder();
        NetworkBinder broken = new NetworkBinder() {
            public void bind(Socket s) throws IOException { b.bind(s); throw new IOException("injected"); }
            public void bind(DatagramSocket s) throws IOException { throw new IOException("injected"); }
        };
        fails(() -> new ProbeReservations(List.of(V4),new InetSocketAddress(V4,53),broken,1,80,500), "binder failure aborts construction");
        check(b.tcp.get(0).isClosed(), "constructor failure releases unpublished socket");
    }
    public static void main(String[] args) throws Exception {
        reservationsAndExhaustion(); realStreamsAndRegistration();
        failedAttemptsAndUnknownRevocation(); udpLifecycle(); jsseLayeringAndStreamClose(); udpReceiveCancellationBarrier(); factoryOverloadsAndUnknownExceptions(); validationAndBinderFailure();
        System.out.println("ProbeReservationsChecks: " + checks + " checks passed");
    }
}
