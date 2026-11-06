import uk.co.traynor.privategallery.core.ui.NativeThemePolicy;
import uk.co.traynor.privategallery.core.ui.EditingContextPolicy;

public final class NativeThemeEditorChecks {
    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        // Existing explicit native choices survive bridge admission; absent choices retain Home.
        for (String nativeTheme : new String[]{"dark", "light", "system"}) {
            equal(nativeTheme, NativeThemePolicy.initial(nativeTheme, "dark", false));
            equal("light", NativeThemePolicy.initial(nativeTheme, "light", true));
        }
        equal("dark", NativeThemePolicy.initial(null, "dark", false));
        equal("light", NativeThemePolicy.initial("corrupt", "light", false));
        equal("system", NativeThemePolicy.initial(null, "invalid", true));
        equal("light", NativeThemePolicy.next("dark"));
        equal("system", NativeThemePolicy.next("light"));
        equal("dark", NativeThemePolicy.next("system"));
        for (String destination : new String[]{"GALLERY", "VAULT", "FAVOURITE"}) {
            equal(true, EditingContextPolicy.allowed(destination, false, false));
            equal(false, EditingContextPolicy.allowed(destination, true, false));
            equal(false, EditingContextPolicy.allowed(destination, false, true));
        }
        for (String destination : new String[]{"BROWSER", "SETTINGS", "LOCK", "SETUP", "RECOVER", "HOME", "WEBAPP"}) {
            equal(false, EditingContextPolicy.allowed(destination, false, false));
        }
        System.out.println("Native theme/editor policies: PASS");
    }
}
