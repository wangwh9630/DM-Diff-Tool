package com.example.dmdiff.diff;

import java.util.ArrayList;
import java.util.List;

public class TableDiff {
    private String tableName;
    private DiffType diffType;
    private List<ColumnDiff> columnDiffs;
    private List<IndexDiff> indexDiffs;
    private List<TriggerDiff> triggerDiffs;
    private List<ForeignKeyDiff> foreignKeyDiffs;
    private PartitionDiff partitionDiff;
    private boolean columnOrderChanged;

    public TableDiff() {
        this.columnDiffs = new ArrayList<>();
        this.indexDiffs = new ArrayList<>();
        this.triggerDiffs = new ArrayList<>();
        this.foreignKeyDiffs = new ArrayList<>();
    }

    public TableDiff(String tableName, DiffType diffType) {
        this.tableName = tableName;
        this.diffType = diffType;
        this.columnDiffs = new ArrayList<>();
        this.indexDiffs = new ArrayList<>();
        this.triggerDiffs = new ArrayList<>();
        this.foreignKeyDiffs = new ArrayList<>();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    public List<ColumnDiff> getColumnDiffs() {
        return columnDiffs;
    }

    public void setColumnDiffs(List<ColumnDiff> columnDiffs) {
        this.columnDiffs = columnDiffs;
    }

    public List<IndexDiff> getIndexDiffs() {
        return indexDiffs;
    }

    public void setIndexDiffs(List<IndexDiff> indexDiffs) {
        this.indexDiffs = indexDiffs;
    }

    public void addColumnDiff(ColumnDiff diff) {
        this.columnDiffs.add(diff);
    }

    public void addIndexDiff(IndexDiff diff) {
        this.indexDiffs.add(diff);
    }

    public boolean hasChanges() {
        return !columnDiffs.isEmpty() || !indexDiffs.isEmpty();
    }
}