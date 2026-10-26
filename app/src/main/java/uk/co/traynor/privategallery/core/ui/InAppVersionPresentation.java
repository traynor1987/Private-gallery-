package uk.co.traynor.privategallery.core.ui;

/** In-app copy only. Never used for package metadata, diagnostics or update compatibility. */
public final class InAppVersionPresentation {
    private final boolean hidden;
    private final String name;
    private final int code;
    public InAppVersionPresentation(boolean hidden, String realName, int realCode) {
        this.hidden=hidden;this.name=hidden?"0.0.1":realName;this.code=hidden?4:realCode;
    }
    public String installed(){return name+" · Build "+code;}
    public String header(){return "Private Gallery "+installed();}
    public String about(){return "Private Gallery "+name;}
    public String description(){return hidden?"Basic gallery functionality. More features coming soon.":"Media stays in encrypted private app storage until you restore it.";}
    public String updateStatus(String realStatus){return hidden?"Checks use the installed Android app version.":realStatus;}
}
