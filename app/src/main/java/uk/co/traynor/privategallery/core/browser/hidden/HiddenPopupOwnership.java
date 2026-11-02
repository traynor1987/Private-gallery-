package uk.co.traynor.privategallery.core.browser.hidden;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Main-thread presentation ownership only. Never conveys authentication or network authority. */
public final class HiddenPopupOwnership<T> {
    private final IdentityHashMap<T,T> parents=new IdentityHashMap<>();
    public void add(T view) {parents.put(view,null);}
    public void addPopup(T popup,T opener) {
        if(popup==opener || !parents.containsKey(opener) || parents.containsKey(popup))throw new IllegalArgumentException("Invalid popup owner");
        parents.put(popup,opener);
    }
    public boolean contains(T view) {return parents.containsKey(view);}
    public boolean isPopup(T view) {return parents.get(view)!=null;}
    public T root(T view) {
        if(!contains(view))return null;
        T parent;while((parent=parents.get(view))!=null)view=parent;
        return view;
    }
    public List<T> group(T view) {
        List<T> result=new ArrayList<>();
        if(!contains(view))return result;
        for(T candidate:parents.keySet()) {
            T cursor=candidate;
            while(cursor!=null) {if(cursor==view){result.add(candidate);break;}cursor=parents.get(cursor);}
        }
        return result;
    }
    public void remove(T view) {for(T owned:group(view))parents.remove(owned);}
    /** A background popup may close, but must never steal the currently selected app. */
    public T close(T popup,T current,boolean authorized) {
        if(!authorized || !isPopup(popup))return null;
        T opener=parents.get(popup);
        boolean selected=group(popup).contains(current);
        remove(popup);
        return selected && contains(opener)?opener:null;
    }
    public void clear() {parents.clear();}
}
