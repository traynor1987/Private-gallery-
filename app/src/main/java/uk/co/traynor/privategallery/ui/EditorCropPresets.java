package uk.co.traynor.privategallery.ui;

/** Presentation geometry in the visually oriented original image's normalized coordinates. */
public final class EditorCropPresets {
    private EditorCropPresets() {}

    /** EXIF orientations 5–8 exchange the visually oriented source axes. */
    public static float[] orientedCentered(int width, int height, int orientation, float ratio) {
        return orientedCentered(width, height, orientation, 0, ratio);
    }

    /** Preset labels describe the final output; crop coordinates remain in original orientation. */
    public static float[] orientedCentered(int width, int height, int orientation, int quarterTurns, float ratio) {
        if (quarterTurns < 0 || quarterTurns > 3 || !Float.isFinite(ratio) || ratio < 0f)
            throw new IllegalArgumentException("Invalid rotation or aspect ratio");
        float sourceRatio = quarterTurns % 2 == 1 && ratio != 0f ? 1f / ratio : ratio;
        return orientation >= 5 && orientation <= 8 ? centered(height, width, sourceRatio) : centered(width, height, sourceRatio);
    }

    /** Zero selects the original; other ratios choose the largest centered in-bounds rectangle. */
    public static float[] centered(int width, int height, float ratio) {
        if (width <= 0 || height <= 0 || !Float.isFinite(ratio) || ratio < 0f)
            throw new IllegalArgumentException("Invalid source dimensions or aspect ratio");
        if (ratio == 0f) return new float[] {0f, 0f, 1f, 1f};
        float sourceRatio = (float) width / height;
        float cropWidth = sourceRatio > ratio ? ratio / sourceRatio : 1f;
        float cropHeight = sourceRatio > ratio ? 1f : sourceRatio / ratio;
        float left = (1f - cropWidth) / 2f;
        float top = (1f - cropHeight) / 2f;
        return new float[] {left, top, 1f - left, 1f - top};
    }
}
