package com.example.dmdiff.dto;

public class ConnectionResult {
    private boolean success;
    private String message;
    private String dbVersion;
    private boolean caseSensitive;

    public ConnectionResult() {}

    public static ConnectionResult success(String dbVersion, boolean caseSensitive) {
        ConnectionResult result = new ConnectionResult();
        result.success = true;
        result.message = "连接成功";
        result.dbVersion = dbVersion;
        result.caseSensitive = caseSensitive;
        return result;
    }

    public static ConnectionResult failure(String message) {
        ConnectionResult result = new ConnectionResult();
        result.success = false;
        result.message = message;
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDbVersion() {
        return dbVersion;
    }

    public void setDbVersion(String dbVersion) {
        this.dbVersion = dbVersion;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
}