package com.autohr.config.database;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

@Component
public class SqliteGeneratedKeyConfigurer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SqliteGeneratedKeyConfigurer.class);
    private static final Field KEY_GENERATOR_FIELD = keyGeneratorField();

    private final ActiveDatabase activeDatabase;
    private final SqlSessionFactory sqlSessionFactory;

    public SqliteGeneratedKeyConfigurer(ActiveDatabase activeDatabase, SqlSessionFactory sqlSessionFactory) {
        this.activeDatabase = activeDatabase;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (activeDatabase.type() != DatabaseType.SQLITE) {
            return;
        }
        Configuration configuration = sqlSessionFactory.getConfiguration();
        int configuredStatements = 0;
        for (String statementName : configuration.getMappedStatementNames()) {
            if (!statementName.contains(".")) {
                continue;
            }
            MappedStatement statement = configuration.getMappedStatement(statementName, false);
            if (statement.getSqlCommandType() == SqlCommandType.INSERT
                    && statement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
                ReflectionUtils.setField(KEY_GENERATOR_FIELD, statement, SqliteGeneratedKeyGenerator.INSTANCE);
                configuredStatements++;
            }
        }
        log.info("Configured SQLite generated-key retrieval for {} insert statements", configuredStatements);
    }

    private static Field keyGeneratorField() {
        Field field = ReflectionUtils.findField(MappedStatement.class, "keyGenerator");
        if (field == null) {
            throw new IllegalStateException("MyBatis MappedStatement.keyGenerator field is unavailable");
        }
        ReflectionUtils.makeAccessible(field);
        return field;
    }

    private enum SqliteGeneratedKeyGenerator implements KeyGenerator {
        INSTANCE;

        @Override
        public void processBefore(Executor executor, MappedStatement statement, Statement jdbcStatement, Object parameter) {
        }

        @Override
        public void processAfter(Executor executor, MappedStatement statement, Statement jdbcStatement, Object parameter) {
            String[] keyProperties = statement.getKeyProperties();
            if (keyProperties == null || keyProperties.length != 1) {
                throw new IllegalStateException("SQLite generated keys require exactly one key property for " + statement.getId());
            }
            long generatedId = readLastInsertedId(jdbcStatement);
            assignGeneratedId(statement.getConfiguration(), parameter, keyProperties[0], generatedId, statement.getId());
        }

        private long readLastInsertedId(Statement jdbcStatement) {
            try (Statement keyStatement = jdbcStatement.getConnection().createStatement();
                 ResultSet resultSet = keyStatement.executeQuery("SELECT last_insert_rowid()")) {
                if (!resultSet.next()) {
                    throw new SQLException("SQLite did not return last_insert_rowid()");
                }
                return resultSet.getLong(1);
            } catch (SQLException ex) {
                throw new IllegalStateException("Failed to retrieve the SQLite generated key", ex);
            }
        }

        private void assignGeneratedId(Configuration configuration, Object parameter, String keyProperty,
                                       long generatedId, String statementId) {
            MetaObject parameterMeta = configuration.newMetaObject(parameter);
            if (parameterMeta.hasSetter(keyProperty)) {
                parameterMeta.setValue(keyProperty, generatedId);
                return;
            }
            if (parameter instanceof Map<?, ?> parameterMap) {
                for (Object value : parameterMap.values()) {
                    if (value == null) {
                        continue;
                    }
                    MetaObject valueMeta = configuration.newMetaObject(value);
                    if (valueMeta.hasSetter(keyProperty)) {
                        valueMeta.setValue(keyProperty, generatedId);
                        return;
                    }
                }
            }
            throw new IllegalStateException("Cannot assign SQLite generated key property " + keyProperty
                    + " for " + statementId);
        }
    }
}
