package uk.co.traynor.privategallery.core.hidden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** TEST ONLY: volatile synthetic fixtures. No files, content resolver, Primary repository or export API. */
public final class SyntheticHiddenGallery implements AutoCloseable {
    private final boolean enabled;
    private final List<Item> items = new ArrayList<>();
    private final LinkedHashSet<String> collections = new LinkedHashSet<>();
    public static final class Item {
        public final String id;
        public final boolean video;
        public final boolean favourite;
        public final String collection;
        private final byte[] bytes;
        private Item(String id, boolean video, byte[] bytes, boolean favourite, String collection) {
            this.id=id; this.video=video; this.bytes=bytes; this.favourite=favourite; this.collection=collection;
        }
    }
    public SyntheticHiddenGallery(boolean enabled) { this.enabled=enabled; collections.add("Test collection"); }
    public synchronized String importFixture(boolean video, byte[] ownedBytes) {
        if (!enabled || ownedBytes.length == 0 || ownedBytes.length > 2*1024*1024 || items.size() >= 20) {
            Arrays.fill(ownedBytes,(byte)0);
            throw new IllegalStateException("Synthetic prototype imports unavailable or limit reached");
        }
        String id=UUID.randomUUID().toString();
        items.add(new Item(id,video,ownedBytes,false,"Test collection"));
        return id;
    }
    public synchronized List<Item> items() { return Collections.unmodifiableList(new ArrayList<>(items)); }
    public synchronized List<String> collections() { return new ArrayList<>(collections); }
    public synchronized byte[] read(String id) { return find(id).bytes.clone(); }
    public synchronized void toggleFavourite(String id) {
        Item item=find(id); items.set(items.indexOf(item),new Item(id,item.video,item.bytes,!item.favourite,item.collection));
    }
    public synchronized void addCollection(String name) {
        if (name == null) throw new IllegalArgumentException();
        String normalized=name.trim();
        if (collections.size() >= 10 || normalized.equals("All") || normalized.equals("Favourites") || normalized.isEmpty() || normalized.length()>40 || normalized.chars().anyMatch(c->c<32)) throw new IllegalArgumentException();
        collections.add(normalized);
    }
    public synchronized void assignCollection(String id,String collection) {
        if (!collections.contains(collection)) throw new IllegalArgumentException();
        Item item=find(id); items.set(items.indexOf(item),new Item(id,item.video,item.bytes,item.favourite,collection));
    }
    private Item find(String id) {
        for(Item item:items) if(item.id.equals(id)) return item;
        throw new IllegalStateException("Synthetic item unavailable");
    }
    @Override public synchronized void close() {
        for(Item item:items) Arrays.fill(item.bytes,(byte)0);
        items.clear(); collections.clear(); collections.add("Test collection");
    }
}
