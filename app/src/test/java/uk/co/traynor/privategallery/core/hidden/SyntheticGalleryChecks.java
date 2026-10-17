package uk.co.traynor.privategallery.core.hidden;
import java.util.Arrays;
public final class SyntheticGalleryChecks {
    public static void main(String[] args) {
        SyntheticHiddenGallery disabled = new SyntheticHiddenGallery(false);
        try { disabled.importFixture(false, new byte[]{1}); throw new AssertionError(); } catch (IllegalStateException expected) { }
        SyntheticHiddenGallery primary = new SyntheticHiddenGallery(true);
        SyntheticHiddenGallery hidden = new SyntheticHiddenGallery(true);
        byte[] photo = new byte[]{1,2,3};
        String image = hidden.importFixture(false, photo);
        String video = hidden.importFixture(true, new byte[]{4,5,6});
        if (!primary.items().isEmpty() || hidden.items().size()!=2) throw new AssertionError("Mixed contexts");
        hidden.toggleFavourite(image);
        hidden.addCollection("Demo"); hidden.assignCollection(image,"Demo");
        if (!hidden.items().get(0).favourite || !hidden.items().get(0).collection.equals("Demo")) throw new AssertionError();
        if (!hidden.items().get(1).video || !Arrays.equals(hidden.read(video),new byte[]{4,5,6})) throw new AssertionError();
        try { hidden.addCollection(" All "); throw new AssertionError("Reserved filter name accepted"); } catch (IllegalArgumentException expected) { }
        hidden.close();
        if (!Arrays.equals(photo,new byte[3]) || !hidden.items().isEmpty() || !primary.items().isEmpty()) throw new AssertionError("Cleanup");
        System.out.println("Synthetic gallery isolation/import/favourites/collections/reset checks passed");
    }
}
