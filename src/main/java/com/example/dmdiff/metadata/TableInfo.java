package com.example.dmdiff.metadata;

import java.util.ArrayList;
import java.util.List;

public class TableInfo {
    private String schemaName;
    private String tableName;
    private String tableType;
    private String remarks;
    private List<ColumnInfo> columns;
    private List<IndexInfo> indexes;
    private List<TriggerInfo> triggers;
    private List<ForeignKeyInfo> foreignKeys;
    private PartitionInfo partitionInfo;

    public TableInfo() {
        this.columns = new ArrayList<>();
        this.indexes = new ArrayList<>();
        this.triggers = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
    }

    public TableInfo(String schemaName, String tableName, String tableType) {
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.tableType = tableType;
        this.columns = new ArrayList<>();
        this.indexes = new ArrayList<>();
        this.triggers = new ArrayList<>();
        this.foreignKeys = new ArrayList<>();
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    public List<IndexInfo> getIndexes() {
        return indexes;
    }

    public void setIndexes(List<IndexInfo> indexes) {
        this.indexes = indexes;
    }

    public void addColumn(ColumnInfo column) {
        this.columns.add(column);
    }

    public void addIndex(IndexInfo index) {
        this.indexes.add(index);
    }

    public List<TriggerInfo> getTriggers() {
        return triggers;
    }

    public void setTriggers(List<TriggerInfo> triggers) {
        this.triggers = triggers;
    }

    public void addTrigger(TriggerInfo trigger) {
        this.triggers.add(trigger);
    }

    public List<ForeignKeyInfo> getForeignKeys() {
        return foreignKeys;
    }

    public void setForeignKeys(List<ForeignKeyInfo> foreignKeys) {
        this.foreignKeys = foreignKeys;
    }

    public void addForeignKey(ForeignKeyInfo foreignKey) {
        this.foreignKeys.add(foreignKey);
    }

    public PartitionInfo getPartitionInfo() {
        return partitionInfo;
    }

    public void setPartitionInfo(PartitionInfo partitionInfo) {
        this.partitionInfo = partitionInfo;
    }

    public boolean isPartitioned() {
        return partitionInfo != null;
    }

    public String getFullName() {
        if (schemaName != null && !schemaName.isEmpty()) {
            return schemaName + "." + tableName;
        }
        return tableName;
    }
}