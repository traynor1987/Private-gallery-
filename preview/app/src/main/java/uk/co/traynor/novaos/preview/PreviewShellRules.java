package uk.co.traynor.novaos.preview;
public final class PreviewShellRules {
 private PreviewShellRules(){}
 /** Selection and missing website metadata are not lifecycle-stop signals. WebView retains native audio-focus control. */
 public static boolean keepRunning(boolean foreground,boolean selected,boolean playing){return foreground;}
 public static boolean mediaState(String state){return "playing".equals(state)||"paused".equals(state);}
 public static String returnRoute(String route,boolean active){return "browser".equals(route)&&!active?"home":route;}
 public static int mediaPriority(String state){return "playing".equals(state)?1:0;}
 public static String order(boolean reverse){return reverse?"back,home,recents":"recents,home,back";}
}
