package com.autohr.config.database;

import com.baomidou.mybatisplus.annotation.DbType;

import java.util.Locale;

public enum DatabaseType {
    SQLITE("org.sqlite.JDBC", DbType.SQLITE),
    MYSQL("com.mysql.cj.jdbc.Driver", DbType.MYSQL),
    PGSQL("org.postgresql.Driver", DbType.POSTGRE_SQL);

    private final String driverClassName;
    private final DbType mybatisDbType;

    DatabaseType(String driverClassName, DbType mybatisDbType) {
        this.driverClassName = driverClassName;
        this.mybatisDbType = mybatisDbType;
    }

    public String driverClassName() {
        return driverClassName;
    }

    public DbType mybatisDbType() {
        return mybatisDbType;
    }

    public static DatabaseType from(String value) {
        if (value == null || value.isBlank()) {
            return SQLITE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mysql" -> MYSQL;
            case "pgsql", "postgres", "postgresql" -> PGSQL;
            case "sqlite" -> SQLITE;
            default -> throw new IllegalArgumentException("Unsupported DB_TYPE: " + value + ". Use sqlite, mysql, or pgsql.");
        };
    }
}
