package com.example.dmdiff.diff;

import com.example.dmdiff.metadata.TriggerInfo;

public class TriggerDiff {
    private String triggerName;
    private DiffType diffType;
    private TriggerInfo sourceTrigger;
    private TriggerInfo targetTrigger;
    private String changeDetail;

    public TriggerDiff() {}

    public TriggerDiff(String triggerName, DiffType diffType) {
        this.triggerName = triggerName;
        this.diffType = diffType;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    public TriggerInfo getSourceTrigger() {
        return sourceTrigger;
    }

    public void setSourceTrigger(TriggerInfo sourceTrigger) {
        this.sourceTrigger = sourceTrigger;
    }

    public TriggerInfo getTargetTrigger() {
        return targetTrigger;
    }

    public void setTargetTrigger(TriggerInfo targetTrigger) {
        this.targetTrigger = targetTrigger;
    }

    public String getChangeDetail() {
        return changeDetail;
    }

    public void setChangeDetail(String changeDetail) {
        this.changeDetail = changeDetail;
    }
}
