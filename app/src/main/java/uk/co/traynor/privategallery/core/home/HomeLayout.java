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
        RECENTLY_DELETED("Recently Deleted"), COLLECTIONS("Collections"), SETTINGS("Settings");
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
        return new HomeLayout(List.of(Shortcut.values()), Set.of(), Set.of());
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
