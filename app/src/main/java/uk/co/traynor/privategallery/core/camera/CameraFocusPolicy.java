package uk.co.traynor.privategallery.core.camera;

/** Focus requests are preview-local and have no authority after a rebind or owner loss. */
public final class CameraFocusPolicy {
    private CameraFocusPolicy() {}
    public static boolean validPoint(float x, float y, int width, int height) {
        return Float.isFinite(x) && Float.isFinite(y) && width > 0 && height > 0
                && x >= 0 && y >= 0 && x < width && y < height;
    }
    public static boolean current(long request, long latest, int binding, int currentBinding,
            boolean sameCamera, boolean bound, boolean entered, boolean allowed,
            boolean revoked, boolean permission) {
        return request == latest && binding == currentBinding && sameCamera && bound
                && entered && allowed && !revoked && permission;
    }
}
