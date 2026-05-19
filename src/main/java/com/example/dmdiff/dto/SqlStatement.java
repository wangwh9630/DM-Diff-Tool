package com.example.dmdiff.dto;

public class SqlStatement {
    private String tableName;
    private String sql;
    private String actionType;
    private boolean success;
    private String errorMessage;

    public SqlStatement() {}

    public SqlStatement(String tableName, String sql, String actionType) {
        this.tableName = tableName;
        this.sql = sql;
        this.actionType = actionType;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}