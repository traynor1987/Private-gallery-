package uk.co.traynor.privategallery.core.privateapps;

import java.util.function.UnaryOperator;

/** A caller must retain this owner-bound handle; never substitute the currently selected vault. */
public interface PrivateAppStore {
 enum Owner { PRIMARY, HIDDEN_VPN, HIDDEN_TOR }
 Owner owner();
 PrivateAppCatalog read();
 PrivateAppCatalog update(UnaryOperator<PrivateAppCatalog> change);
}
