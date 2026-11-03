package uk.co.traynor.privategallery.core.vpn.continuity;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.net.SocketFactory;

/**
 * Owner-private, pre-child source-binding ledger. Construction reserves every descriptor; sealing
 * permits borrowing, never allocation/refill. Create a new pool AND fence only after the previous
 * sensitive producers retire. This class establishes Java descriptor ownership, not Android VPN
 * policy, wildcard/mapped-family exclusivity, or child/process retirement.
 *
 * <p>Future JNI adapter: supply a generation-scoped Registrar whose Permit captures immutable fence,
 * generation and unique lease identity. register must admit only the exact Tuple. revoke must close
 * native admission synchronously and acknowledge exact lease removal; false/throw means UNKNOWN.
 * This receipt does not assert full transport drain or completion of callbacks already admitted.
 * Callbacks must be bounded and must not reenter this pool or acquire caller-held session locks.
 * The pool keeps UNKNOWN descriptors physically bound until trusted final retirement.
 *
 * <p>Future OwnedVpnDns optional ProbeContext: use udpQuery for this pool's fixed numeric DNS target;
 * use tcpFactory(concrete same-family local address, registrar) for a separately burned TCP fallback.
 * Readiness HTTPS uses an IPv4 factory and explicitly IPv4 DNS results. Do not supply these factories
 * to ordinary browsing. Never use the platform resolver or default sockets on failure/exhaustion.
 * DNS adapters must validate DNS IDs/question/source, bound payloads and use publishIfActive for
 * publication; a retained UDP socket may contain delayed responses from an earlier parent query.
 * TLS may layer on the real returned Socket, retaining ownership and autoClose=true. A parked TCP
 * socket rejects Java I/O after close, but isClosed() correctly reports that its FD is still open;
 * clients must not interpret isClosed() as a native lease removal receipt.
 */
public final class ProbeReservations {
    public enum Protocol { TCP, UDP }

    /** Numeric addresses only; family is derived from their canonical 4/16-byte representation. */
    public record Tuple(Protocol protocol, InetAddress localAddress, int localPort,
                        InetAddress remoteAddress, int remotePort) {
        public Tuple {
            Objects.requireNonNull(protocol, "protocol");
            concrete(localAddress); concrete(remoteAddress);
            if (!sameFamily(localAddress, remoteAddress)) throw new IllegalArgumentException("family mismatch");
            port(localPort); port(remotePort);
        }
        public int family() { return localAddress instanceof Inet4Address ? 4 : 6; }
        @Override public String toString() { return "ProbeTuple[redacted]"; }
    }

    /** Network.bindSocket equivalent. Invoked once per real unbound socket before concrete bind. */
    public interface NetworkBinder {
        void bind(Socket socket) throws IOException;
        void bind(DatagramSocket socket) throws IOException;
    }
    @FunctionalInterface public interface Registrar {
        /** Any exception is treated as possibly admitted; the descriptor is parked. */
        Permit register(Tuple tuple) throws IOException;
    }
    @FunctionalInterface public interface Permit {
        /** true only after exact lease removal acknowledgment; not a full transport-drain receipt. */
        boolean revoke() throws IOException;
    }

    private final List<ReservedTcp> tcp = new ArrayList<>();
    private final List<ReservedUdp> udp = new ArrayList<>();
    private final InetSocketAddress dnsTarget;
    private final int receiveTimeoutMillis;
    private final int connectTimeoutMillis;
    private boolean sealed;
    private boolean retired;

    /**
     * @param tcpSlotsPerAddress fixed count (1..256) at EACH local address; all allocated immediately
     * @param receiveTimeoutMillis bounded UDP receive interval (1..5000 ms)
     * @param connectTimeoutMillis maximum TCP connect timeout (1..30000 ms), including connect(...,0)
     */
    public ProbeReservations(List<InetAddress> localAddresses, InetSocketAddress dnsTarget,
                             NetworkBinder binder, int tcpSlotsPerAddress,
                             int receiveTimeoutMillis, int connectTimeoutMillis) throws IOException {
        Objects.requireNonNull(localAddresses, "localAddresses");
        Objects.requireNonNull(binder, "binder");
        this.dnsTarget = endpoint(dnsTarget);
        if (localAddresses.isEmpty() || localAddresses.size() > 8 || tcpSlotsPerAddress < 1
                || tcpSlotsPerAddress > 256 || receiveTimeoutMillis < 1 || receiveTimeoutMillis > 5000
                || connectTimeoutMillis < 1 || connectTimeoutMillis > 30000) {
            throw new IllegalArgumentException("invalid reservation bounds");
        }
        this.receiveTimeoutMillis = receiveTimeoutMillis;
        this.connectTimeoutMillis = connectTimeoutMillis;
        List<InetAddress> addresses = List.copyOf(localAddresses);
        Set<InetAddress> unique = new HashSet<>();
        boolean dnsFamilyPresent = false;
        for (InetAddress address : addresses) {
            concrete(address);
            if (!unique.add(address)) throw new IllegalArgumentException("duplicate local address");
            dnsFamilyPresent |= sameFamily(address, dnsTarget.getAddress());
        }
        if (!dnsFamilyPresent) throw new IllegalArgumentException("DNS family unavailable");
        try {
            for (InetAddress address : addresses) {
                for (int i = 0; i < tcpSlotsPerAddress; i++) {
                    ReservedTcp socket = new ReservedTcp(address);
                    tcp.add(socket); // retain before any callback that could fail
                    socket.prebind(binder);
                }
                if (sameFamily(address, dnsTarget.getAddress())) {
                    ReservedUdp reservation = new ReservedUdp(address);
                    udp.add(reservation);
                    binder.bind(reservation.socket);
                    reservation.socket.bind(new InetSocketAddress(address, 0));
                    reservation.socket.setSoTimeout(receiveTimeoutMillis);
                }
            }
        } catch (IOException | RuntimeException failure) {
            // No reference has escaped construction and no child is admitted yet.
            for (ReservedTcp socket : tcp) try { socket.forceClose(); } catch (IOException ignored) { }
            for (ReservedUdp reservation : udp) reservation.socket.close();
            throw new IOException("probe reservation construction failed", failure);
        }
    }

    /** Caller must perform this before admitting any child; it never creates descriptors. */
    public synchronized void sealForChildAdmission() {
        if (retired) throw new IllegalStateException("pool retired");
        sealed = true;
    }

    /**
     * Real SocketFactory, scoped to an exact pre-reserved local address and generation registrar.
     * Every createSocket burns one slot, including an unconnected socket later abandoned by TLS.
     * Host-name overloads accept strict dotted IPv4 literals only; use InetAddress overloads for IPv6.
     * Local-binding overloads must name the reservation's concrete address and port zero.
     */
    public SocketFactory tcpFactory(InetAddress localAddress, Registrar registrar) {
        concrete(localAddress); Objects.requireNonNull(registrar, "registrar");
        return new SocketFactory() {
            @Override public Socket createSocket() throws IOException { return claim(localAddress, registrar); }
            @Override public Socket createSocket(InetAddress host, int remotePort) throws IOException {
                Socket socket = createSocket();
                try { socket.connect(new InetSocketAddress(host, remotePort)); return socket; }
                catch (IOException | RuntimeException e) { socket.close(); throw e; }
            }
            @Override public Socket createSocket(String host, int remotePort) throws IOException {
                return createSocket(numericV4(host), remotePort);
            }
            @Override public Socket createSocket(InetAddress host, int remotePort,
                                                  InetAddress local, int localPort) throws IOException {
                requireLocal(local, localPort); return createSocket(host, remotePort);
            }
            @Override public Socket createSocket(String host, int remotePort,
                                                  InetAddress local, int localPort) throws IOException {
                requireLocal(local, localPort); return createSocket(host, remotePort);
            }
            private void requireLocal(InetAddress local, int localPort) throws IOException {
                if (!localAddress.equals(local) || localPort != 0) throw new IOException("unreserved local binding");
            }
        };
    }

    private synchronized Socket claim(InetAddress localAddress, Registrar registrar) throws IOException {
        available();
        for (ReservedTcp socket : tcp) {
            if (!socket.claimed && socket.local.equals(localAddress)) {
                socket.claimed = true;
                socket.registrar = registrar;
                return socket;
            }
        }
        throw new IOException("pre-reserved TCP slots exhausted");
    }

    /** Fixed DNS target only. No raw DatagramSocket escapes and logical close never closes its FD. */
    public synchronized Query udpQuery(InetAddress localAddress, Registrar registrar) throws IOException {
        available(); Objects.requireNonNull(registrar, "registrar");
        for (ReservedUdp reservation : udp) {
            if (reservation.local.equals(localAddress)) synchronized (reservation) {
                if (reservation.parked || reservation.current != null) throw new IOException("UDP reservation unavailable");
                Query query = new Query(reservation, registrar);
                reservation.current = query;
                return query;
            }
        }
        throw new IOException("pre-reserved DNS family unavailable");
    }

    private void available() throws IOException {
        if (!sealed || retired) throw new IOException("pool not available");
    }

    /**
     * The ONLY final FD destruction path. Predicate is owner-trusted: all sensitive producers AND
     * the native fence/lease registry must already be retired, so even UNKNOWN permits cannot admit.
     * This Java class cannot establish that proof. False/throw leaves the whole pool unchanged.
     * Does not reset/refill a pool; retirement is terminal. Safe to repeat with a valid proof.
     */
    public void disposeAfterRetirement(BooleanSupplier trustedReleasePredicate) throws IOException {
        Objects.requireNonNull(trustedReleasePredicate, "trustedReleasePredicate");
        synchronized (this) {
            if (!trustedReleasePredicate.getAsBoolean()) throw new IllegalStateException("retirement not proven");
            retired = true;
        }
        IOException failure = null;
        for (ReservedUdp reservation : udp) {
            Query query;
            synchronized (reservation) { query = reservation.current; reservation.parked = true; }
            if (query != null) query.cancel();
            reservation.socket.close();
        }
        for (ReservedTcp socket : tcp) {
            try { socket.forceClose(); } catch (IOException e) { failure = e; }
        }
        if (failure != null) throw new IOException("reservation retirement failed", failure);
    }

    private final class ReservedTcp extends Socket {
        private final InetAddress local;
        private final Object lifecycle = new Object();
        private boolean claimed;
        private Registrar registrar;
        private boolean attempted;
        private boolean logicalClosed;
        private boolean parked;
        private Permit permit;
        private InputStream input;
        private OutputStream output;
        ReservedTcp(InetAddress local) { super(); this.local = local; }
        void prebind(NetworkBinder binder) throws IOException {
            super.setReuseAddress(false);
            binder.bind(this);
            super.bind(new InetSocketAddress(local, 0));
        }
        @Override public void bind(SocketAddress endpoint) throws IOException { throw new IOException("binding already reserved"); }
        @Override public void setReuseAddress(boolean on) throws SocketException {
            if (on) throw new SocketException("reuse forbidden");
            super.setReuseAddress(false);
        }
        @Override public <T> Socket setOption(SocketOption<T> option, T value) throws IOException {
            if ((option == StandardSocketOptions.SO_REUSEADDR || option == StandardSocketOptions.SO_REUSEPORT)
                    && Boolean.TRUE.equals(value)) throw new SocketException("reuse forbidden");
            return super.setOption(option, value);
        }
        @Override public void connect(SocketAddress remote) throws IOException { connect(remote, connectTimeoutMillis); }
        @Override public void connect(SocketAddress remote, int timeout) throws IOException {
            try {
                synchronized (lifecycle) {
                    requireOpen();
                    if (!claimed || attempted) throw new IOException("TCP slot already attempted or unclaimed");
                    attempted = true;
                    InetSocketAddress target = endpoint(remote);
                    if (!sameFamily(local, target.getAddress()) || timeout < 0) throw new IOException("invalid TCP target");
                    // register throwing/null can have partially admitted the tuple: hold its binding forever.
                    parked = true;
                    permit = registrar.register(new Tuple(Protocol.TCP, local, getLocalPort(),
                            target.getAddress(), target.getPort()));
                    if (permit == null) throw new IOException("missing revocation permit");
                    parked = false;
                }
                super.connect(remote, timeout == 0 ? connectTimeoutMillis : Math.min(timeout, connectTimeoutMillis));
            } catch (IOException | RuntimeException failure) {
                try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        private void requireOpen() throws SocketException {
            if (logicalClosed || super.isClosed()) throw new SocketException("probe socket closed");
        }
        private void requireOpenForIo() throws SocketException {
            synchronized (lifecycle) { requireOpen(); }
        }
        @Override public void sendUrgentData(int data) throws IOException { requireOpenForIo(); super.sendUrgentData(data); }
        @Override public void shutdownInput() throws IOException { requireOpenForIo(); super.shutdownInput(); }
        @Override public void shutdownOutput() throws IOException { requireOpenForIo(); super.shutdownOutput(); }
        @Override public InputStream getInputStream() throws IOException {
            synchronized (lifecycle) {
                requireOpen();
                if (input == null) input = new FilterInputStream(super.getInputStream()) {
                    @Override public int read() throws IOException { requireOpenForIo(); return in.read(); }
                    @Override public int read(byte[] b, int off, int len) throws IOException {
                        requireOpenForIo(); return in.read(b, off, len);
                    }
                    @Override public void close() throws IOException { ReservedTcp.this.close(); }
                };
                return input;
            }
        }
        @Override public OutputStream getOutputStream() throws IOException {
            synchronized (lifecycle) {
                requireOpen();
                if (output == null) output = new FilterOutputStream(super.getOutputStream()) {
                    @Override public void write(int value) throws IOException { requireOpenForIo(); out.write(value); }
                    @Override public void write(byte[] b, int off, int len) throws IOException {
                        requireOpenForIo(); out.write(b, off, len);
                    }
                    @Override public void flush() throws IOException { requireOpenForIo(); out.flush(); }
                    @Override public void close() throws IOException { ReservedTcp.this.close(); }
                };
                return output;
            }
        }
        @Override public void close() throws IOException {
            synchronized (lifecycle) {
                if (logicalClosed) return;
                logicalClosed = true;
                if (permit != null) {
                    parked = !revoke(permit);
                    permit = null;
                }
                if (!parked) super.close();
            }
        }
        void forceClose() throws IOException {
            close(); // attempt revocation first even when trusted destruction is permitted
            synchronized (lifecycle) { logicalClosed = true; super.close(); }
        }
        @Override public String toString() { return "ProbeSocket[redacted]"; }
    }

    private final class ReservedUdp {
        final InetAddress local;
        final DatagramSocket socket;
        Query current;
        boolean parked;
        ReservedUdp(InetAddress local) throws SocketException {
            this.local = local;
            socket = new DatagramSocket(null);
            try { socket.setReuseAddress(false); }
            catch (SocketException failure) { socket.close(); throw failure; }
        }
    }

    /** Identity-scoped UDP owner. At most one in-flight send/receive per query. */
    public final class Query implements AutoCloseable {
        private final ReservedUdp reservation;
        private final Registrar registrar;
        private Permit permit;
        private boolean attemptedRegistration;
        private boolean cancelled;
        private boolean reusable;
        private int operations;
        Query(ReservedUdp reservation, Registrar registrar) { this.reservation = reservation; this.registrar = registrar; }
        private void live() throws SocketException {
            if (cancelled || reservation.current != this || reservation.parked) throw new SocketException("query inactive");
        }
        private void begin(boolean sending) throws IOException {
            synchronized (reservation) {
                live();
                if (operations != 0) throw new IOException("query operation already active");
                if (!attemptedRegistration) {
                    if (!sending) throw new IOException("send required before receive");
                    attemptedRegistration = true;
                    try {
                        permit = registrar.register(new Tuple(Protocol.UDP, reservation.local,
                                reservation.socket.getLocalPort(), dnsTarget.getAddress(), dnsTarget.getPort()));
                        if (permit == null) throw new IOException("missing revocation permit");
                    } catch (IOException | RuntimeException failure) {
                        cancelled = true; reservation.parked = true;
                        throw failure;
                    }
                }
                operations++;
            }
        }
        private void end() { synchronized (reservation) { operations--; reservation.notifyAll(); } }
        public void send(byte[] payload) throws IOException {
            Objects.requireNonNull(payload, "payload");
            if (payload.length < 1 || payload.length > 65507) throw new IllegalArgumentException("UDP payload bounds");
            begin(true);
            try {
                reservation.socket.send(new DatagramPacket(payload, payload.length, dnsTarget));
                synchronized (reservation) { live(); }
            } finally { end(); }
        }
        /** Bounded receive; result is linearized before cancellation. Use publishIfActive for publication. */
        public byte[] receive(int maxBytes) throws IOException {
            if (maxBytes < 1 || maxBytes > 65507) throw new IllegalArgumentException("UDP response bounds");
            begin(false);
            try {
                DatagramPacket packet = new DatagramPacket(new byte[maxBytes], maxBytes);
                reservation.socket.receive(packet);
                synchronized (reservation) {
                    live();
                    if (!dnsTarget.getAddress().equals(packet.getAddress()) || dnsTarget.getPort() != packet.getPort()) {
                        throw new IOException("unexpected DNS response source");
                    }
                    return Arrays.copyOf(packet.getData(), packet.getLength());
                }
            } finally { end(); }
        }
        /** Callback must be bounded/nonblocking and must not reenter pool/query APIs. */
        public boolean publishIfActive(byte[] response, Consumer<byte[]> publisher) {
            Objects.requireNonNull(response, "response"); Objects.requireNonNull(publisher, "publisher");
            synchronized (reservation) {
                if (cancelled || reservation.current != this || reservation.parked) return false;
                publisher.accept(response);
                return true;
            }
        }
        /**
         * Marks cancelled before revocation, then waits at most receive interval + 250 ms for I/O.
         * Returns true iff admission removal AND operation quiescence allow another borrower.
         * Unknown revoke, interruption or timeout permanently parks this reservation. No FD close.
         */
        public boolean cancel() {
            synchronized (reservation) {
                if (cancelled) return reusable;
                cancelled = true;
                boolean removed = permit == null || revoke(permit);
                permit = null;
                if (!removed) reservation.parked = true;
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(receiveTimeoutMillis + 250L);
                while (operations != 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) { reservation.parked = true; break; }
                    try { TimeUnit.NANOSECONDS.timedWait(reservation, remaining); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); reservation.parked = true; break;
                    }
                }
                reusable = removed && operations == 0 && !reservation.parked;
                if (reusable && reservation.current == this) reservation.current = null;
                return reusable;
            }
        }
        @Override public void close() { cancel(); }
    }

    private static boolean revoke(Permit permit) {
        try { return permit.revoke(); }
        catch (IOException | RuntimeException unknown) { return false; }
    }
    private static InetSocketAddress endpoint(SocketAddress remote) throws IOException {
        if (!(remote instanceof InetSocketAddress address) || address.isUnresolved()) throw new IOException("numeric endpoint required");
        concrete(address.getAddress()); port(address.getPort()); return address;
    }
    private static void port(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port");
    }
    private static boolean sameFamily(InetAddress a, InetAddress b) { return a.getAddress().length == b.getAddress().length; }
    private static void concrete(InetAddress address) {
        Objects.requireNonNull(address, "address");
        if (address.isAnyLocalAddress() || address.isMulticastAddress()) throw new IllegalArgumentException("concrete unicast required");
        if (address instanceof Inet6Address v6) {
            byte[] bytes = address.getAddress(); boolean mapped = bytes[10] == (byte) 255 && bytes[11] == (byte) 255;
            for (int i = 0; i < 10; i++) mapped &= bytes[i] == 0;
            if (mapped || v6.getScopeId() != 0 || address.isLinkLocalAddress()) throw new IllegalArgumentException("ambiguous IPv6 form");
        } else if (!(address instanceof Inet4Address)) throw new IllegalArgumentException("unsupported family");
    }
    private static InetAddress numericV4(String literal) throws IOException {
        if (literal == null) throw new IOException("numeric IPv4 required");
        String[] parts = literal.split("\\.", -1);
        if (parts.length != 4) throw new IOException("numeric IPv4 required");
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3 || (part.length() > 1 && part.charAt(0) == '0')) throw new IOException("numeric IPv4 required");
            int value = 0;
            for (int j = 0; j < part.length(); j++) {
                char digit = part.charAt(j);
                if (digit < '0' || digit > '9') throw new IOException("numeric IPv4 required");
                value = value * 10 + digit - '0';
            }
            if (value > 255) throw new IOException("numeric IPv4 required");
            bytes[i] = (byte) value;
        }
        return InetAddress.getByAddress(bytes);
    }
}
