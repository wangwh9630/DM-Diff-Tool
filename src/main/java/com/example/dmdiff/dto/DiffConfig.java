package com.example.dmdiff.dto;

import java.util.List;

public class DiffConfig {
    private ConnectionConfig sourceConfig;
    private ConnectionConfig targetConfig;
    private boolean ignoreCase;
    private List<String> blacklist;
    private boolean compareTables = true;
    private boolean compareViews;
    private boolean compareIndexes;
    private boolean compareProcedures;

    public DiffConfig() {}

    public ConnectionConfig getSourceConfig() {
        return sourceConfig;
    }

    public void setSourceConfig(ConnectionConfig sourceConfig) {
        this.sourceConfig = sourceConfig;
    }

    public ConnectionConfig getTargetConfig() {
        return targetConfig;
    }

    public void setTargetConfig(ConnectionConfig targetConfig) {
        this.targetConfig = targetConfig;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public List<String> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(List<String> blacklist) {
        this.blacklist = blacklist;
    }

    public boolean isCompareTables() {
        return compareTables;
    }

    public void setCompareTables(boolean compareTables) {
        this.compareTables = compareTables;
    }

    public boolean isCompareViews() {
        return compareViews;
    }

    public void setCompareViews(boolean compareViews) {
        this.compareViews = compareViews;
    }

    public boolean isCompareIndexes() {
        return compareIndexes;
    }

    public void setCompareIndexes(boolean compareIndexes) {
        this.compareIndexes = compareIndexes;
    }

    public boolean isCompareProcedures() {
        return compareProcedures;
    }

    public void setCompareProcedures(boolean compareProcedures) {
        this.compareProcedures = compareProcedures;
    }
}