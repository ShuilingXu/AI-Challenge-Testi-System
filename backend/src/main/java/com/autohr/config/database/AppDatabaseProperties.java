package com.autohr.config.database;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.database")
public class AppDatabaseProperties {

    private String type = "sqlite";
    private String url;
    private String username;
    private String password;
    private String sqliteFallbackUrl = "jdbc:sqlite:autohr.db";
    private boolean fallbackEnabled = true;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSqliteFallbackUrl() {
        return sqliteFallbackUrl;
    }

    public void setSqliteFallbackUrl(String sqliteFallbackUrl) {
        this.sqliteFallbackUrl = sqliteFallbackUrl;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }
}
