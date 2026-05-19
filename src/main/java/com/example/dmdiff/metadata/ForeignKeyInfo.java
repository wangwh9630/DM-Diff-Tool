package com.example.dmdiff.metadata;

import java.util.ArrayList;
import java.util.List;

public class ForeignKeyInfo {
    private String constraintName;
    private String tableName;
    private List<String> columnNames;
    private String referencedTableName;
    private List<String> referencedColumnNames;
    private String deleteRule;
    private String updateRule;
    private String status;

    public ForeignKeyInfo() {
        this.columnNames = new ArrayList<>();
        this.referencedColumnNames = new ArrayList<>();
    }

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public void setColumnNames(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public void addColumnName(String columnName) {
        this.columnNames.add(columnName);
    }

    public String getColumnList() {
        return String.join(", ", columnNames);
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public void setReferencedTableName(String referencedTableName) {
        this.referencedTableName = referencedTableName;
    }

    public List<String> getReferencedColumnNames() {
        return referencedColumnNames;
    }

    public void setReferencedColumnNames(List<String> referencedColumnNames) {
        this.referencedColumnNames = referencedColumnNames;
    }

    public void addReferencedColumnName(String columnName) {
        this.referencedColumnNames.add(columnName);
    }

    public String getReferencedColumnList() {
        return String.join(", ", referencedColumnNames);
    }

    public String getDeleteRule() {
        return deleteRule;
    }

    public void setDeleteRule(String deleteRule) {
        this.deleteRule = deleteRule;
    }

    public String getUpdateRule() {
        return updateRule;
    }

    public void setUpdateRule(String updateRule) {
        this.updateRule = updateRule;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForeignKeyInfo that = (ForeignKeyInfo) o;
        return java.util.Objects.equals(constraintName, that.constraintName) &&
               java.util.Objects.equals(tableName, that.tableName) &&
               java.util.Objects.equals(columnNames, that.columnNames) &&
               java.util.Objects.equals(referencedTableName, that.referencedTableName) &&
               java.util.Objects.equals(referencedColumnNames, that.referencedColumnNames) &&
               java.util.Objects.equals(deleteRule, that.deleteRule) &&
               java.util.Objects.equals(updateRule, that.updateRule);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(constraintName, tableName, columnNames, referencedTableName,
                referencedColumnNames, deleteRule, updateRule);
    }
}
