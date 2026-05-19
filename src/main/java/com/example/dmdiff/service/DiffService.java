package com.example.dmdiff.service;

import com.example.dmdiff.diff.*;
import com.example.dmdiff.dto.ConnectionConfig;
import com.example.dmdiff.dto.DiffConfig;
import com.example.dmdiff.metadata.ColumnInfo;
import com.example.dmdiff.metadata.IndexInfo;
import com.example.dmdiff.metadata.TableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DiffService {
    private static final Logger logger = LoggerFactory.getLogger(DiffService.class);

    @Autowired
    private DatabaseService databaseService;

    public DiffResult compare(DiffConfig config) throws SQLException {
        List<TableInfo> sourceTables = databaseService.getAllTables(config.getSourceConfig());
        List<TableInfo> targetTables = databaseService.getAllTables(config.getTargetConfig());

        sourceTables = filterTables(sourceTables, config.getBlacklist(), config.isIgnoreCase());
        targetTables = filterTables(targetTables, config.getBlacklist(), config.isIgnoreCase());

        return compareTables(sourceTables, targetTables, config.isIgnoreCase());
    }

    private List<TableInfo> filterTables(List<TableInfo> tables, List<String> blacklist, boolean ignoreCase) {
        List<TableInfo> filtered = new ArrayList<>();
        
        for (TableInfo table : tables) {
            String tableName = ignoreCase ? table.getTableName().toLowerCase() : table.getTableName();
            boolean shouldFilter = false;
            
            if (blacklist != null) {
                for (String pattern : blacklist) {
                    String patternToMatch = ignoreCase ? pattern.toLowerCase() : pattern;
                    if (tableName.matches(patternToMatch)) {
                        shouldFilter = true;
                        break;
                    }
                }
            }
            
            if (!shouldFilter) {
                filtered.add(table);
            }
        }
        
        return filtered;
    }

    private DiffResult compareTables(List<TableInfo> sourceTables, List<TableInfo> targetTables, boolean ignoreCase) {
        DiffResult result = new DiffResult();
        
        Map<String, TableInfo> sourceTableMap = new HashMap<>();
        Map<String, TableInfo> targetTableMap = new HashMap<>();
        
        for (TableInfo table : sourceTables) {
            String key = ignoreCase ? table.getTableName().toLowerCase() : table.getTableName();
            sourceTableMap.put(key, table);
        }
        
        for (TableInfo table : targetTables) {
            String key = ignoreCase ? table.getTableName().toLowerCase() : table.getTableName();
            targetTableMap.put(key, table);
        }
        
        for (Map.Entry<String, TableInfo> entry : sourceTableMap.entrySet()) {
            String key = entry.getKey();
            TableInfo sourceTable = entry.getValue();
            
            if (!targetTableMap.containsKey(key)) {
                TableDiff diff = new TableDiff(sourceTable.getTableName(), DiffType.DELETE);
                for (ColumnInfo col : sourceTable.getColumns()) {
                    ColumnDiff colDiff = new ColumnDiff(col.getColumnName(), DiffType.DELETE);
                    colDiff.setSourceColumn(col);
                    diff.addColumnDiff(colDiff);
                }
                for (IndexInfo idx : sourceTable.getIndexes()) {
                    IndexDiff idxDiff = new IndexDiff(idx.getIndexName(), DiffType.DELETE);
                    idxDiff.setSourceIndex(idx);
                    diff.addIndexDiff(idxDiff);
                }
                result.addDeletedTable(diff);
            }
        }
        
        for (Map.Entry<String, TableInfo> entry : targetTableMap.entrySet()) {
            String key = entry.getKey();
            TableInfo targetTable = entry.getValue();
            
            if (!sourceTableMap.containsKey(key)) {
                TableDiff diff = new TableDiff(targetTable.getTableName(), DiffType.ADD);
                for (ColumnInfo col : targetTable.getColumns()) {
                    ColumnDiff colDiff = new ColumnDiff(col.getColumnName(), DiffType.ADD);
                    colDiff.setTargetColumn(col);
                    diff.addColumnDiff(colDiff);
                }
                for (IndexInfo idx : targetTable.getIndexes()) {
                    IndexDiff idxDiff = new IndexDiff(idx.getIndexName(), DiffType.ADD);
                    idxDiff.setTargetIndex(idx);
                    diff.addIndexDiff(idxDiff);
                }
                result.addAddedTable(diff);
            } else {
                TableInfo sourceTable = sourceTableMap.get(key);
                TableDiff tableDiff = compareTableDetails(sourceTable, targetTable, ignoreCase);
                
                if (tableDiff.hasChanges()) {
                    result.addModifiedTable(tableDiff);
                }
            }
        }
        
        return result;
    }

    private TableDiff compareTableDetails(TableInfo sourceTable, TableInfo targetTable, boolean ignoreCase) {
        TableDiff diff = new TableDiff(sourceTable.getTableName(), DiffType.MODIFY);
        
        compareColumns(sourceTable.getColumns(), targetTable.getColumns(), diff, ignoreCase);
        compareIndexes(sourceTable.getIndexes(), targetTable.getIndexes(), diff, ignoreCase);
        
        return diff;
    }

    private void compareColumns(List<ColumnInfo> sourceColumns, List<ColumnInfo> targetColumns, 
                                TableDiff tableDiff, boolean ignoreCase) {
        Map<String, ColumnInfo> sourceColumnMap = new HashMap<>();
        Map<String, ColumnInfo> targetColumnMap = new HashMap<>();
        
        for (ColumnInfo col : sourceColumns) {
            String key = ignoreCase ? col.getColumnName().toLowerCase() : col.getColumnName();
            sourceColumnMap.put(key, col);
        }
        
        for (ColumnInfo col : targetColumns) {
            String key = ignoreCase ? col.getColumnName().toLowerCase() : col.getColumnName();
            targetColumnMap.put(key, col);
        }
        
        for (Map.Entry<String, ColumnInfo> entry : sourceColumnMap.entrySet()) {
            String key = entry.getKey();
            ColumnInfo sourceCol = entry.getValue();
            
            if (!targetColumnMap.containsKey(key)) {
                ColumnDiff colDiff = new ColumnDiff(sourceCol.getColumnName(), DiffType.DELETE);
                colDiff.setSourceColumn(sourceCol);
                tableDiff.addColumnDiff(colDiff);
            }
        }
        
        for (Map.Entry<String, ColumnInfo> entry : targetColumnMap.entrySet()) {
            String key = entry.getKey();
            ColumnInfo targetCol = entry.getValue();
            
            if (!sourceColumnMap.containsKey(key)) {
                ColumnDiff colDiff = new ColumnDiff(targetCol.getColumnName(), DiffType.ADD);
                colDiff.setTargetColumn(targetCol);
                tableDiff.addColumnDiff(colDiff);
            } else {
                ColumnInfo sourceCol = sourceColumnMap.get(key);
                ColumnDiff colDiff = compareColumnDetails(sourceCol, targetCol);
                
                if (colDiff != null) {
                    tableDiff.addColumnDiff(colDiff);
                }
            }
        }
    }

    private ColumnDiff compareColumnDetails(ColumnInfo source, ColumnInfo target) {
        StringBuilder changes = new StringBuilder();
        
        if (!Objects.equals(source.getDataType(), target.getDataType())) {
            changes.append(String.format("类型: %s -> %s; ", source.getDataType(), target.getDataType()));
        }
        
        if (source.getDataLength() != target.getDataLength()) {
            changes.append(String.format("长度: %d -> %d; ", source.getDataLength(), target.getDataLength()));
        }
        
        if (source.getPrecision() != target.getPrecision()) {
            changes.append(String.format("精度: %d -> %d; ", source.getPrecision(), target.getPrecision()));
        }
        
        if (source.getScale() != target.getScale()) {
            changes.append(String.format("小数位: %d -> %d; ", source.getScale(), target.getScale()));
        }
        
        if (source.isNullable() != target.isNullable()) {
            changes.append(String.format("可空: %s -> %s; ", source.isNullable(), target.isNullable()));
        }
        
        if (!Objects.equals(source.getDefaultValue(), target.getDefaultValue())) {
            changes.append(String.format("默认值: %s -> %s; ", 
                source.getDefaultValue() != null ? source.getDefaultValue() : "NULL", 
                target.getDefaultValue() != null ? target.getDefaultValue() : "NULL"));
        }
        
        if (!Objects.equals(source.getRemarks(), target.getRemarks())) {
            changes.append(String.format("注释: %s -> %s; ", 
                source.getRemarks() != null ? source.getRemarks() : "", 
                target.getRemarks() != null ? target.getRemarks() : ""));
        }
        
        if (changes.length() > 0) {
            ColumnDiff diff = new ColumnDiff(source.getColumnName(), DiffType.MODIFY);
            diff.setSourceColumn(source);
            diff.setTargetColumn(target);
            diff.setChangeDetail(changes.toString().trim());
            return diff;
        }
        
        return null;
    }

    private void compareIndexes(List<IndexInfo> sourceIndexes, List<IndexInfo> targetIndexes,
                                TableDiff tableDiff, boolean ignoreCase) {
        Map<String, IndexInfo> sourceIndexMap = new HashMap<>();
        Map<String, IndexInfo> targetIndexMap = new HashMap<>();
        
        for (IndexInfo idx : sourceIndexes) {
            String key = ignoreCase ? idx.getIndexName().toLowerCase() : idx.getIndexName();
            sourceIndexMap.put(key, idx);
        }
        
        for (IndexInfo idx : targetIndexes) {
            String key = ignoreCase ? idx.getIndexName().toLowerCase() : idx.getIndexName();
            targetIndexMap.put(key, idx);
        }
        
        for (Map.Entry<String, IndexInfo> entry : sourceIndexMap.entrySet()) {
            String key = entry.getKey();
            IndexInfo sourceIdx = entry.getValue();
            
            if (!targetIndexMap.containsKey(key)) {
                IndexDiff idxDiff = new IndexDiff(sourceIdx.getIndexName(), DiffType.DELETE);
                idxDiff.setSourceIndex(sourceIdx);
                tableDiff.addIndexDiff(idxDiff);
            }
        }
        
        for (Map.Entry<String, IndexInfo> entry : targetIndexMap.entrySet()) {
            String key = entry.getKey();
            IndexInfo targetIdx = entry.getValue();
            
            if (!sourceIndexMap.containsKey(key)) {
                IndexDiff idxDiff = new IndexDiff(targetIdx.getIndexName(), DiffType.ADD);
                idxDiff.setTargetIndex(targetIdx);
                tableDiff.addIndexDiff(idxDiff);
            } else {
                IndexInfo sourceIdx = sourceIndexMap.get(key);
                
                if (!indexEquals(sourceIdx, targetIdx, ignoreCase)) {
                    IndexDiff idxDiff = new IndexDiff(sourceIdx.getIndexName(), DiffType.MODIFY);
                    idxDiff.setSourceIndex(sourceIdx);
                    idxDiff.setTargetIndex(targetIdx);
                    tableDiff.addIndexDiff(idxDiff);
                }
            }
        }
    }

    private boolean indexEquals(IndexInfo source, IndexInfo target, boolean ignoreCase) {
        if (source.isUnique() != target.isUnique()) return false;
        if (source.isClustered() != target.isClustered()) return false;
        
        List<String> sourceCols = source.getColumnNames();
        List<String> targetCols = target.getColumnNames();
        
        if (sourceCols.size() != targetCols.size()) return false;
        
        for (int i = 0; i < sourceCols.size(); i++) {
            String sourceCol = ignoreCase ? sourceCols.get(i).toLowerCase() : sourceCols.get(i);
            String targetCol = ignoreCase ? targetCols.get(i).toLowerCase() : targetCols.get(i);
            
            if (!sourceCol.equals(targetCol)) {
                return false;
            }
        }
        
        return true;
    }
}