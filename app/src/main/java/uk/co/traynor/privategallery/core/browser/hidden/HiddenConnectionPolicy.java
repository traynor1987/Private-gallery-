package uk.co.traynor.privategallery.core.browser.hidden;

import java.net.URI;
import java.util.Locale;

/** Generation-fenced policy. Readiness inputs must come from adapters, never preferences. */
public final class HiddenConnectionPolicy {
    public enum Mode {
        TOR_ONLY("Tor Only", "TOR"), VPN_TOR("VPN + Tor", "VPN + TOR"), VPN_ONLY("VPN Only", "VPN");
        public final String label, indicator;
        Mode(String label,String indicator) {this.label=label;this.indicator=indicator;}
        public static Mode parse(String value) {try{return valueOf(value);}catch(Exception e){return TOR_ONLY;}}
    }
    public enum State { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED }
    private Mode mode=Mode.TOR_ONLY;
    private State state=State.DISCONNECTED;
    private long epoch;
    private boolean authorized;
    public synchronized long begin(Mode selected,boolean authenticated) {
        epoch++; mode=selected;authorized=authenticated;
        state=authenticated && selected!=Mode.VPN_TOR ? State.CONNECTING : State.FAILED;
        return epoch;
    }
    public synchronized boolean ready(long request,boolean tor,boolean pinnedOwnedVpn) {
        if(request!=epoch || !authorized || state!=State.CONNECTING) return false;
        boolean verified=mode==Mode.TOR_ONLY ? tor : mode==Mode.VPN_ONLY && pinnedOwnedVpn;
        if(verified) state=State.CONNECTED;
        return verified;
    }
    public synchronized void disconnect() {epoch++;authorized=false;state=State.DISCONNECTED;}
    public synchronized void fail(long request) {if(request==epoch){epoch++;authorized=false;state=State.FAILED;}}
    /** Only the native startup probe may use this phase; renderer remains blocked until ready. */
    public synchronized boolean canEstablish(long request) {
        return request==epoch && authorized && mode!=Mode.VPN_TOR &&
            (state==State.CONNECTING || state==State.CONNECTED);
    }
    public synchronized boolean allowed() {return authorized && state==State.CONNECTED;}
    public synchronized boolean current(long request) {return request==epoch && allowed();}
    public synchronized Mode mode() {return mode;}
    public synchronized State state() {return state;}
    /** Onion names never enter VPN DNS. Tor verifies the v3 identity/checksum itself. */
    public static boolean safeUrl(String value, Mode mode) {
        if (!safeUrl(value) || mode == Mode.VPN_TOR) return false;
        String host = URI.create(value).getHost().toLowerCase(Locale.ROOT);
        if (!host.endsWith(".onion")) return true;
        return mode == Mode.TOR_ONLY && host.matches("([a-z0-9-]+\\.)*[a-z2-7]{56}\\.onion");
    }
    public static boolean safeUrl(String value) {
        try {
            URI uri=new URI(value);
            String scheme=uri.getScheme();
            String host=uri.getHost();
            if(!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) || host==null || uri.getUserInfo()!=null) return false;
            host=host.toLowerCase(Locale.ROOT);
            if(host.endsWith(".") || host.indexOf(':')>=0 || host.indexOf('[')>=0 || !host.contains(".")) return false;
            if(host.matches("[0-9.]+") || host.startsWith("0x") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return false;
            return uri.getPort()==-1 || uri.getPort()==80 || uri.getPort()==443;
        } catch(Exception e) {return false;}
    }
}
