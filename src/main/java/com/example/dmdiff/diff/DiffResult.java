package com.example.dmdiff.diff;

import java.util.ArrayList;
import java.util.List;

public class DiffResult {
    private List<TableDiff> addedTables;
    private List<TableDiff> deletedTables;
    private List<TableDiff> modifiedTables;

    public DiffResult() {
        this.addedTables = new ArrayList<>();
        this.deletedTables = new ArrayList<>();
        this.modifiedTables = new ArrayList<>();
    }

    public List<TableDiff> getAddedTables() {
        return addedTables;
    }

    public void setAddedTables(List<TableDiff> addedTables) {
        this.addedTables = addedTables;
    }

    public List<TableDiff> getDeletedTables() {
        return deletedTables;
    }

    public void setDeletedTables(List<TableDiff> deletedTables) {
        this.deletedTables = deletedTables;
    }

    public List<TableDiff> getModifiedTables() {
        return modifiedTables;
    }

    public void setModifiedTables(List<TableDiff> modifiedTables) {
        this.modifiedTables = modifiedTables;
    }

    public void addAddedTable(TableDiff diff) {
        this.addedTables.add(diff);
    }

    public void addDeletedTable(TableDiff diff) {
        this.deletedTables.add(diff);
    }

    public void addModifiedTable(TableDiff diff) {
        this.modifiedTables.add(diff);
    }

    public boolean hasChanges() {
        return !addedTables.isEmpty() || !deletedTables.isEmpty() || !modifiedTables.isEmpty();
    }

    public int getTotalChanges() {
        return addedTables.size() + deletedTables.size() + modifiedTables.size();
    }
}