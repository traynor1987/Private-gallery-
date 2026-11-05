package uk.co.traynor.novaos.preview;
public final class LayoutTransaction {
 public interface Step { boolean run(); }
 public static boolean apply(Step home,Step widgets,Step rollbackHome,Step rollbackWidgets){
  try{if(home.run()&&widgets.run())return true;}catch(RuntimeException ignored){}
  boolean a=false,b=false;try{a=rollbackHome.run();}catch(RuntimeException ignored){}try{b=rollbackWidgets.run();}catch(RuntimeException ignored){}
  if(!a||!b)throw new IllegalStateException("Restore failed and rollback needs recovery. Saved snapshots have been preserved.");
  return false;
 }
}
