package com.autohr.common.file;

/**
 * A browser-facing, short-lived external download URL. A null URL means the
 * caller must use the authenticated local download endpoint instead.
 */
public record DownloadUrlResponse(String url) {

    public static DownloadUrlResponse localFallback() {
        return new DownloadUrlResponse(null);
    }
}
