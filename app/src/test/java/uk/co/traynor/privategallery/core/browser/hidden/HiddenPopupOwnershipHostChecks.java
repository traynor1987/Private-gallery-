package uk.co.traynor.privategallery.core.browser.hidden;
import java.util.*;
public final class HiddenPopupOwnershipHostChecks {
 public static void main(String[] args) {
  Object app=new Object(), popup=new Object(), nested=new Object(), other=new Object();
  HiddenPopupOwnership<Object> owners=new HiddenPopupOwnership<>();
  owners.add(app);owners.add(other);owners.addPopup(popup,app);owners.addPopup(nested,popup);
  check(owners.isPopup(popup));check(owners.root(nested)==app);
  check(owners.close(popup,popup,true)==app);check(!owners.contains(nested));check(owners.contains(app));
  owners.addPopup(popup,app);check(owners.close(popup,other,true)==null);check(owners.contains(other));
  owners.addPopup(popup,app);check(owners.close(popup,popup,false)==null);check(owners.contains(popup));
  check(owners.close(new Object(),other,true)==null);
  check(owners.group(app).size()==2);owners.remove(app);check(!owners.contains(popup));
  owners.add(app);owners.addPopup(popup,app);owners.clear();check(owners.close(popup,popup,true)==null);
  HiddenCallbackOwners<Object> callbacks=new HiddenCallbackOwners<>();
  callbacks.add(app);callbacks.add(popup);callbacks.add(other);
  callbacks.removeAll(Arrays.asList(app,popup));
  int[] writes={0}, authorityChecks={0};
  for(String event:Arrays.asList("page", "render", "auth", "download")) {
   if(callbacks.owns(popup,()->{authorityChecks[0]++;return true;}))writes[0]++;
  }
  check(writes[0]==0 && authorityChecks[0]==0);
  check(callbacks.owns(other,()->true));check(!callbacks.owns(other,()->false));
  callbacks.clear();check(!callbacks.owns(other,()->true));
  System.out.println("Hidden popup ownership checks passed");
 }
 private static void check(boolean value){if(!value)throw new AssertionError();}
}
