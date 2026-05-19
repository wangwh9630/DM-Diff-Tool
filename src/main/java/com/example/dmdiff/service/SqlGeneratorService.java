package com.example.dmdiff.service;

import com.example.dmdiff.diff.*;
import com.example.dmdiff.dto.SqlStatement;
import com.example.dmdiff.metadata.ColumnInfo;
import com.example.dmdiff.metadata.IndexInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SqlGeneratorService {
    private static final Logger logger = LoggerFactory.getLogger(SqlGeneratorService.class);

    public String generateUpgradeSql(DiffResult diffResult) {
        StringBuilder sql = new StringBuilder();
        
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            sql.append(generateCreateTableSql(tableDiff)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            sql.append(generateAlterTableSql(tableDiff)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            sql.append(generateDropTableSql(tableDiff.getTableName())).append("\n\n");
        }
        
        return sql.toString();
    }

    public String generateRollbackSql(DiffResult diffResult) {
        StringBuilder sql = new StringBuilder();
        
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            sql.append(generateCreateTableSql(tableDiff)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            sql.append(generateRollbackAlterTableSql(tableDiff)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            sql.append(generateDropTableSql(tableDiff.getTableName())).append("\n\n");
        }
        
        return sql.toString();
    }

    public List<SqlStatement> generateCreate(DiffResult diffResult) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            String sql = generateCreateTableSql(tableDiff);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ADD"));
        }
        return statements;
    }

    public List<SqlStatement> generateAlter(DiffResult diffResult) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            String sql = generateAlterTableSql(tableDiff);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
        }
        return statements;
    }

    public List<SqlStatement> generateDrop(DiffResult diffResult) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            String sql = generateDropTableSql(tableDiff.getTableName());
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "DELETE"));
        }
        return statements;
    }

    public List<SqlStatement> rollbackAll(DiffResult diffResult) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            String sql = generateCreateTableSql(tableDiff);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_CREATE"));
        }
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            String sql = generateRollbackAlterTableSql(tableDiff);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
        }
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            String sql = generateDropTableSql(tableDiff.getTableName());
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_DROP"));
        }
        return statements;
    }

    private String generateCreateTableSql(TableDiff tableDiff) {
        StringBuilder sql = new StringBuilder();
        String tableName = getQualifiedName(tableDiff);
        sql.append("-- 创建表 ").append(tableDiff.getTableName()).append("\n");
        sql.append("CREATE TABLE ").append(tableName).append(" (\n");

        List<ColumnDiff> columnDiffs = tableDiff.getColumnDiffs();
        List<String> columnDefs = new ArrayList<>();

        for (ColumnDiff colDiff : columnDiffs) {
            ColumnInfo column = colDiff.getTargetColumn();
            if (column == null) {
                column = colDiff.getSourceColumn();
            }
            if (column == null) continue;

            StringBuilder colDef = new StringBuilder();
            colDef.append("    ").append(column.getColumnName())
                  .append(" ").append(adjustTypeForDm(column));

            if (!column.isNullable()) {
                colDef.append(" NOT NULL");
            }

            if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
                colDef.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
            }

            columnDefs.add(colDef.toString());
        }

        sql.append(String.join(",\n", columnDefs));
        sql.append("\n);");

        if (tableDiff.getIndexDiffs() != null) {
            for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
                IndexInfo index = idxDiff.getTargetIndex();
                if (index == null) {
                    index = idxDiff.getSourceIndex();
                }
                if (index != null) {
                    sql.append("\n").append(generateCreateIndexSql(tableDiff.getTableName(), index));
                }
            }
        }

        return sql.toString();
    }

    private String adjustTypeForDm(ColumnInfo column) {
        String dataType = column.getDataType().toUpperCase();
        if (dataType.equals("VARCHAR") || dataType.equals("CHAR")) {
            return dataType + "(" + column.getDataLength() + ")";
        } else if (dataType.equals("NUMBER")) {
            return "DECIMAL(" + column.getPrecision() + "," + column.getScale() + ")";
        }
        return dataType;
    }

    private String getQualifiedName(TableDiff tableDiff) {
        return tableDiff.getTableName();
    }

    private String generateDropTableSql(String tableName) {
        return String.format("-- 删除表 %s\nDROP TABLE %s;", tableName, tableName);
    }

    private String generateAlterTableSql(TableDiff tableDiff) {
        StringBuilder sql = new StringBuilder();
        String tableName = tableDiff.getTableName();
        
        List<String> dropIndexes = new ArrayList<>();
        List<String> dropConstraints = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();
        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> addIndexes = new ArrayList<>();
        List<String> addConstraints = new ArrayList<>();
        
        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.DELETE) {
                dropIndexes.add(generateDropIndexSql(tableName, idxDiff.getSourceIndex()));
            } else if (idxDiff.getDiffType() == DiffType.ADD) {
                addIndexes.add(generateCreateIndexSql(tableName, idxDiff.getTargetIndex()));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                dropIndexes.add(generateDropIndexSql(tableName, idxDiff.getSourceIndex()));
                addIndexes.add(generateCreateIndexSql(tableName, idxDiff.getTargetIndex()));
            }
        }
        
        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.DELETE) {
                dropColumns.add(generateDropColumnSql(tableName, colDiff.getSourceColumn()));
            } else if (colDiff.getDiffType() == DiffType.ADD) {
                addColumns.add(generateAddColumnSql(tableName, colDiff.getTargetColumn()));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                modifyColumns.add(generateModifyColumnSql(tableName, colDiff.getTargetColumn()));
            }
        }
        
        for (String s : dropIndexes) { sql.append(s).append("\n"); }
        for (String s : dropConstraints) { sql.append(s).append("\n"); }
        for (String s : dropColumns) { sql.append(s).append("\n"); }
        for (String s : addColumns) { sql.append(s).append("\n"); }
        for (String s : modifyColumns) { sql.append(s).append("\n"); }
        for (String s : addConstraints) { sql.append(s).append("\n"); }
        for (String s : addIndexes) { sql.append(s).append("\n"); }
        
        return sql.toString();
    }

    private String generateRollbackAlterTableSql(TableDiff tableDiff) {
        StringBuilder sql = new StringBuilder();
        String tableName = tableDiff.getTableName();
        
        List<String> dropIndexes = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();
        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> addIndexes = new ArrayList<>();
        
        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.ADD) {
                dropIndexes.add(generateDropIndexSql(tableName, idxDiff.getTargetIndex()));
            } else if (idxDiff.getDiffType() == DiffType.DELETE) {
                addIndexes.add(generateCreateIndexSql(tableName, idxDiff.getSourceIndex()));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                dropIndexes.add(generateDropIndexSql(tableName, idxDiff.getTargetIndex()));
                addIndexes.add(generateCreateIndexSql(tableName, idxDiff.getSourceIndex()));
            }
        }
        
        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.ADD) {
                dropColumns.add(generateDropColumnSql(tableName, colDiff.getTargetColumn()));
            } else if (colDiff.getDiffType() == DiffType.DELETE) {
                addColumns.add(generateAddColumnSql(tableName, colDiff.getSourceColumn()));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                modifyColumns.add(generateModifyColumnSql(tableName, colDiff.getSourceColumn()));
            }
        }
        
        for (String s : dropIndexes) { sql.append(s).append("\n"); }
        for (String s : dropColumns) { sql.append(s).append("\n"); }
        for (String s : addColumns) { sql.append(s).append("\n"); }
        for (String s : modifyColumns) { sql.append(s).append("\n"); }
        for (String s : addIndexes) { sql.append(s).append("\n"); }
        
        return sql.toString();
    }

    private String generateDropIndexSql(String tableName, IndexInfo index) {
        return String.format("-- 删除索引 %s\nDROP INDEX %s;", index.getIndexName(), index.getIndexName());
    }

    private String generateCreateIndexSql(String tableName, IndexInfo index) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 创建索引 ").append(index.getIndexName()).append("\n");
        
        if (index.isUnique()) {
            sql.append("CREATE UNIQUE INDEX ");
        } else {
            sql.append("CREATE INDEX ");
        }
        
        sql.append(index.getIndexName())
           .append(" ON ")
           .append(tableName)
           .append("(")
           .append(index.getColumnList())
           .append(");");
        
        return sql.toString();
    }

    private String generateDropColumnSql(String tableName, ColumnInfo column) {
        return String.format("-- 删除字段 %s\nALTER TABLE %s DROP COLUMN %s;", 
            column.getColumnName(), tableName, column.getColumnName());
    }

    private String generateAddColumnSql(String tableName, ColumnInfo column) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 添加字段 ").append(column.getColumnName()).append("\n");
        sql.append("ALTER TABLE ").append(tableName).append(" ADD ");
        sql.append(column.getColumnName()).append(" ").append(adjustTypeForDm(column));
        
        if (!column.isNullable()) {
            sql.append(" NOT NULL");
        }
        
        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            sql.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
        }
        
        sql.append(";");
        
        return sql.toString();
    }

    private String generateModifyColumnSql(String tableName, ColumnInfo column) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 修改字段 ").append(column.getColumnName()).append("\n");
        sql.append("ALTER TABLE ").append(tableName).append(" MODIFY ");
        sql.append(column.getColumnName()).append(" ").append(adjustTypeForDm(column));
        
        if (!column.isNullable()) {
            sql.append(" NOT NULL");
        } else {
            sql.append(" NULL");
        }
        
        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            sql.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
        }
        
        sql.append(";");
        
        return sql.toString();
    }
}