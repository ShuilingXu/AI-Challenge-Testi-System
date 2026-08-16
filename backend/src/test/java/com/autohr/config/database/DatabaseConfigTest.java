package com.autohr.config.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseConfigTest {

    @TempDir
    Path tempDirectory;

    @Test
    void enablesForeignKeysOnSqlitePoolConnections() throws Exception {
        String url = "jdbc:sqlite:" + tempDirectory.resolve("foreign-keys.db").toAbsolutePath();
        DatabaseConfig config = new DatabaseConfig();
        DataSource dataSource = config.dataSource(new ActiveDatabase(DatabaseType.SQLITE, url, "", "", false));

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            assertEquals(1, result.getInt(1));
        } finally {
            if (dataSource instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }
}
