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
import java.util.List;

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
}