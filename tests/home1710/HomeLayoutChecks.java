import java.util.*;
import uk.co.traynor.privategallery.core.home.HomeLayout;

public class HomeLayoutChecks {
  static void check(boolean condition) { if (!condition) throw new AssertionError(); }
  public static void main(String[] args) {
    HomeLayout home = HomeLayout.defaults();
    check(home.visible().size() == 11);
    home = home.hide(HomeLayout.Shortcut.BROWSER).pin(HomeLayout.Shortcut.CAMERA);
    check(!home.visible().contains(HomeLayout.Shortcut.BROWSER));
    check(home.favourites().contains(HomeLayout.Shortcut.CAMERA));
    check(!home.hide(HomeLayout.Shortcut.CAMERA).favourites().contains(HomeLayout.Shortcut.CAMERA));
    home = home.move(HomeLayout.Shortcut.SETTINGS, 0);
    check(home.visible().get(0) == HomeLayout.Shortcut.SETTINGS);
    check(home.show(HomeLayout.Shortcut.BROWSER).visible().contains(HomeLayout.Shortcut.BROWSER));
    check(home.restore().equals(HomeLayout.defaults()));
    check(HomeLayout.defaults().search("gAl").equals(List.of(HomeLayout.Shortcut.GALLERY)));
    check(HomeLayout.defaults().search("nonsense").isEmpty());
    System.out.println("Home layout checks passed");
  }
}
