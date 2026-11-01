import uk.co.traynor.privategallery.core.home.HomeLayout;
public class HomePersistenceChecks {
 public static void main(String[] args) {
  var a=HomeLayout.defaults().move(HomeLayout.Shortcut.SETTINGS,0).hide(HomeLayout.Shortcut.BROWSER).pin(HomeLayout.Shortcut.CAMERA);
  if(!a.equals(HomeLayout.decode(a.encode())))throw new AssertionError("round trip");
  if(!a.unpin(HomeLayout.Shortcut.CAMERA).favourites().isEmpty())throw new AssertionError("unpin");
  if(a.before(HomeLayout.Shortcut.CAMERA,HomeLayout.Shortcut.GALLERY).visible().indexOf(HomeLayout.Shortcut.CAMERA)>=a.before(HomeLayout.Shortcut.CAMERA,HomeLayout.Shortcut.GALLERY).visible().indexOf(HomeLayout.Shortcut.GALLERY))throw new AssertionError("relative reorder");
  for(String bad:new String[]{"", "v2", "v1|GALLERY|BROWSER|CAMERA", "v1|GALLERY,GALLERY||"}) {
   try{HomeLayout.decode(bad);throw new AssertionError("accepted invalid layout");}catch(IllegalArgumentException expected){}
  }
  System.out.println("Home persistence checks passed");
 }
}
