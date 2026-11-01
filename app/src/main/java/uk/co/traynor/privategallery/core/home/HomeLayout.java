package uk.co.traynor.privategallery.core.home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Non-sensitive launcher arrangement. Vault-specific persistence belongs to the caller. */
public final class HomeLayout {
    public enum Shortcut {
        GALLERY("Gallery"), BROWSER("Browser"), PRIVATE_APPS("Private Apps"),
        EVIDENCE_LOCKER("Evidence Locker"), CAMERA("Camera"),
        RECENTLY_DELETED("Recently Deleted"), COLLECTIONS("Collections"), SETTINGS("Settings"), VAULT("Vault"), FAVOURITES("Favourites"), TOR_BROWSER("Tor Browser");
        public final String label;
        Shortcut(String label) { this.label = label; }
    }
    private final List<Shortcut> order;
    private final Set<Shortcut> hidden;
    private final Set<Shortcut> favourites;

    private HomeLayout(List<Shortcut> order, Set<Shortcut> hidden, Set<Shortcut> favourites) {
        this.order = Collections.unmodifiableList(new ArrayList<>(order));
        EnumSet<Shortcut> h = EnumSet.noneOf(Shortcut.class); h.addAll(hidden);
        EnumSet<Shortcut> f = EnumSet.noneOf(Shortcut.class); f.addAll(favourites); f.removeAll(h);
        this.hidden = Collections.unmodifiableSet(h);
        this.favourites = Collections.unmodifiableSet(f);
    }
    public static HomeLayout defaults() {
        return new HomeLayout(List.of(Shortcut.GALLERY, Shortcut.VAULT, Shortcut.BROWSER,
            Shortcut.PRIVATE_APPS, Shortcut.EVIDENCE_LOCKER, Shortcut.CAMERA, Shortcut.COLLECTIONS,
            Shortcut.FAVOURITES, Shortcut.RECENTLY_DELETED, Shortcut.SETTINGS, Shortcut.TOR_BROWSER), Set.of(), Set.of());
    }
    public List<Shortcut> visible() {
        List<Shortcut> result = new ArrayList<>();
        for (Shortcut item : order) if (!hidden.contains(item)) result.add(item);
        return Collections.unmodifiableList(result);
    }
    public Set<Shortcut> favourites() { return favourites; }
    public HomeLayout hide(Shortcut item) {
        Objects.requireNonNull(item); EnumSet<Shortcut> h = EnumSet.noneOf(Shortcut.class); h.addAll(hidden); h.add(item);
        return new HomeLayout(order, h, favourites);
    }
    public HomeLayout show(Shortcut item) {
        Objects.requireNonNull(item); EnumSet<Shortcut> h = EnumSet.noneOf(Shortcut.class); h.addAll(hidden); h.remove(item);
        return new HomeLayout(order, h, favourites);
    }
    public HomeLayout pin(Shortcut item) {
        Objects.requireNonNull(item); if (hidden.contains(item)) return this;
        EnumSet<Shortcut> f = EnumSet.noneOf(Shortcut.class); f.addAll(favourites); f.add(item);
        return new HomeLayout(order, hidden, f);
    }
    public HomeLayout unpin(Shortcut item) {
        EnumSet<Shortcut> f = EnumSet.noneOf(Shortcut.class); f.addAll(favourites); f.remove(Objects.requireNonNull(item));
        return new HomeLayout(order, hidden, f);
    }
    public String encode() {
        return "v1|" + names(order) + "|" + names(hidden) + "|" + names(favourites);
    }
    private static String names(Iterable<Shortcut> values) {
        List<String> names = new ArrayList<>(); for (Shortcut value : values) names.add(value.name());
        return String.join(",", names);
    }
    private static List<Shortcut> parse(String value) {
        List<Shortcut> result = new ArrayList<>();
        if (!value.isEmpty()) for (String name : value.split(",", -1)) {
            Shortcut item = Shortcut.valueOf(name); if (result.contains(item)) throw new IllegalArgumentException("Duplicate shortcut");
            result.add(item);
        }
        return result;
    }
    public static HomeLayout decode(String value) {
        if (value == null || value.length() > 1024) throw new IllegalArgumentException("Invalid layout");
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4 || !parts[0].equals("v1")) throw new IllegalArgumentException("Invalid layout");
        List<Shortcut> order = parse(parts[1]);
        if (order.size() != Shortcut.values().length) {
            // Accept exactly the complete historical eight- or ten-app schemas.
            Set<Shortcut> ten = EnumSet.allOf(Shortcut.class); ten.remove(Shortcut.TOR_BROWSER);
            Set<Shortcut> eight = EnumSet.copyOf(ten); eight.remove(Shortcut.VAULT); eight.remove(Shortcut.FAVOURITES);
            Set<Shortcut> present = new java.util.HashSet<>(order);
            if (!ten.equals(present) && !eight.equals(present)) throw new IllegalArgumentException("Incomplete layout");
            if (eight.equals(present)) {order.add(Shortcut.VAULT);order.add(Shortcut.FAVOURITES);}
            order.add(Shortcut.TOR_BROWSER);
        }
        return new HomeLayout(order, new java.util.HashSet<>(parse(parts[2])), new java.util.HashSet<>(parse(parts[3])));
    }
    public HomeLayout before(Shortcut item, Shortcut target) {
        Objects.requireNonNull(item); Objects.requireNonNull(target); if (item == target) return this;
        List<Shortcut> next = new ArrayList<>(order); next.remove(item); next.add(next.indexOf(target), item);
        return new HomeLayout(next, hidden, favourites);
    }
    public HomeLayout move(Shortcut item, int position) {
        Objects.requireNonNull(item); List<Shortcut> newOrder = new ArrayList<>(order);
        newOrder.remove(item); newOrder.add(Math.max(0, Math.min(position, newOrder.size())), item);
        return new HomeLayout(newOrder, hidden, favourites);
    }
    public HomeLayout restore() { return defaults(); }
    public List<Shortcut> search(String query) {
        String q = Objects.requireNonNull(query).trim().toLowerCase(Locale.ROOT);
        List<Shortcut> result = new ArrayList<>();
        for (Shortcut item : visible()) if (item.label.toLowerCase(Locale.ROOT).contains(q)) result.add(item);
        return Collections.unmodifiableList(result);
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof HomeLayout)) return false;
        HomeLayout that = (HomeLayout) other;
        return order.equals(that.order) && hidden.equals(that.hidden) && favourites.equals(that.favourites);
    }
    @Override public int hashCode() { return Objects.hash(order, hidden, favourites); }
}
