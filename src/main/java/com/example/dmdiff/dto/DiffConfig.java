package com.example.dmdiff.dto;

import java.util.List;

public class DiffConfig {
    private ConnectionConfig sourceConfig;
    private ConnectionConfig targetConfig;
    private boolean ignoreCase;
    private List<String> blacklist;

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
}