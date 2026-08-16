package com.autohr.config.database;

public record ActiveDatabase(
        DatabaseType type,
        String url,
        String username,
        String password,
        boolean fallback
) {
}
