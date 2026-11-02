package uk.co.traynor.privategallery.core.browser.hidden;
import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.function.BooleanSupplier;
/** Identity membership fence shared by UI and off-main provider interception callbacks. */
public final class HiddenCallbackOwners<T> {
 private final IdentityHashMap<T,Boolean> members=new IdentityHashMap<>();
 public synchronized void add(T value){members.put(value,Boolean.TRUE);}
 public synchronized boolean contains(T value){return members.containsKey(value);}
 public synchronized void removeAll(Collection<T> values){for(T value:values)members.remove(value);}
 public synchronized void clear(){members.clear();}
 public boolean owns(T value,BooleanSupplier authority){return contains(value) && authority.getAsBoolean();}
}
