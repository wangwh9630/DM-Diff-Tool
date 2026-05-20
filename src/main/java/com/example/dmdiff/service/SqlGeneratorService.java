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

    // ==================== 文本展示用方法（保留原有，方便界面展示） ====================

    public String generateUpgradeSql(DiffResult diffResult, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            sql.append(generateCreateTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            sql.append(generateAlterTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            sql.append(generateDropTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        return sql.toString();
    }

    public String generateRollbackSql(DiffResult diffResult, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            sql.append(generateCreateTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            sql.append(generateRollbackAlterTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            sql.append(generateDropTableSql(tableDiff, sourceSchema)).append("\n\n");
        }
        
        return sql.toString();
    }

    // ==================== 逐条执行用方法（核心修复） ====================

    public List<SqlStatement> generateCreate(DiffResult diffResult, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            String tableOnlySql = generateCreateTableOnlySql(tableDiff, sourceSchema);
            statements.add(new SqlStatement(tableDiff.getTableName(), tableOnlySql, "ADD"));
            if (tableDiff.getIndexDiffs() != null) {
                for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
                    IndexInfo index = idxDiff.getTargetIndex();
                    if (index == null) {
                        index = idxDiff.getSourceIndex();
                    }
                    if (index != null) {
                        String idxSql = generateCreateIndexSql(getQualifiedName(tableDiff, sourceSchema), index);
                        statements.add(new SqlStatement(tableDiff.getTableName(), idxSql, "ADD"));
                    }
                }
            }
        }
        return statements;
    }

    public List<SqlStatement> generateAlter(DiffResult diffResult, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            statements.addAll(generateAlterStatements(tableDiff, sourceSchema));
        }
        return statements;
    }

    public List<SqlStatement> generateDrop(DiffResult diffResult, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            String sql = generateDropTableSql(tableDiff, sourceSchema);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "DELETE"));
        }
        return statements;
    }

    public List<SqlStatement> rollbackAll(DiffResult diffResult, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        for (TableDiff tableDiff : diffResult.getDeletedTables()) {
            String sql = generateCreateTableOnlySql(tableDiff, sourceSchema);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_CREATE"));
            if (tableDiff.getIndexDiffs() != null) {
                for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
                    IndexInfo index = idxDiff.getSourceIndex();
                    if (index != null) {
                        String idxSql = generateCreateIndexSql(getQualifiedName(tableDiff, sourceSchema), index);
                        statements.add(new SqlStatement(tableDiff.getTableName(), idxSql, "ROLLBACK_CREATE"));
                    }
                }
            }
        }
        for (TableDiff tableDiff : diffResult.getModifiedTables()) {
            statements.addAll(generateRollbackAlterStatements(tableDiff, sourceSchema));
        }
        for (TableDiff tableDiff : diffResult.getAddedTables()) {
            String sql = generateDropTableSql(tableDiff, sourceSchema);
            statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_DROP"));
        }
        return statements;
    }

    // ==================== 拆分 ALTER 为逐条语句 ====================

    private List<SqlStatement> generateAlterStatements(TableDiff tableDiff, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        String qualifiedName = getQualifiedName(tableDiff, sourceSchema);

        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.DELETE) {
                String sql = generateDropIndexSql(qualifiedName, idxDiff.getSourceIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
            } else if (idxDiff.getDiffType() == DiffType.ADD) {
                String sql = generateCreateIndexSql(qualifiedName, idxDiff.getTargetIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                String dropSql = generateDropIndexSql(qualifiedName, idxDiff.getSourceIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), dropSql, "MODIFY"));
                String createSql = generateCreateIndexSql(qualifiedName, idxDiff.getTargetIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), createSql, "MODIFY"));
            }
        }

        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.DELETE) {
                String sql = generateDropColumnSql(qualifiedName, colDiff.getSourceColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
            } else if (colDiff.getDiffType() == DiffType.ADD) {
                String sql = generateAddColumnSql(qualifiedName, colDiff.getTargetColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                String sql = generateModifyColumnSql(qualifiedName, colDiff.getTargetColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "MODIFY"));
            }
        }

        return statements;
    }

    private List<SqlStatement> generateRollbackAlterStatements(TableDiff tableDiff, String sourceSchema) {
        List<SqlStatement> statements = new ArrayList<>();
        String qualifiedName = getQualifiedName(tableDiff, sourceSchema);

        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.ADD) {
                String sql = generateDropIndexSql(qualifiedName, idxDiff.getTargetIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
            } else if (idxDiff.getDiffType() == DiffType.DELETE) {
                String sql = generateCreateIndexSql(qualifiedName, idxDiff.getSourceIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                String dropSql = generateDropIndexSql(qualifiedName, idxDiff.getTargetIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), dropSql, "ROLLBACK_MODIFY"));
                String createSql = generateCreateIndexSql(qualifiedName, idxDiff.getSourceIndex());
                statements.add(new SqlStatement(tableDiff.getTableName(), createSql, "ROLLBACK_MODIFY"));
            }
        }

        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.ADD) {
                String sql = generateDropColumnSql(qualifiedName, colDiff.getTargetColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
            } else if (colDiff.getDiffType() == DiffType.DELETE) {
                String sql = generateAddColumnSql(qualifiedName, colDiff.getSourceColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                String sql = generateModifyColumnSql(qualifiedName, colDiff.getSourceColumn());
                statements.add(new SqlStatement(tableDiff.getTableName(), sql, "ROLLBACK_MODIFY"));
            }
        }

        return statements;
    }

    // ==================== SQL 语句生成 ====================

    private String getQualifiedName(TableDiff tableDiff, String sourceSchema) {
        if (sourceSchema != null && !sourceSchema.isEmpty()) {
            return sourceSchema + "." + tableDiff.getTableName();
        }
        if (tableDiff.getSchemaName() != null && !tableDiff.getSchemaName().isEmpty()) {
            return tableDiff.getSchemaName() + "." + tableDiff.getTableName();
        }
        return tableDiff.getTableName();
    }

    private String getSchemaPrefix(String tableName) {
        if (tableName != null && tableName.contains(".")) {
            return tableName.substring(0, tableName.indexOf('.') + 1);
        }
        return "";
    }

    private String generateCreateTableSql(TableDiff tableDiff, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        String tableName = getQualifiedName(tableDiff, sourceSchema);
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
                    sql.append("\n").append(generateCreateIndexSql(tableName, index));
                }
            }
        }

        return sql.toString();
    }

    private String generateCreateTableOnlySql(TableDiff tableDiff, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        String tableName = getQualifiedName(tableDiff, sourceSchema);
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

    private String generateDropTableSql(TableDiff tableDiff, String sourceSchema) {
        String qualifiedName = getQualifiedName(tableDiff, sourceSchema);
        return String.format("-- 删除表 %s\nDROP TABLE %s;", tableDiff.getTableName(), qualifiedName);
    }

    private String generateAlterTableSql(TableDiff tableDiff, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        String qualifiedName = getQualifiedName(tableDiff, sourceSchema);
        
        List<String> dropIndexes = new ArrayList<>();
        List<String> dropConstraints = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();
        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> addIndexes = new ArrayList<>();
        List<String> addConstraints = new ArrayList<>();
        
        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.DELETE) {
                dropIndexes.add(generateDropIndexSql(qualifiedName, idxDiff.getSourceIndex()));
            } else if (idxDiff.getDiffType() == DiffType.ADD) {
                addIndexes.add(generateCreateIndexSql(qualifiedName, idxDiff.getTargetIndex()));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                dropIndexes.add(generateDropIndexSql(qualifiedName, idxDiff.getSourceIndex()));
                addIndexes.add(generateCreateIndexSql(qualifiedName, idxDiff.getTargetIndex()));
            }
        }
        
        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.DELETE) {
                dropColumns.add(generateDropColumnSql(qualifiedName, colDiff.getSourceColumn()));
            } else if (colDiff.getDiffType() == DiffType.ADD) {
                addColumns.add(generateAddColumnSql(qualifiedName, colDiff.getTargetColumn()));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                modifyColumns.add(generateModifyColumnSql(qualifiedName, colDiff.getTargetColumn()));
            }
        }
        
        for (String s : dropIndexes) { sql.append(s).append("\n"); }
        for (String s : dropConstraints) { sql.append(s).append("\n"); }
        for (String s : dropColumns) { sql.append(s).append("\n"); }
        for (String s : addColumns) { sql.append(s).append("\n"); }
        for (String s : modifyColumns) { sql.append(s).append("\n"); }
        for (String s : addConstraints) { sql.append(s).append("\n"); }
        for (String s : addIndexes) { sql.append(s).append("\n"); }
        
        return sql.toString().trim();
    }

    private String generateRollbackAlterTableSql(TableDiff tableDiff, String sourceSchema) {
        StringBuilder sql = new StringBuilder();
        String qualifiedName = getQualifiedName(tableDiff, sourceSchema);
        
        List<String> dropIndexes = new ArrayList<>();
        List<String> dropColumns = new ArrayList<>();
        List<String> addColumns = new ArrayList<>();
        List<String> modifyColumns = new ArrayList<>();
        List<String> addIndexes = new ArrayList<>();
        
        for (IndexDiff idxDiff : tableDiff.getIndexDiffs()) {
            if (idxDiff.getDiffType() == DiffType.ADD) {
                dropIndexes.add(generateDropIndexSql(qualifiedName, idxDiff.getTargetIndex()));
            } else if (idxDiff.getDiffType() == DiffType.DELETE) {
                addIndexes.add(generateCreateIndexSql(qualifiedName, idxDiff.getSourceIndex()));
            } else if (idxDiff.getDiffType() == DiffType.MODIFY) {
                dropIndexes.add(generateDropIndexSql(qualifiedName, idxDiff.getTargetIndex()));
                addIndexes.add(generateCreateIndexSql(qualifiedName, idxDiff.getSourceIndex()));
            }
        }
        
        for (ColumnDiff colDiff : tableDiff.getColumnDiffs()) {
            if (colDiff.getDiffType() == DiffType.ADD) {
                dropColumns.add(generateDropColumnSql(qualifiedName, colDiff.getTargetColumn()));
            } else if (colDiff.getDiffType() == DiffType.DELETE) {
                addColumns.add(generateAddColumnSql(qualifiedName, colDiff.getSourceColumn()));
            } else if (colDiff.getDiffType() == DiffType.MODIFY) {
                modifyColumns.add(generateModifyColumnSql(qualifiedName, colDiff.getSourceColumn()));
            }
        }
        
        for (String s : dropIndexes) { sql.append(s).append("\n"); }
        for (String s : dropColumns) { sql.append(s).append("\n"); }
        for (String s : addColumns) { sql.append(s).append("\n"); }
        for (String s : modifyColumns) { sql.append(s).append("\n"); }
        for (String s : addIndexes) { sql.append(s).append("\n"); }
        
        return sql.toString().trim();
    }

    private String generateDropIndexSql(String tableName, IndexInfo index) {
        String schemaPrefix = getSchemaPrefix(tableName);
        return String.format("-- 删除索引 %s\nDROP INDEX %s%s;", 
            index.getIndexName(), schemaPrefix, index.getIndexName());
    }

    private String generateCreateIndexSql(String tableName, IndexInfo index) {
        StringBuilder sql = new StringBuilder();
        sql.append("-- 创建索引 ").append(index.getIndexName()).append("\n");
        
        if (index.isUnique()) {
            sql.append("CREATE UNIQUE INDEX ");
        } else {
            sql.append("CREATE INDEX ");
        }

        String schemaPrefix = getSchemaPrefix(tableName);
        
        sql.append(schemaPrefix).append(index.getIndexName())
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