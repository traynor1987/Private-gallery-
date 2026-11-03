package uk.co.traynor.privategallery.core.browser.v2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime-only launch ownership. Identifiers are opaque; URLs never select an application. */
public final class BrowserLaunchOwnership {
    private final Map<String, String> owners = new LinkedHashMap<>();
    private String ordinarySelected;

    public void register(String tabId, String appId) {
        Objects.requireNonNull(tabId);
        if (owners.containsKey(tabId)) throw new IllegalArgumentException("Tab already owned");
        owners.put(tabId, appId);
    }

    public String owner(String tabId) { return owners.get(tabId); }

    public void selected(String tabId) {
        if (!owners.containsKey(tabId)) throw new IllegalArgumentException("Unknown tab");
        if (owners.get(tabId) == null) ordinarySelected = tabId;
    }

    public List<String> ordinaryTabs() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : owners.entrySet()) {
            if (entry.getValue() == null) result.add(entry.getKey());
        }
        return result;
    }

    public String ordinarySelection() {
        if (ordinarySelected != null && owners.containsKey(ordinarySelected) && owners.get(ordinarySelected) == null) {
            return ordinarySelected;
        }
        List<String> ordinary = ordinaryTabs();
        return ordinary.isEmpty() ? null : ordinary.get(0);
    }

    public boolean canFocus(String tabId, String activeId) {
        return owners.containsKey(tabId) && owners.containsKey(activeId) &&
                Objects.equals(owners.get(tabId), owners.get(activeId));
    }

    public void remove(String tabId) {
        owners.remove(tabId);
        if (Objects.equals(ordinarySelected, tabId)) ordinarySelected = null;
    }

    public void clear() { owners.clear(); ordinarySelected = null; }
}
