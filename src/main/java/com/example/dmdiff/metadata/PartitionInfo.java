package com.example.dmdiff.metadata;

import java.util.ArrayList;
import java.util.List;

public class PartitionInfo {
    private String tableName;
    private String partitionType;
    private String partitionColumn;
    private int partitionCount;
    private List<PartitionDetail> partitions;

    public PartitionInfo() {
        this.partitions = new ArrayList<>();
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getPartitionType() {
        return partitionType;
    }

    public void setPartitionType(String partitionType) {
        this.partitionType = partitionType;
    }

    public String getPartitionColumn() {
        return partitionColumn;
    }

    public void setPartitionColumn(String partitionColumn) {
        this.partitionColumn = partitionColumn;
    }

    public int getPartitionCount() {
        return partitionCount;
    }

    public void setPartitionCount(int partitionCount) {
        this.partitionCount = partitionCount;
    }

    public List<PartitionDetail> getPartitions() {
        return partitions;
    }

    public void setPartitions(List<PartitionDetail> partitions) {
        this.partitions = partitions;
    }

    public void addPartition(PartitionDetail partition) {
        this.partitions.add(partition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartitionInfo that = (PartitionInfo) o;
        return partitionCount == that.partitionCount &&
               java.util.Objects.equals(tableName, that.tableName) &&
               java.util.Objects.equals(partitionType, that.partitionType) &&
               java.util.Objects.equals(partitionColumn, that.partitionColumn) &&
               java.util.Objects.equals(partitions, that.partitions);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(tableName, partitionType, partitionColumn, partitionCount, partitions);
    }

    public static class PartitionDetail {
        private String partitionName;
        private String highValue;
        private String tablespaceName;

        public PartitionDetail() {}

        public String getPartitionName() {
            return partitionName;
        }

        public void setPartitionName(String partitionName) {
            this.partitionName = partitionName;
        }

        public String getHighValue() {
            return highValue;
        }

        public void setHighValue(String highValue) {
            this.highValue = highValue;
        }

        public String getTablespaceName() {
            return tablespaceName;
        }

        public void setTablespaceName(String tablespaceName) {
            this.tablespaceName = tablespaceName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PartitionDetail that = (PartitionDetail) o;
            return java.util.Objects.equals(partitionName, that.partitionName) &&
                   java.util.Objects.equals(highValue, that.highValue);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(partitionName, highValue);
        }
    }
}
