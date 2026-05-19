package com.example.dmdiff.diff;

import com.example.dmdiff.metadata.ColumnInfo;

public class ColumnDiff {
    private String columnName;
    private DiffType diffType;
    private ColumnInfo sourceColumn;
    private ColumnInfo targetColumn;
    private String changeDetail;

    public ColumnDiff() {}

    public ColumnDiff(String columnName, DiffType diffType) {
        this.columnName = columnName;
        this.diffType = diffType;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    public ColumnInfo getSourceColumn() {
        return sourceColumn;
    }

    public void setSourceColumn(ColumnInfo sourceColumn) {
        this.sourceColumn = sourceColumn;
    }

    public ColumnInfo getTargetColumn() {
        return targetColumn;
    }

    public void setTargetColumn(ColumnInfo targetColumn) {
        this.targetColumn = targetColumn;
    }

    public String getChangeDetail() {
        return changeDetail;
    }

    public void setChangeDetail(String changeDetail) {
        this.changeDetail = changeDetail;
    }
}