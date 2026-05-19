package com.example.dmdiff.diff;

import com.example.dmdiff.metadata.PartitionInfo;

public class PartitionDiff {
    private String tableName;
    private DiffType diffType;
    private PartitionInfo sourcePartition;
    private PartitionInfo targetPartition;
    private String changeDetail;

    public PartitionDiff() {}

    public PartitionDiff(String tableName, DiffType diffType) {
        this.tableName = tableName;
        this.diffType = diffType;
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

    public PartitionInfo getSourcePartition() {
        return sourcePartition;
    }

    public void setSourcePartition(PartitionInfo sourcePartition) {
        this.sourcePartition = sourcePartition;
    }

    public PartitionInfo getTargetPartition() {
        return targetPartition;
    }

    public void setTargetPartition(PartitionInfo targetPartition) {
        this.targetPartition = targetPartition;
    }

    public String getChangeDetail() {
        return changeDetail;
    }

    public void setChangeDetail(String changeDetail) {
        this.changeDetail = changeDetail;
    }
}
