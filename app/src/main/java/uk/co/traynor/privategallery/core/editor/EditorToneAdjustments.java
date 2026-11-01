package uk.co.traynor.privategallery.core.editor;

/** Local colour-matrix extension; values never alter source metadata or storage formats. */
public final class EditorToneAdjustments {
    private EditorToneAdjustments() {}
    public static float[] matrix(float exposure, float temperature) {
        if(!Float.isFinite(exposure)||exposure < -2f||exposure > 2f||
            !Float.isFinite(temperature)||temperature < -1f||temperature > 1f)
            throw new IllegalArgumentException("Invalid exposure or temperature");
        float gain=(float)Math.pow(2,exposure);
        // A bounded creative warmth adjustment: positive values lift red and reduce blue.
        float red=gain*(1f+.2f*temperature),blue=gain*(1f-.2f*temperature);
        return new float[]{red,0,0,0,0, 0,gain,0,0,0, 0,0,blue,0,0, 0,0,0,1,0};
    }
}
