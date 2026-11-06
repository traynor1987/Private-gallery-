package uk.co.traynor.privategallery.core.ui.status;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Host executable: real presentation and lifecycle policy, with a simulated event source. */
public final class SystemStatusChecks {
    private static int checks;
    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        check(SystemStatusSnapshot.batteryPercent(0, 100) == 0, "zero battery must remain zero");
        check(SystemStatusSnapshot.batteryPercent(100, 100) == 100, "full battery");
        check(SystemStatusSnapshot.batteryPercent(73, 100) == 73, "exact battery percentage");
        check(SystemStatusSnapshot.batteryPercent(3, 4) == 75, "scaled Android reading");
        check(SystemStatusSnapshot.batteryPercent(-1, 100) == null, "unknown battery omitted");
        check(SystemStatusSnapshot.batteryPercent(101, 100) == null, "invalid battery omitted");
        check(SystemStatusSnapshot.batteryPercent(40, 0) == null, "invalid scale omitted");
        check(snapshot(0, false, false, false, false, SystemStatusSnapshot.Transport.UNKNOWN,
                false, null).batteryFillFraction() == 0f, "zero battery glyph must have empty interior");
        check(snapshot(100, false, false, false, false, SystemStatusSnapshot.Transport.UNKNOWN,
                false, null).batteryFillFraction() == 1f, "full battery glyph must have full interior");
        check(snapshot(73, false, false, false, false, SystemStatusSnapshot.Transport.UNKNOWN,
                false, null).batteryFillFraction() == 0.73f, "battery glyph uses actual fraction");
        SystemStatusSnapshot wifi = snapshot(73, false, false, false, false,
                SystemStatusSnapshot.Transport.WIFI, true, null);
        check(wifi.networkLabel().equals("Wi-Fi"), "validated wifi");
        SystemStatusSnapshot noInternet = snapshot(73, false, false, false, false,
                SystemStatusSnapshot.Transport.WIFI, false, null);
        check(noInternet.networkLabel().equals("Wi-Fi · No internet"), "association must not claim internet");
        check(snapshot(4, false, false, false, true, SystemStatusSnapshot.Transport.DISCONNECTED,
                false, null).networkLabel().equals("Airplane mode"), "actual airplane state");
        check(snapshot(4, false, false, false, true, SystemStatusSnapshot.Transport.WIFI,
                true, null).networkLabel().equals("Wi-Fi"), "wifi can be enabled in airplane mode");
        check(snapshot(4, false, false, false, false, SystemStatusSnapshot.Transport.DISCONNECTED,
                false, null).networkLabel().equals("Offline"), "disconnected state");
        check(snapshot(null, false, false, false, false, SystemStatusSnapshot.Transport.UNKNOWN,
                false, null).networkLabel().isEmpty(), "restricted detail must be omitted");
        FakeProvider provider = new FakeProvider();
        List<SystemStatusSnapshot> shown = new ArrayList<>();
        SystemStatusSession session = new SystemStatusSession(provider, shown::add);
        session.setVisible(true, true, false, false);
        provider.send(wifi);
        check(shown.size() == 1 && shown.get(0).batteryPercent == 73, "provider update delivered");
        provider.send(snapshot(74, true, true, true, false, SystemStatusSnapshot.Transport.MOBILE, true, "5G"));
        check(shown.get(1).chargerConnected && shown.get(1).charging && shown.get(1).powerSaver,
                "charger charging and saver remain independent actual fields");
        check(shown.get(1).networkLabel().equals("5G"), "Android reported 5G");
        provider.send(snapshot(74, true, false, false, false, SystemStatusSnapshot.Transport.MOBILE, true, "LTE"));
        check(shown.get(2).networkLabel().equals("LTE"), "5G to LTE handover updates");
        check(shown.get(2).chargerConnected && !shown.get(2).charging, "plugged does not invent charging");
        provider.send(snapshot(74, false, false, false, false, SystemStatusSnapshot.Transport.MOBILE, true, null));
        check(shown.get(3).networkLabel().equals("Mobile"), "restricted radio type omitted, no 4G guess");
        provider.send(snapshot(74, false, false, false, false, SystemStatusSnapshot.Transport.OTHER, true, null));
        check(shown.get(4).networkLabel().equals("Connected"), "virtual transport never claims secured VPN");
        Consumer<SystemStatusSnapshot> stale = provider.listener;
        session.setVisible(true, true, false, true);
        stale.accept(wifi);
        check(shown.size() == 5 && provider.closes == 1, "real status bar prevents duplicate and closes source");
        session.setVisible(true, true, false, true);
        check(provider.opens == 1, "visible Android bar must not subscribe");
        session.setVisible(true, true, false, false);
        provider.send(noInternet);
        check(shown.size() == 6, "resume gets fresh updates");
        stale.accept(wifi);
        check(shown.size() == 6, "retired subscription rejected after restart");
        session.setVisible(true, false, false, false);
        check(provider.closes == 2, "background stops source");
        session.setVisible(true, true, true, false);
        check(provider.opens == 2, "locked surface cannot subscribe");
        session.setVisible(false, true, false, false);
        check(provider.opens == 2, "covered or disabled surface cannot subscribe");
        session.setVisible(true, true, false, false);
        session.close();
        provider.listener.accept(wifi);
        check(provider.closes == 3 && shown.size() == 6, "dispose rejects queued callback");
        session.setVisible(true, true, false, false);
        check(provider.opens == 3, "disposed session cannot reopen");
        System.out.println(checks + " system status checks passed");
    }
    private static SystemStatusSnapshot snapshot(Integer percent, boolean plugged, boolean charging,
            boolean saver, boolean airplane, SystemStatusSnapshot.Transport transport, boolean validated, String type) {
        return new SystemStatusSnapshot(percent, plugged, charging, saver, airplane, transport, validated, type);
    }
    private static final class FakeProvider implements SystemStatusProvider {
        int opens, closes;
        Consumer<SystemStatusSnapshot> listener;
        public AutoCloseable subscribe(Consumer<SystemStatusSnapshot> observer) {
            opens++;
            listener = observer;
            return () -> closes++;
        }
        void send(SystemStatusSnapshot value) { listener.accept(value); }
    }
}
