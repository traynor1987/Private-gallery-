// SPDX-License-Identifier: MIT
package uk.co.traynor.privategallery.core.vpn.continuity;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
/**
 * Owner-private foundation. Never expose handles/configuration or these methods
 * over child IPC. This class is deliberately unreferenced by the active backend;
 * production must never instantiate it alongside the original GoBackend runtime.
 * Only epoch, sequence, publish, invalidate and invalidateTransport are safe
 * under authentication/session monitors. Every other call may block. Replacement,
 * retirement and final release are forbidden inside those monitors.
 * Epoch must be captured within authenticated replacement admission and carried
 * unchanged to the worker. Generation is strictly increasing per fence; returned
 * transport and lease handles are distinct process-unique positive longs.
 */
final class NativeVpnFence {
    static final long OK = 0, INVALID = -1, DENIED = -2, BUSY = -3, FAILED = -4, EXHAUSTED = -5;
    private NativeVpnFence() {}
    private static volatile boolean loaded;
    private static final class Library { static { System.loadLibrary("nova-wg"); } static void load() {} }
    interface Protector {
        /** Synchronous VpnService.protect(fd) ONLY. Never reenter any owner,
         * bridge, session or authentication API. JNI clears/denies exceptions.
         * The native configuration/FD-lifetime locks are held by design. */
        boolean protect(int fd);
    }
    /** Java retains original throughout. A temporary Java duplicate is closed
     * in finally; JNI duplicates it at entry and consumes THAT owned duplicate
     * on every result. Failure before JNI entry leaves Java ownership intact.
     * The source must already be nonblocking; native never changes shared flags. */
    static long create(ParcelFileDescriptor original, int mtu, String trustedName) {
        try { Library.load(); loaded = true; } catch (LinkageError error) { return FAILED; }
        if (original == null) return INVALID;
        try (ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(original.getFileDescriptor())) {
            return nCreate(duplicate.getFd(), mtu, trustedName);
        } catch (IOException | IllegalStateException error) { return FAILED; }
    }
    static long replace(long fence, long generation, long expectedEpoch, String privateKeyHex,
                        String peerKeyHex, String presharedKeyHex, String numericEndpoint,
                        String commaSeparatedAllowedPrefixes, int keepalive, int listenPort,
                        Protector protector) {
        if (!loaded) return INVALID;
        return nReplace(fence,generation,expectedEpoch,privateKeyHex,peerKeyHex,presharedKeyHex,
                numericEndpoint,commaSeparatedAllowedPrefixes,keepalive,listenPort,protector);
    }
    static long epoch(long fence) { if (!loaded) return INVALID; return nEpoch(fence); }
    static long beginProbe(long fence,long transport,long epoch) { if (!loaded) return INVALID; return nBeginProbe(fence,transport,epoch); }
    /** Generic gate lease only: does not prove kernel port ownership. Parent
     * readiness/probe provenance needs the later independently reviewed design. */
    static long register(long fence,long transport,long epoch,int protocol,String source,String destination) {
        if (!loaded) return INVALID;
        return nRegister(fence,transport,epoch,protocol,source,destination);
    }
    /** Success removes future admission; it is not an I/O drain receipt. */
    static long remove(long fence,long transport,long epoch,long lease) { if (!loaded) return INVALID; return nRemove(fence,transport,epoch,lease); }
    static long sequence(long fence,long transport,long epoch) { if (!loaded) return INVALID; return nSequence(fence,transport,epoch); }
    static long publish(long fence,long transport,long epoch,long sequence) { if (!loaded) return INVALID; return nPublish(fence,transport,epoch,sequence); }
    static long invalidate(long fence) { if (!loaded) return INVALID; return nInvalidate(fence); }
    static long invalidateTransport(long fence,long transport) { if (!loaded) return INVALID; return nInvalidateTransport(fence,transport); }
    static long retire(long fence,long transport) { if (!loaded) return INVALID; return nRetire(fence,transport); }
    /** Cleanup for failed Replace; original generation/epoch prevents stale
     * cleanup from touching a newer transport. Runs outside session monitors. */
    static long retireCurrent(long fence,long generation,long replacementEpoch) { if (!loaded) return INVALID; return nRetireCurrent(fence,generation,replacementEpoch); }
    /** Explicit external proof assertion by the trusted owner. Native cannot
     * infer sensitive child death. False always denies final lifetime release. */
    static long release(long fence,boolean allSensitiveChildrenDead) { if (!loaded) return INVALID; return nRelease(fence,allSensitiveChildrenDead); }
    /** [status, handshake seconds, handshake nanos, rx bytes, tx bytes]. */
    static long[] statistics(long fence,long transport,long epoch) {
        if (!loaded) return new long[]{INVALID,0,0,0,0};
        long[] result=nStatistics(fence,transport,epoch);
        return result == null ? new long[]{FAILED,0,0,0,0} : result;
    }
    private static native long nCreate(int borrowedDuplicate,int mtu,String name);
    private static native long nReplace(long f,long generation,long epoch,String privateKey,String peerKey,String psk,String endpoint,String routes,int keepalive,int listenPort,Protector protector);
    private static native long nEpoch(long f);
    private static native long nBeginProbe(long f,long t,long e);
    private static native long nRegister(long f,long t,long e,int protocol,String source,String destination);
    private static native long nRemove(long f,long t,long e,long lease);
    private static native long nSequence(long f,long t,long e);
    private static native long nPublish(long f,long t,long e,long sequence);
    private static native long nInvalidate(long f);
    private static native long nInvalidateTransport(long f,long t);
    private static native long nRetire(long f,long t);
    private static native long nRetireCurrent(long f,long generation,long replacementEpoch);
    private static native long nRelease(long f,boolean proof);
    private static native long[] nStatistics(long f,long t,long e);
}
