package com.example.dmdiff.diff;

import com.example.dmdiff.metadata.ForeignKeyInfo;

public class ForeignKeyDiff {
    private String constraintName;
    private DiffType diffType;
    private ForeignKeyInfo sourceFk;
    private ForeignKeyInfo targetFk;
    private String changeDetail;

    public ForeignKeyDiff() {}

    public ForeignKeyDiff(String constraintName, DiffType diffType) {
        this.constraintName = constraintName;
        this.diffType = diffType;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    public ForeignKeyInfo getSourceFk() {
        return sourceFk;
    }

    public void setSourceFk(ForeignKeyInfo sourceFk) {
        this.sourceFk = sourceFk;
    }

    public ForeignKeyInfo getTargetFk() {
        return targetFk;
    }

    public void setTargetFk(ForeignKeyInfo targetFk) {
        this.targetFk = targetFk;
    }

    public String getChangeDetail() {
        return changeDetail;
    }

    public void setChangeDetail(String changeDetail) {
        this.changeDetail = changeDetail;
    }
}
