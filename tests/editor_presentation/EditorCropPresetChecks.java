package uk.co.traynor.privategallery.ui;

public final class EditorCropPresetChecks {
    private static void close(float expected, float actual) {
        if (Math.abs(expected - actual) > .00001f) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        float[] square = EditorCropPresets.centered(4000, 3000, 1f);
        close(.125f, square[0]); close(0f, square[1]); close(.875f, square[2]); close(1f, square[3]);
        float[] wide = EditorCropPresets.centered(3000, 4000, 16f / 9f);
        close(0f, wide[0]); close(1f, wide[2]); close((1f - .421875f) / 2f, wide[1]);
        for (int[] size : new int[][] {{4000,3000},{3000,4000},{4000,4000},{1920,1080}}) {
            for (float ratio : new float[] {1f,4f/3f,16f/9f}) {
                float[] crop = EditorCropPresets.centered(size[0],size[1],ratio);
                close(1f,crop[0]+crop[2]); close(1f,crop[1]+crop[3]);
                if (crop[0]<0 || crop[1]<0 || crop[2]>1 || crop[3]>1) throw new AssertionError("Out of bounds");
                if (crop[0]!=0 && crop[1]!=0) throw new AssertionError("Not maximal");
                close(ratio,(crop[2]-crop[0])*size[0]/((crop[3]-crop[1])*size[1]));
            }
            float[] original=EditorCropPresets.centered(size[0],size[1],0f);
            close(0f,original[0]);close(0f,original[1]);close(1f,original[2]);close(1f,original[3]);
        }
        for (int orientation=1;orientation<=8;orientation++) {
            float[] crop=EditorCropPresets.orientedCentered(4000,3000,orientation,1f);
            float[] expected=EditorCropPresets.centered(orientation>=5?3000:4000,orientation>=5?4000:3000,1f);
            for(int i=0;i<4;i++)close(expected[i],crop[i]);
        }
        for (float ratio : new float[] {-1f,Float.NaN,Float.POSITIVE_INFINITY}) {
            try {EditorCropPresets.centered(4000,3000,ratio);throw new AssertionError("Invalid ratio accepted");}
            catch(IllegalArgumentException expected) {}
        }
        for (int[] invalid : new int[][] {{0,1},{1,0},{-1,100}}) {
            try { EditorCropPresets.centered(invalid[0],invalid[1],1); throw new AssertionError("Invalid dimensions accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("Editor centered crop presets: orientation, ratio, maximality and bounds PASS");
    }
}
