package com.example.dmdiff.dto;

import java.util.List;

public class MigrationRequest {
    private List<String> tableNames;
    private boolean migrateStructure = true;
    private boolean migrateData = true;
    private int batchSize = 1000;

    public MigrationRequest() {}

    public List<String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
    }

    public boolean isMigrateStructure() {
        return migrateStructure;
    }

    public void setMigrateStructure(boolean migrateStructure) {
        this.migrateStructure = migrateStructure;
    }

    public boolean isMigrateData() {
        return migrateData;
    }

    public void setMigrateData(boolean migrateData) {
        this.migrateData = migrateData;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}