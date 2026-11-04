package uk.co.traynor.privategallery.core.home.desktop;

import uk.co.traynor.privategallery.core.home.HomeLayout.Shortcut;
import uk.co.traynor.privategallery.core.home.NovaNavigation;
import java.io.*;
import java.util.*;

/**
 * Inactive Home 2.0 preparation. Immutable presentation data, never launch authority.
 * Folder names are sensitive. encode() is NOT encryption: persist only through
 * the authenticated Space's encrypted store. No disk, media or session access.
 */
public final class HomeDesktop {
    public enum Space { PRIMARY, HIDDEN }
    public enum Wallpaper { AURORA, MIDNIGHT, DUSK }
    private static final int MAGIC = 0x4e483201; // NH2, schema 1
    private static final int MAX_PAGES = 8, MAX_CELLS = 48, MAX_DOCK = 5, MAX_BYTES = 65536;

    public static final class Item {
        private final String id, label;
        private final List<Shortcut> apps;
        private Item(String id, String label, List<Shortcut> apps) {
            this.id = id; this.label = label; this.apps = List.copyOf(apps);
        }
        private static Item shortcut(Shortcut app) { return new Item("app-" + app.name(), app.label, List.of(app)); }
        public boolean isFolder() { return apps.size() > 1; }
        public String id() { return id; }
        public String label() { return label; }
        public Shortcut app() { if (isFolder()) throw new IllegalStateException("Folder has no single app"); return apps.get(0); }
        public List<Shortcut> apps() { return apps; }
    }

    private final Space space;
    private final List<List<Item>> pages;
    private final List<Shortcut> dock;
    private final Set<Shortcut> hidden;
    private final Wallpaper wallpaper;
    private final int dim, blur, columns, iconSize;
    private final boolean reducedMotion;

    private HomeDesktop(Space space, List<List<Item>> pages, List<Shortcut> dock, Set<Shortcut> hidden,
                        Wallpaper wallpaper, int dim, int blur, int columns, int iconSize, boolean reducedMotion) {
        if (space == null || wallpaper == null || pages == null || dock == null || hidden == null ||
                pages.isEmpty() || pages.size() > MAX_PAGES || dock.size() > MAX_DOCK || dim < 0 || dim > 80 ||
                blur < 0 || blur > 30 || columns < 3 || columns > 8 || iconSize < 48 || iconSize > 88)
            throw new IllegalArgumentException("Invalid desktop");
        this.space = space;
        this.wallpaper = wallpaper; this.dim = dim; this.blur = blur;
        this.columns = columns; this.iconSize = iconSize; this.reducedMotion = reducedMotion;
        this.hidden = Set.copyOf(hidden);
        EnumSet<Shortcut> placed = EnumSet.noneOf(Shortcut.class);
        Set<String> ids = new HashSet<>();
        List<List<Item>> copy = new ArrayList<>();
        for (List<Item> page : pages) {
            if (page == null || page.size() > MAX_CELLS) throw new IllegalArgumentException("Invalid page");
            for (Item item : page) {
                if (item == null || item.apps.isEmpty() || !ids.add(item.id)) throw new IllegalArgumentException("Invalid item");
                if (item.isFolder()) { validateFolderId(item.id); validateLabel(item.label); }
                for (Shortcut app : item.apps) {
                    if (app == null || hidden.contains(app) || !placed.add(app)) throw new IllegalArgumentException("Duplicate or hidden app");
                }
            }
            copy.add(List.copyOf(page));
        }
        for (Shortcut app : dock) {
            if (app == null || hidden.contains(app) || !placed.add(app)) throw new IllegalArgumentException("Invalid dock");
        }
        this.pages = List.copyOf(copy); this.dock = List.copyOf(dock);
    }

    public static HomeDesktop defaults(Space space) {
        return new HomeDesktop(space, List.of(List.of(Item.shortcut(Shortcut.EVIDENCE_LOCKER),
                Item.shortcut(Shortcut.COLLECTIONS), Item.shortcut(Shortcut.FAVOURITES), Item.shortcut(Shortcut.SETTINGS))),
                List.of(Shortcut.GALLERY, Shortcut.BROWSER, Shortcut.PRIVATE_APPS, Shortcut.CAMERA), Set.of(),
                space == Space.HIDDEN ? Wallpaper.MIDNIGHT : Wallpaper.AURORA, 12, 0, 4, 64, false);
    }
    public Space space() { return space; }
    public List<List<Item>> pages() { return pages; }
    /** Pager state may lag immutable page removal by one frame. Never index raw selection. */
    public int pageIndex(int requested) { return Math.max(0, Math.min(requested, pages.size() - 1)); }
    public List<Shortcut> dock() { return dock; }
    public Set<Shortcut> hidden() { return hidden; }
    public Wallpaper wallpaper() { return wallpaper; }
    public int dim() { return dim; }
    public int blur() { return blur; }
    public int columns() { return columns; }
    public int iconSize() { return iconSize; }
    public boolean reducedMotion() { return reducedMotion; }

    /** Runtime availability is applied at render time too; persisted data is not authorisation. */
    public boolean available(Shortcut app) {
        return app != null && !hidden.contains(app) && NovaNavigation.available(app, space == Space.HIDDEN);
    }
    public List<Shortcut> drawer(String query) {
        if (query == null || query.length() > 256) throw new IllegalArgumentException("Invalid search");
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Shortcut> result = new ArrayList<>();
        for (Shortcut app : Shortcut.values()) if (available(app) && app.label.toLowerCase(Locale.ROOT).contains(q)) result.add(app);
        result.sort(Comparator.comparing(app -> app.label.toLowerCase(Locale.ROOT)));
        return List.copyOf(result);
    }
    public List<Item> visiblePage(int page) {
        checkPage(page);
        List<Item> result = new ArrayList<>();
        for (Item item : pages.get(page)) {
            List<Shortcut> apps = item.apps.stream().filter(this::available).toList();
            if (!apps.isEmpty()) result.add(apps.size() == 1 ? Item.shortcut(apps.get(0)) : new Item(item.id, item.label, apps));
        }
        return List.copyOf(result);
    }
    public List<Shortcut> visibleDock() { return dock.stream().filter(this::available).toList(); }

    private HomeDesktop copy(List<List<Item>> pages, List<Shortcut> dock, Set<Shortcut> hidden) {
        return new HomeDesktop(space, pages, dock, hidden, wallpaper, dim, blur, columns, iconSize, reducedMotion);
    }
    private List<List<Item>> mutablePages() {
        List<List<Item>> result = new ArrayList<>();
        for (List<Item> page : pages) result.add(new ArrayList<>(page));
        return result;
    }
    private void checkPage(int page) { if (page < 0 || page >= pages.size()) throw new IllegalArgumentException("Invalid page"); }
    private void checkApp(Shortcut app) { if (!available(app)) throw new IllegalArgumentException("App unavailable"); }
    private static void insertAt(List<?> items, int position) {
        if (position < 0 || position > items.size()) throw new IllegalArgumentException("Invalid position");
    }
    public HomeDesktop addPage() {
        List<List<Item>> next = mutablePages(); next.add(new ArrayList<>());
        return copy(next, dock, hidden);
    }
    public HomeDesktop removePage(int page) {
        checkPage(page);
        if (pages.size() == 1 || !pages.get(page).isEmpty()) throw new IllegalArgumentException("Page not empty");
        List<List<Item>> next = mutablePages(); next.remove(page); return copy(next, dock, hidden);
    }
    public HomeDesktop movePage(int from, int to) {
        checkPage(from); checkPage(to);
        List<List<Item>> next = mutablePages(); next.add(to, next.remove(from)); return copy(next, dock, hidden);
    }
    public HomeDesktop remove(Shortcut app) {
        if (app == null) throw new IllegalArgumentException("Missing app");
        List<List<Item>> next = new ArrayList<>();
        for (List<Item> page : pages) {
            List<Item> items = new ArrayList<>();
            for (Item item : page) {
                if (!item.apps.contains(app)) { items.add(item); continue; }
                List<Shortcut> remaining = new ArrayList<>(item.apps); remaining.remove(app);
                if (!remaining.isEmpty()) items.add(remaining.size() == 1 ? Item.shortcut(remaining.get(0)) : new Item(item.id, item.label, remaining));
            }
            next.add(items);
        }
        List<Shortcut> nextDock = new ArrayList<>(dock); nextDock.remove(app);
        return copy(next, nextDock, hidden);
    }
    public HomeDesktop place(Shortcut app, int page, int position) {
        checkApp(app); checkPage(page);
        HomeDesktop removed = remove(app);
        List<List<Item>> next = removed.mutablePages(); insertAt(next.get(page), position);
        next.get(page).add(position, Item.shortcut(app)); return copy(next, removed.dock, hidden);
    }
    public HomeDesktop dock(Shortcut app, int position) {
        checkApp(app);
        HomeDesktop removed = remove(app); List<Shortcut> next = new ArrayList<>(removed.dock);
        insertAt(next, position); next.add(position, app); return copy(removed.pages, next, hidden);
    }
    public HomeDesktop folder(String id, String label, List<Shortcut> apps, int page, int position) {
        validateFolderId(id); validateLabel(label); checkPage(page);
        if (apps == null || apps.size() < 2 || apps.size() > Shortcut.values().length || new HashSet<>(apps).size() != apps.size())
            throw new IllegalArgumentException("Invalid folder members");
        // Existing folders must be changed explicitly, never overwritten by a reused ID.
        for (List<Item> p : pages) for (Item item : p) if (item.id.equals(id)) throw new IllegalArgumentException("Folder exists");
        HomeDesktop removed = this;
        for (Shortcut app : apps) { checkApp(app); removed = removed.remove(app); }
        List<List<Item>> next = removed.mutablePages(); insertAt(next.get(page), position);
        next.get(page).add(position, new Item(id, label.trim(), apps)); return copy(next, removed.dock, hidden);
    }
    public HomeDesktop renameFolder(String id, String label) {
        validateFolderId(id); validateLabel(label);
        List<List<Item>> next = mutablePages(); boolean found = false;
        for (List<Item> page : next) for (int i=0; i<page.size(); i++) {
            Item item = page.get(i);
            if (item.isFolder() && item.id.equals(id)) { page.set(i, new Item(id, label.trim(), item.apps)); found = true; }
        }
        if (!found) throw new IllegalArgumentException("Folder absent");
        return copy(next, dock, hidden);
    }
    public HomeDesktop hide(Shortcut app) {
        if (app == null) throw new IllegalArgumentException("Missing app");
        HomeDesktop removed = remove(app); Set<Shortcut> next = new HashSet<>(hidden); next.add(app);
        return copy(removed.pages, removed.dock, next);
    }
    public HomeDesktop show(Shortcut app) {
        if (app == null) throw new IllegalArgumentException("Missing app");
        Set<Shortcut> next = new HashSet<>(hidden); next.remove(app); return copy(pages, dock, next);
    }
    public HomeDesktop appearance(Wallpaper wallpaper, int dim, int blur, int columns, int iconSize, boolean reducedMotion) {
        return new HomeDesktop(space, pages, dock, hidden, wallpaper, dim, blur, columns, iconSize, reducedMotion);
    }
    private static void validateFolderId(String id) {
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]{0,39}")) throw new IllegalArgumentException("Invalid folder ID");
    }
    private static void validateLabel(String label) {
        if (label == null || label.isBlank() || label.codePointCount(0,label.length()) > 32 || label.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid folder label");
    }

    /** Bounded, versioned plaintext transport encoding. Caller MUST encrypt it before persistence. */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC); out.writeByte(space.ordinal()); out.writeByte(wallpaper.ordinal());
                out.writeByte(dim); out.writeByte(blur); out.writeByte(columns); out.writeByte(iconSize); out.writeBoolean(reducedMotion);
                out.writeByte(hidden.size());
                for (Shortcut app : Shortcut.values()) if (hidden.contains(app)) out.writeUTF(app.name());
                out.writeByte(dock.size()); for (Shortcut app : dock) out.writeUTF(app.name());
                out.writeByte(pages.size());
                for (List<Item> page : pages) {
                    out.writeByte(page.size());
                    for (Item item : page) {
                        out.writeBoolean(item.isFolder());
                        if (item.isFolder()) { out.writeUTF(item.id); out.writeUTF(item.label); }
                        out.writeByte(item.apps.size()); for (Shortcut app : item.apps) out.writeUTF(app.name());
                    }
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Desktop too large");
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public static HomeDesktop decode(Space expected, byte[] bytes) {
        if (expected == null || bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Invalid encoding");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readUnsignedByte() != expected.ordinal()) throw new IllegalArgumentException("Wrong desktop owner/schema");
            int palette = in.readUnsignedByte();
            if (palette >= Wallpaper.values().length) throw new IllegalArgumentException("Invalid wallpaper");
            int dim = in.readUnsignedByte(), blur = in.readUnsignedByte(), columns = in.readUnsignedByte(), iconSize = in.readUnsignedByte();
            boolean reduced = readBoolean(in);
            int hiddenCount = count(in, Shortcut.values().length); Set<Shortcut> hidden = EnumSet.noneOf(Shortcut.class);
            for (int i=0; i<hiddenCount; i++) if (!hidden.add(Shortcut.valueOf(in.readUTF()))) throw new IllegalArgumentException("Duplicate hidden app");
            int dockCount = count(in, MAX_DOCK); List<Shortcut> dock = new ArrayList<>();
            for (int i=0; i<dockCount; i++) dock.add(Shortcut.valueOf(in.readUTF()));
            int pageCount = count(in, MAX_PAGES); List<List<Item>> pages = new ArrayList<>();
            for (int p=0; p<pageCount; p++) {
                int itemCount = count(in, MAX_CELLS); List<Item> page = new ArrayList<>();
                for (int i=0; i<itemCount; i++) {
                    boolean folder = readBoolean(in); String id = folder ? in.readUTF() : null, label = folder ? in.readUTF() : null;
                    int appCount = count(in, Shortcut.values().length);
                    if (appCount < (folder ? 2 : 1) || (!folder && appCount != 1)) throw new IllegalArgumentException("Invalid item members");
                    List<Shortcut> apps = new ArrayList<>();
                    for (int a=0; a<appCount; a++) apps.add(Shortcut.valueOf(in.readUTF()));
                    page.add(folder ? new Item(id,label,apps) : Item.shortcut(apps.get(0)));
                }
                pages.add(page);
            }
            if (in.available() != 0) throw new IllegalArgumentException("Trailing data");
            return new HomeDesktop(expected,pages,dock,hidden,Wallpaper.values()[palette],dim,blur,columns,iconSize,reduced);
        } catch (IOException | IndexOutOfBoundsException error) { throw new IllegalArgumentException("Malformed desktop", error); }
    }
    private static boolean readBoolean(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte(); if (value > 1) throw new IllegalArgumentException("Invalid boolean"); return value == 1;
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readUnsignedByte(); if (count > maximum) throw new IllegalArgumentException("Invalid count"); return count;
    }
}
