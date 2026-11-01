package uk.co.traynor.privategallery.core.editor;

/** Independent numeric expectations for the matrix consumed by Android's real colour filter. */
public final class EditorToneAdjustmentChecks {
    private static void close(float expected,float actual) {
        if(Math.abs(expected-actual)>.00001f)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        float[] neutral=EditorToneAdjustments.matrix(0,0);
        for(int i=0;i<20;i++)close(i==0||i==6||i==12||i==18?1:0,neutral[i]);
        close(2,EditorToneAdjustments.matrix(1,0)[0]);
        close(.5f,EditorToneAdjustments.matrix(-1,0)[6]);
        close(4,EditorToneAdjustments.matrix(2,0)[12]);
        float[] warm=EditorToneAdjustments.matrix(0,1),cool=EditorToneAdjustments.matrix(0,-1);
        close(1.2f,warm[0]);close(1,warm[6]);close(.8f,warm[12]);
        close(.8f,cool[0]);close(1,cool[6]);close(1.2f,cool[12]);
        close(1,warm[18]);close(0,warm[19]);
        close(2.4f,EditorToneAdjustments.matrix(1,1)[0]);close(1.6f,EditorToneAdjustments.matrix(1,1)[12]);
        for(float[] invalid:new float[][]{{Float.NaN,0},{0,Float.NaN},{Float.POSITIVE_INFINITY,0},{0,Float.NEGATIVE_INFINITY},{2.01f,0},{-2.01f,0},{0,1.01f},{0,-1.01f}}) {
            try{EditorToneAdjustments.matrix(invalid[0],invalid[1]);throw new AssertionError("Invalid input accepted");}
            catch(IllegalArgumentException expected){}
        }
        System.out.println("Editor exposure/temperature identity, gains, direction, alpha and finite bounds PASS");
    }
}
