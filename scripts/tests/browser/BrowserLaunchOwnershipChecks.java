import java.util.List;
import uk.co.traynor.privategallery.core.browser.v2.BrowserLaunchOwnership;

public final class BrowserLaunchOwnershipChecks {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        BrowserLaunchOwnership owners = new BrowserLaunchOwnership();
        owners.register("browser-first", null);
        owners.selected("browser-first");
        owners.register("browser-second", null);
        owners.selected("browser-second");
        owners.register("saved-app-a", "app-a");
        owners.selected("saved-app-a");
        check("browser-second".equals(owners.ordinarySelection()),
                "Private App -> Home -> Browser must restore Browser's selected tab");
        check(owners.ordinaryTabs().equals(List.of("browser-first", "browser-second")),
                "Browser metadata and picker must exclude app tabs");

        owners.register("saved-app-b", "app-b");
        owners.register("app-a-popup", owners.owner("saved-app-a"));
        check(!owners.canFocus("saved-app-a", "browser-second"),
                "Background app callback must not redirect Browser");
        check(!owners.canFocus("saved-app-a", "saved-app-b"),
                "Background app callback must not redirect another saved app");
        check(owners.canFocus("app-a-popup", "saved-app-a"),
                "An app-owned popup keeps its parent's owner");
        owners.remove("app-a-popup");
        check(!owners.canFocus("app-a-popup", "saved-app-a"),
                "A removed popup callback must be stale");

        owners.remove("browser-second");
        check("browser-first".equals(owners.ordinarySelection()),
                "Removed Browser selection may fall back only to another ordinary tab");
        owners.remove("browser-first");
        check(owners.ordinarySelection() == null,
                "An app URL is never a Browser fallback when no ordinary tab survives");
        check(owners.ordinaryTabs().isEmpty(), "Only ordinary metadata is restorable");
        owners.register("new-empty-browser", null);
        owners.selected("new-empty-browser");
        check("new-empty-browser".equals(owners.ordinarySelection()),
                "Fresh Browser selection must be independent from the saved apps");
        owners.clear();
        check(owners.ordinaryTabs().isEmpty() && owners.ordinarySelection() == null,
                "Lock/reset must discard remembered runtime owner identities");
        check(!owners.canFocus("saved-app-a", "saved-app-b"),
                "Prior runtime callbacks cannot inherit replacement owner authority");
        System.out.println("BrowserLaunchOwnershipChecks passed");
    }
}
