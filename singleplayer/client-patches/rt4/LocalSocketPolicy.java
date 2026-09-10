package rt4;

/** Central policy for any retained RT4 request to create a kernel socket. */
public final class LocalSocketPolicy {
    private LocalSocketPolicy() {}

    /**
     * Return a completed in-memory JS5 request when openSocket() was called by
     * the retained cache connector. Other legacy network call sites are left to
     * fail closed once the single-player world listener is disabled.
     */
    public static PrivilegedRequest openJs5IfRequested() {
        if (!Boolean.getBoolean("singleplayer") || !isJs5ConnectCall()) {
            return null;
        }
        PrivilegedRequest request = new PrivilegedRequest();
        request.result = new LocalJs5Socket();
        request.status = 1;
        return request;
    }

    private static boolean isJs5ConnectCall() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("rt4.client".equals(frame.getClassName())
                    && "js5Connect".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }
}
