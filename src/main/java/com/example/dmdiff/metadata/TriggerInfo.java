package com.example.dmdiff.metadata;

public class TriggerInfo {
    private String triggerName;
    private String tableName;
    private String triggerType;
    private String triggeringEvent;
    private String status;
    private String triggerBody;
    private String whenClause;

    public TriggerInfo() {}

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggeringEvent() {
        return triggeringEvent;
    }

    public void setTriggeringEvent(String triggeringEvent) {
        this.triggeringEvent = triggeringEvent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTriggerBody() {
        return triggerBody;
    }

    public void setTriggerBody(String triggerBody) {
        this.triggerBody = triggerBody;
    }

    public String getWhenClause() {
        return whenClause;
    }

    public void setWhenClause(String whenClause) {
        this.whenClause = whenClause;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TriggerInfo that = (TriggerInfo) o;
        return java.util.Objects.equals(triggerName, that.triggerName) &&
               java.util.Objects.equals(tableName, that.tableName) &&
               java.util.Objects.equals(triggerType, that.triggerType) &&
               java.util.Objects.equals(triggeringEvent, that.triggeringEvent) &&
               java.util.Objects.equals(status, that.status) &&
               java.util.Objects.equals(triggerBody, that.triggerBody) &&
               java.util.Objects.equals(whenClause, that.whenClause);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(triggerName, tableName, triggerType, triggeringEvent, status, triggerBody, whenClause);
    }
}
