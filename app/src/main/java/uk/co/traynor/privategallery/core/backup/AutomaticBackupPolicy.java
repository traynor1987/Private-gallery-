package uk.co.traynor.privategallery.core.backup;
/** No authority retention: a due check may execute only within a current foreground Primary session. */
public final class AutomaticBackupPolicy {
    private AutomaticBackupPolicy() {}
    public static boolean eligible(boolean primary,boolean foreground,boolean current,long due,long now,long lastAttempt,
                                   boolean chargingRequired,boolean charging,boolean wifiRequired,boolean wifi) {
        return primary&&foreground&&current&&due>0&&due<=now&&lastAttempt>=0&&
                (lastAttempt==0||(now>=lastAttempt&&now-lastAttempt>=900_000L))&&
                (!chargingRequired||charging)&&(!wifiRequired||wifi);
    }
}
