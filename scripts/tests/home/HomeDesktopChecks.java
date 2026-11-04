import uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut;
import uk.co.traynor.privategallery.core.home.desktop.HomeDesktop;
import java.util.*;

public final class HomeDesktopChecks {
    static int assertions;
    static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    static void reject(Runnable action) { assertions++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("Invalid input accepted"); }
    static byte[] disabledTor(boolean folder) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            var out = new java.io.DataOutputStream(bytes);
            out.writeInt(0x4e483201); out.writeByte(1); out.writeByte(0);
            out.writeByte(0); out.writeByte(0); out.writeByte(4); out.writeByte(64); out.writeBoolean(false);
            out.writeByte(0); out.writeByte(folder ? 0 : 1);
            if (!folder) out.writeUTF("TOR_BROWSER");
            out.writeByte(1); out.writeByte(folder ? 1 : 0);
            if (folder) {
                out.writeBoolean(true); out.writeUTF("network"); out.writeUTF("Network"); out.writeByte(2);
                out.writeUTF("BROWSER"); out.writeUTF("TOR_BROWSER");
            }
            out.flush(); return bytes.toByteArray();
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    public static void main(String[] args) {
        var primary = HomeDesktop.defaults(HomeDesktop.Space.PRIMARY);
        var hidden = HomeDesktop.defaults(HomeDesktop.Space.HIDDEN);
        check(primary.dock().equals(List.of(Shortcut.GALLERY, Shortcut.BROWSER, Shortcut.PRIVATE_APPS, Shortcut.CAMERA)), "default dock");
        check(primary.pages().size() == 1, "default page");
        check(primary.drawer("tor").isEmpty() && hidden.drawer("tor").isEmpty(), "Tor stays disabled");
        var savedTorDock = HomeDesktop.decode(HomeDesktop.Space.HIDDEN, disabledTor(false));
        check(savedTorDock.visibleDock().isEmpty(), "disabled Tor filtered from saved dock");
        var savedTorFolder = HomeDesktop.decode(HomeDesktop.Space.HIDDEN, disabledTor(true));
        check(savedTorFolder.visiblePage(0).get(0).app() == Shortcut.BROWSER, "disabled Tor filtered and saved folder collapsed");
        check(savedTorFolder.drawer("Tor").isEmpty(), "saved Tor cannot enable drawer entry");
        var moved = primary.addPage().place(Shortcut.GALLERY, 1, 0);
        check(primary.dock().contains(Shortcut.GALLERY), "immutable original");
        check(!moved.dock().contains(Shortcut.GALLERY) && moved.pages().get(1).get(0).app() == Shortcut.GALLERY, "move dock to page");
        moved = moved.place(Shortcut.CAMERA, 1, 1).folder("photos", "My photos", List.of(Shortcut.GALLERY, Shortcut.CAMERA), 1, 0);
        check(moved.pages().get(1).get(0).apps().equals(List.of(Shortcut.GALLERY, Shortcut.CAMERA)), "folder order");
        check(moved.pages().get(1).size() == 1 && !moved.dock().contains(Shortcut.CAMERA), "folder removes originals");
        var renamed = moved.renameFolder("photos", "Pictures");
        check(renamed.pages().get(1).get(0).label().equals("Pictures"), "rename");
        check(moved.pages().get(1).get(0).label().equals("My photos"), "rename immutable");
        var docked = renamed.dock(Shortcut.GALLERY, 0);
        check(docked.pages().get(1).get(0).app() == Shortcut.CAMERA, "singleton folder collapses");
        check(docked.dock().get(0) == Shortcut.GALLERY, "dock replacement ordering");
        var concealed = renamed.hide(Shortcut.GALLERY);
        check(concealed.drawer("gallery").isEmpty(), "hidden drawer");
        check(concealed.pages().get(1).get(0).app() == Shortcut.CAMERA, "hidden folder member removed");
        check(concealed.show(Shortcut.GALLERY).drawer("gallery").contains(Shortcut.GALLERY), "show drawer");
        check(concealed.show(Shortcut.GALLERY).pages().equals(concealed.pages()), "show does not restore placement");
        check(primary.drawer("  BROWSER ").equals(List.of(Shortcut.BROWSER)), "normalised search");
        var emptySecond = primary.addPage();
        check(emptySecond.removePage(1).pages().size() == 1, "remove empty page");
        check(emptySecond.removePage(1).pageIndex(1) == 0, "stale last-page selection clamps after removal");
        check(primary.pageIndex(Integer.MAX_VALUE) == 0 && primary.pageIndex(-1) == 0, "page selection bounded");
        reject(() -> primary.removePage(0));
        reject(() -> emptySecond.removePage(0));
        check(moved.movePage(1,0).pages().get(0).get(0).id().equals("photos"), "page order");
        var configured = renamed.appearance(HomeDesktop.Wallpaper.DUSK, 20, 12, 5, 72, true);
        var decoded = HomeDesktop.decode(HomeDesktop.Space.PRIMARY, configured.encode());
        check(Arrays.equals(configured.encode(), decoded.encode()), "canonical roundtrip");
        check(decoded.wallpaper() == HomeDesktop.Wallpaper.DUSK && decoded.dim() == 20 && decoded.blur() == 12, "wallpaper preferences");
        check(hidden.wallpaper() != configured.wallpaper(), "independent wallpaper snapshots");
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.HIDDEN, configured.encode()));
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, new byte[0]));
        byte[] trailing = Arrays.copyOf(configured.encode(), configured.encode().length + 1);
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, trailing));
        byte[] truncated = Arrays.copyOf(configured.encode(), configured.encode().length - 1);
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, truncated));
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, new byte[65537]));
        reject(() -> primary.folder("bad", "One", List.of(Shortcut.GALLERY), 0, 0));
        reject(() -> primary.folder("bad", "Dup", List.of(Shortcut.GALLERY, Shortcut.GALLERY), 0, 0));
        reject(() -> primary.folder("../escape", "Bad", List.of(Shortcut.GALLERY, Shortcut.CAMERA), 0, 0));
        reject(() -> primary.folder("bad", "Bad\nname", List.of(Shortcut.GALLERY, Shortcut.CAMERA), 0, 0));
        reject(() -> primary.place(Shortcut.GALLERY, 3, 0));
        reject(() -> primary.place(Shortcut.GALLERY, 0, -1));
        reject(() -> primary.appearance(HomeDesktop.Wallpaper.AURORA, 81, 0, 4, 64, false));
        reject(() -> primary.appearance(HomeDesktop.Wallpaper.AURORA, 0, 31, 4, 64, false));
        reject(() -> primary.appearance(HomeDesktop.Wallpaper.AURORA, 0, 0, 2, 64, false));
        reject(() -> primary.place(Shortcut.TOR_BROWSER, 0, 0));
        reject(() -> primary.dock(Shortcut.TOR_BROWSER, 0));
        reject(() -> primary.hide(Shortcut.GALLERY).place(Shortcut.GALLERY, 0, 0));
        reject(() -> primary.folder("bad", "Unavailable", List.of(Shortcut.GALLERY, Shortcut.TOR_BROWSER), 0, 0));
        var fullDock = primary.dock(Shortcut.SETTINGS, 4);
        reject(() -> fullDock.dock(Shortcut.EVIDENCE_LOCKER, 5));
        check(fullDock.remove(Shortcut.SETTINGS).dock().size() == 4, "remove dock item");
        reject(() -> renamed.renameFolder("missing", "Missing"));
        try { primary.pages().clear(); throw new AssertionError("mutable pages"); } catch (UnsupportedOperationException expected) { assertions++; }
        try { renamed.pages().get(1).get(0).apps().clear(); throw new AssertionError("mutable folder"); } catch (UnsupportedOperationException expected) { assertions++; }
        var maxPages = primary;
        for (int i=1; i<8; i++) maxPages = maxPages.addPage();
        var bounded = maxPages;
        reject(bounded::addPage);
        // Decoder must reject malformed counts, enum ordinals, magic and duplicates rather than partially restore.
        byte[] encoded = configured.encode();
        byte[] badBoolean = encoded.clone(); badBoolean[10] = 2;
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, badBoolean));
        byte[] badMagic = encoded.clone(); badMagic[0] ^= 1;
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, badMagic));
        byte[] badOwner = encoded.clone(); badOwner[4] = (byte)127;
        reject(() -> HomeDesktop.decode(HomeDesktop.Space.PRIMARY, badOwner));
        Random random = new Random(1711);
        for (int i=0; i<400; i++) {
            byte[] mutated = encoded.clone();
            mutated[random.nextInt(mutated.length)] ^= (byte)(1 + random.nextInt(255));
            try {
                var parsed = HomeDesktop.decode(HomeDesktop.Space.PRIMARY, mutated);
                check(Arrays.equals(parsed.encode(), HomeDesktop.decode(HomeDesktop.Space.PRIMARY, parsed.encode()).encode()), "accepted state remains canonical");
                check(!parsed.drawer("").contains(Shortcut.TOR_BROWSER), "decoded Tor filtering");
            } catch (IllegalArgumentException expected) { assertions++; }
        }
        System.out.println("HomeDesktopChecks: " + assertions + " assertions passed");
    }
}
