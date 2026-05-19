package com.example.dmdiff.metadata;

import java.util.ArrayList;
import java.util.List;

public class IndexInfo {
    private String indexName;
    private boolean unique;
    private boolean clustered;
    private List<String> columnNames;

    public IndexInfo() {
        this.columnNames = new ArrayList<>();
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public boolean isClustered() {
        return clustered;
    }

    public void setClustered(boolean clustered) {
        this.clustered = clustered;
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
}