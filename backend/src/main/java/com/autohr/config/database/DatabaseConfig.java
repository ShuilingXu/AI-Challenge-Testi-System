package com.autohr.config.database;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Configuration
@EnableConfigurationProperties(AppDatabaseProperties.class)
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public ActiveDatabase activeDatabase(AppDatabaseProperties properties) {
        DatabaseType requestedType = DatabaseType.from(properties.getType());
        if (requestedType != DatabaseType.SQLITE && !hasText(properties.getUrl())) {
            throw new IllegalStateException("DB_URL must be configured when DB_TYPE is " + requestedType);
        }
        ActiveDatabase requested = new ActiveDatabase(
                requestedType,
                resolveUrl(requestedType, properties.getUrl(), properties.getSqliteFallbackUrl()),
                properties.getUsername(),
                properties.getPassword(),
                false
        );

        try {
            testConnection(requested);
            log.info("Using {} database: {}", requested.type(), requested.url());
            return requested;
        } catch (SQLException ex) {
            if (requestedType == DatabaseType.SQLITE || !properties.isFallbackEnabled()) {
                throw new IllegalStateException(requestedType + " database connection failed: " + requested.url(), ex);
            }
            ActiveDatabase fallback = new ActiveDatabase(
                    DatabaseType.SQLITE,
                    resolveUrl(DatabaseType.SQLITE, properties.getSqliteFallbackUrl(), properties.getSqliteFallbackUrl()),
                    "",
                    "",
                    true
            );
            try {
                testConnection(fallback);
                log.warn("{} database connection failed, falling back to SQLite: {}", requested.type(), fallback.url(), ex);
                return fallback;
            } catch (SQLException fallbackEx) {
                ex.addSuppressed(fallbackEx);
                throw new IllegalStateException("Primary database failed and SQLite fallback failed", ex);
            }
        }
    }

    @Bean
    public DataSource dataSource(ActiveDatabase activeDatabase) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(activeDatabase.type().driverClassName());
        dataSource.setJdbcUrl(activeDatabase.url());
        if (hasText(activeDatabase.username())) {
            dataSource.setUsername(activeDatabase.username());
        }
        if (hasText(activeDatabase.password())) {
            dataSource.setPassword(activeDatabase.password());
        }
        if (activeDatabase.type() == DatabaseType.SQLITE) {
            // SQLite permits only one writer. Serializing access at the pool prevents
            // simultaneous HR/candidate recording uploads from failing with SQLITE_BUSY.
            dataSource.setMaximumPoolSize(1);
            dataSource.setMinimumIdle(1);
            // Keep the driver properties for busy timeout and compatibility, and use
            // a single initialization statement to enforce foreign keys per connection.
            dataSource.addDataSourceProperty("busy_timeout", "10000");
            dataSource.addDataSourceProperty("foreign_keys", "true");
            // Enforce foreign keys for every pooled connection, including drivers that
            // ignore the equivalent connection property.
            dataSource.setConnectionInitSql("PRAGMA foreign_keys=ON");
        }
        return dataSource;
    }

    private void testConnection(ActiveDatabase database) throws SQLException {
        try {
            Class.forName(database.type().driverClassName());
        } catch (ClassNotFoundException ex) {
            throw new SQLException("Database driver not found: " + database.type().driverClassName(), ex);
        }
        if (hasText(database.username())) {
            try (Connection ignored = DriverManager.getConnection(database.url(), database.username(), database.password())) {
                // Connection probe only.
            }
            return;
        }
        try (Connection ignored = DriverManager.getConnection(database.url())) {
            // Connection probe only.
        }
    }

    private String resolveUrl(DatabaseType type, String configuredUrl, String sqliteFallbackUrl) {
        if (hasText(configuredUrl)) {
            return configuredUrl;
        }
        if (type == DatabaseType.SQLITE && hasText(sqliteFallbackUrl)) {
            return sqliteFallbackUrl;
        }
        return switch (type) {
            case SQLITE -> "jdbc:sqlite:autohr.db";
            case MYSQL -> "jdbc:mysql://localhost:3306/autohr?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=true";
            case PGSQL -> "jdbc:postgresql://localhost:5432/autohr";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
