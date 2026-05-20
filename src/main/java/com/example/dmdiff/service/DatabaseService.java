package com.example.dmdiff.service;

import com.example.dmdiff.dto.ConnectionConfig;
import com.example.dmdiff.dto.ConnectionResult;
import com.example.dmdiff.metadata.ColumnInfo;
import com.example.dmdiff.metadata.IndexInfo;
import com.example.dmdiff.metadata.TableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    public ConnectionResult testConnection(ConnectionConfig config) {
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword())) {
            
            String version = getDatabaseVersion(conn);
            boolean caseSensitive = isCaseSensitive(conn);
            
            return ConnectionResult.success(version, caseSensitive);
        } catch (SQLException e) {
            logger.error("连接失败: {}", e.getMessage());
            return ConnectionResult.failure(e.getMessage());
        }
    }

    private String getDatabaseVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM V$VERSION")) {
            if (rs.next()) {
                return rs.getString(1);
            }
        }
        return "未知版本";
    }

    private boolean isCaseSensitive(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT CASE_SENSITIVE FROM V$DATABASE")) {
            if (rs.next()) {
                return rs.getInt(1) == 1;
            }
        }
        return false;
    }

    public List<TableInfo> getAllTables(ConnectionConfig config) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        String schema = config.getDatabase();
        logger.info("getAllTables: jdbcUrl={}, schema={}", config.getJdbcUrl(), schema);
        
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    
                    TableInfo table = new TableInfo(schema, tableName, "TABLE");
                    table.setColumns(getColumns(conn, schema, tableName));
                    table.setIndexes(getIndexes(conn, schema, tableName));
                    
                    tables.add(table);
                }
            }
        }
        
        logger.info("getAllTables: found {} tables for schema={}", tables.size(), schema);
        return tables;
    }

    private List<ColumnInfo> getColumns(Connection conn, String schema, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getColumns(null, schema, tableName, "%")) {
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("COLUMN_NAME"));
                column.setDataType(rs.getString("TYPE_NAME"));
                column.setDataLength(rs.getInt("COLUMN_SIZE"));
                column.setPrecision(rs.getInt("COLUMN_SIZE"));
                column.setScale(rs.getInt("DECIMAL_DIGITS"));
                column.setNullable("YES".equalsIgnoreCase(rs.getString("IS_NULLABLE")));
                column.setDefaultValue(rs.getString("COLUMN_DEF"));
                column.setRemarks(rs.getString("REMARKS"));
                column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                
                columns.add(column);
            }
        }
        
        return columns;
    }

    private List<IndexInfo> getIndexes(Connection conn, String schema, String tableName) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(null, schema, tableName, false, true)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue;
                
                boolean unique = !rs.getBoolean("NON_UNIQUE");
                String columnName = rs.getString("COLUMN_NAME");
                
                IndexInfo existing = null;
                for (IndexInfo idx : indexes) {
                    if (idx.getIndexName().equals(indexName)) {
                        existing = idx;
                        break;
                    }
                }
                
                if (existing == null) {
                    existing = new IndexInfo();
                    existing.setIndexName(indexName);
                    existing.setUnique(unique);
                    existing.setClustered(false);
                    indexes.add(existing);
                }
                
                if (columnName != null) {
                    existing.addColumnName(columnName);
                }
            }
        }
        
        return indexes;
    }

    public int getTableCount(ConnectionConfig config) throws SQLException {
        String schema = config.getDatabase();
        logger.info("getTableCount: schema={}, jdbcUrl={}", schema, config.getJdbcUrl());
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int getViewCount(ConnectionConfig config) throws SQLException {
        String schema = config.getDatabase();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM ALL_VIEWS WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int getIndexCount(ConnectionConfig config) throws SQLException {
        String schema = config.getDatabase();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM ALL_INDEXES WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public int getProcedureCount(ConnectionConfig config) throws SQLException {
        String schema = config.getDatabase();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM ALL_PROCEDURES WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<String> getDatabases(ConnectionConfig config) throws SQLException {
        List<String> databases = new ArrayList<>();
        String baseUrl = String.format("jdbc:dm://%s:%d", config.getHost(), config.getPort());
        
        try (Connection conn = DriverManager.getConnection(baseUrl, config.getUsername(), config.getPassword())) {
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getSchemas()) {
                while (rs.next()) {
                    String schemaName = rs.getString("TABLE_SCHEM");
                    if (schemaName != null && !schemaName.isEmpty()) {
                        databases.add(schemaName);
                    }
                }
            }
        }
        return databases;
    }

    public void executeSql(ConnectionConfig config, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("SQL 执行成功: {}", sql);
        } catch (SQLException e) {
            logger.error("SQL 执行失败: {}", e.getMessage());
            throw e;
        }
    }

    public void executeSqlOnTarget(ConnectionConfig config, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("目标库 SQL 执行成功: {}", sql);
        } catch (SQLException e) {
            logger.error("目标库 SQL 执行失败: {}", e.getMessage());
            throw e;
        }
    }

    public String getTableDdl(ConnectionConfig config, String schema, String tableName) {
        String ddl = null;
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            String query = "SELECT DBMS_METADATA.GET_DDL('TABLE', '" + tableName + "', '" + schema + "') FROM DUAL";
            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    Clob clob = rs.getClob(1);
                    if (clob != null) {
                        ddl = clob.getSubString(1, (int) clob.length());
                    }
                }
            }
            if (ddl == null || ddl.trim().isEmpty()) {
                String fallbackQuery = "SELECT DBMS_METADATA.GET_DDL('TABLE', '" + tableName + "') FROM DUAL";
                try (ResultSet rs = stmt.executeQuery(fallbackQuery)) {
                    if (rs.next()) {
                        Clob clob = rs.getClob(1);
                        if (clob != null) {
                            ddl = clob.getSubString(1, (int) clob.length());
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("获取表DDL失败: {} - {}", tableName, e.getMessage());
        }
        return ddl;
    }

    public List<String> getAllTableNames(ConnectionConfig config) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        String schema = config.getDatabase();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ? ORDER BY TABLE_NAME")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tableNames.add(rs.getString("TABLE_NAME"));
                }
            }
        }
        return tableNames;
    }

    public long getTableRowCount(ConnectionConfig config, String schema, String tableName) throws SQLException {
        String qualifiedName = schema + "." + tableName;
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + qualifiedName)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 0;
    }

    public List<Map<String, Object>> getTableData(ConnectionConfig config, String schema,
                                                    String tableName, int limit, int offset) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        String qualifiedName = schema + "." + tableName;
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.setFetchSize(100);
            String query = "SELECT * FROM " + qualifiedName;
            if (limit > 0) {
                query += " LIMIT " + limit + " OFFSET " + offset;
            }
            try (ResultSet rs = stmt.executeQuery(query)) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(meta.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    public List<String> getTableColumns(ConnectionConfig config, String schema, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            String query = "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE OWNER = '" + schema
                    + "' AND TABLE_NAME = '" + tableName + "' ORDER BY COLUMN_ID";
            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }

    public int batchInsertData(ConnectionConfig config, String schema, String tableName,
                                List<String> columns, List<Map<String, Object>> rows) throws SQLException {
        String qualifiedName = schema + "." + tableName;
        String colList = columns.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String insertSql = "INSERT INTO " + qualifiedName + " (" + colList + ") VALUES (" + placeholders + ")";

        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            conn.setAutoCommit(false);
            int count = 0;
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    pstmt.setObject(i + 1, row.get(columns.get(i)));
                }
                pstmt.addBatch();
                count++;
                if (count % 500 == 0) {
                    pstmt.executeBatch();
                    conn.commit();
                }
            }
            pstmt.executeBatch();
            conn.commit();
            return count;
        }
    }

    public void disableConstraints(ConnectionConfig config, String schema, String tableName) throws SQLException {
        String sql = "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS WHERE OWNER = '" + schema
                + "' AND TABLE_NAME = '" + tableName + "' AND CONSTRAINT_TYPE IN ('R', 'P', 'U')";
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String constraintName = rs.getString("CONSTRAINT_NAME");
                try {
                    stmt.execute("ALTER TABLE " + schema + "." + tableName + " DISABLE CONSTRAINT " + constraintName);
                } catch (SQLException e) {
                    logger.warn("禁用约束失败: {} - {}", constraintName, e.getMessage());
                }
            }
        }
    }

    public void enableConstraints(ConnectionConfig config, String schema, String tableName) throws SQLException {
        String sql = "SELECT CONSTRAINT_NAME FROM ALL_CONSTRAINTS WHERE OWNER = '" + schema
                + "' AND TABLE_NAME = '" + tableName + "' AND CONSTRAINT_TYPE IN ('R', 'P', 'U')";
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String constraintName = rs.getString("CONSTRAINT_NAME");
                try {
                    stmt.execute("ALTER TABLE " + schema + "." + tableName + " ENABLE CONSTRAINT " + constraintName);
                } catch (SQLException e) {
                    logger.warn("启用约束失败: {} - {}", constraintName, e.getMessage());
                }
            }
        }
    }

    public void truncateTable(ConnectionConfig config, String schema, String tableName) throws SQLException {
        String qualifiedName = schema + "." + tableName;
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + qualifiedName);
        }
    }

    // ==================== 触发器相关 ====================

    public List<Map<String, String>> getTriggers(ConnectionConfig config, String schema) throws SQLException {
        List<Map<String, String>> triggers = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT TRIGGER_NAME, TABLE_NAME, TRIGGER_TYPE, TRIGGERING_EVENT, STATUS, TRIGGER_BODY " +
                     "FROM ALL_TRIGGERS WHERE OWNER = ?")) {
            stmt.setString(1, schema);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> trigger = new HashMap<>();
                    trigger.put("triggerName", rs.getString("TRIGGER_NAME"));
                    trigger.put("tableName", rs.getString("TABLE_NAME"));
                    trigger.put("triggerType", rs.getString("TRIGGER_TYPE"));
                    String event = rs.getString("TRIGGERING_EVENT");
                    trigger.put("triggeringEvent", event != null ? event.replaceAll("\\s+", ",") : "");
                    trigger.put("status", rs.getString("STATUS"));
                    Clob bodyClob = rs.getClob("TRIGGER_BODY");
                    if (bodyClob != null) {
                        trigger.put("triggerBody", bodyClob.getSubString(1, (int) bodyClob.length()));
                    } else {
                        trigger.put("triggerBody", "");
                    }
                    triggers.add(trigger);
                }
            }
        }
        return triggers;
    }

    public String getTriggerDdl(ConnectionConfig config, String schema, String triggerName) {
        try (Connection conn = DriverManager.getConnection(
                config.getJdbcUrl(), config.getUsername(), config.getPassword());
             Statement stmt = conn.createStatement()) {
            String query = "SELECT DBMS_METADATA.GET_DDL('TRIGGER', '" + triggerName + "', '" + schema + "') FROM DUAL";
            try (ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    Clob clob = rs.getClob(1);
                    if (clob != null) {
                        return clob.getSubString(1, (int) clob.length());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("获取触发器DDL失败: {} - {}", triggerName, e.getMessage());
        }
        return null;
    }
}