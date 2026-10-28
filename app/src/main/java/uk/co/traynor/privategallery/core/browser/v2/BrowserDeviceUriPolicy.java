package uk.co.traynor.privategallery.core.browser.v2;

/** Device selection only. Trusted, explicitly confirmed Vault uploads use their separate path. */
public final class BrowserDeviceUriPolicy {
    private BrowserDeviceUriPolicy(){}
    public static String rejection(String scheme,String authority,Integer providerUid,int ownUid,boolean readGrant) {
        if(!"content".equalsIgnoreCase(scheme))return "Only readable Android content documents can be selected from the device.";
        if(authority==null || authority.isEmpty() || authority.indexOf('@')>=0)return "The selected document's provider identity cannot be verified.";
        if(providerUid==null)return "The selected document provider is unavailable.";
        if(providerUid==ownUid)return "Private Gallery's own files cannot be selected through the device picker. Use Choose from Vault instead.";
        if(!readGrant)return "Android did not grant read access to this selected document. Choose it again.";
        return null;
    }
}
