package uk.co.traynor.privategallery.core.camera;

public final class CameraFocusPolicyChecks {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("focus check " + checks); }
    public static void main(String[] args) {
        check(CameraFocusPolicy.validPoint(0, 0, 400, 300));
        check(CameraFocusPolicy.validPoint(399.9f, 299.9f, 400, 300));
        check(!CameraFocusPolicy.validPoint(400, 100, 400, 300));
        check(!CameraFocusPolicy.validPoint(-1, 100, 400, 300));
        check(!CameraFocusPolicy.validPoint(Float.NaN, 10, 400, 300));
        check(!CameraFocusPolicy.validPoint(10, Float.POSITIVE_INFINITY, 400, 300));
        check(!CameraFocusPolicy.validPoint(0, 0, 0, 300));
        check(CameraFocusPolicy.current(3,3,2,2,true,true,true,true,false,true));
        check(!CameraFocusPolicy.current(2,3,2,2,true,true,true,true,false,true));
        check(!CameraFocusPolicy.current(3,3,1,2,true,true,true,true,false,true));
        check(!CameraFocusPolicy.current(3,3,2,2,false,true,true,true,false,true));
        check(!CameraFocusPolicy.current(3,3,2,2,true,false,true,true,false,true));
        check(!CameraFocusPolicy.current(3,3,2,2,true,true,false,true,false,true));
        check(!CameraFocusPolicy.current(3,3,2,2,true,true,true,false,false,true));
        check(!CameraFocusPolicy.current(3,3,2,2,true,true,true,true,true,true));
        check(!CameraFocusPolicy.current(3,3,2,2,true,true,true,true,false,false));
        System.out.println(checks + " camera focus policy checks passed");
    }
}
