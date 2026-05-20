package com.example.dmdiff.dto;

public class MigrationProgress {
    private int total;
    private int completed;
    private String tableName;
    private String phase;
    private boolean success;
    private String message;
    private boolean done;

    public MigrationProgress() {}

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getCompleted() { return completed; }
    public void setCompleted(int completed) { this.completed = completed; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}