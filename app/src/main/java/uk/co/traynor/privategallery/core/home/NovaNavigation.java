package uk.co.traynor.privategallery.core.home;

/** Presentation routes only. MainActivity remains the authentication and engine authority. */
public final class NovaNavigation {
 private NovaNavigation() {}
 public static String route(HomeLayout.Shortcut app) {
  return switch(app) {
   case GALLERY -> "GALLERY";
   case FAVOURITES -> "FAVOURITE";
   case BROWSER, TOR_BROWSER, PRIVATE_APPS -> "BROWSER";
   case SETTINGS -> "SETTINGS";
   default -> "VAULT";
  };
 }
 public static boolean available(HomeLayout.Shortcut app,boolean hidden) {return app!=HomeLayout.Shortcut.TOR_BROWSER || (hidden && NovaReleaseFeatures.TOR_ENABLED);}
 public static String browserMode(HomeLayout.Shortcut app) {return app==HomeLayout.Shortcut.TOR_BROWSER?"TOR_ONLY":"VPN_ONLY";}
 public static String ownerLabel(boolean hidden,boolean concealed) {return concealed?null:(hidden?"Hidden Space":"Primary Space");}
 public static String rootDestination(boolean concealed) {return concealed?"GALLERY":"HOME";}
 public static int columns(int contentWidthDp,float fontScale) {
  int minimumCell=fontScale>=1.5f?104:82;
  return Math.max(1,Math.min(8,Math.max(0,contentWidthDp)/minimumCell));
 }
 public static final class Position {
  private Object identity;
  private long epoch;
  private String collectionId;
  public synchronized void record(Object identity,long epoch,String collectionId) {
   if(identity==null){clear();return;}
   this.identity=identity;this.epoch=epoch;this.collectionId=collectionId;
  }
  public synchronized String restore(Object identity,long epoch) {
   return identity!=null && this.identity==identity && this.epoch==epoch ? collectionId : null;
  }
  public synchronized void clear() {identity=null;epoch=0;collectionId=null;}
 }
}
