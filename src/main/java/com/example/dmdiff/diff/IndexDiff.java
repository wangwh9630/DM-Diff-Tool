package com.example.dmdiff.diff;

import com.example.dmdiff.metadata.IndexInfo;

public class IndexDiff {
    private String indexName;
    private DiffType diffType;
    private IndexInfo sourceIndex;
    private IndexInfo targetIndex;

    public IndexDiff() {}

    public IndexDiff(String indexName, DiffType diffType) {
        this.indexName = indexName;
        this.diffType = diffType;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    public IndexInfo getSourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(IndexInfo sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public IndexInfo getTargetIndex() {
        return targetIndex;
    }

    public void setTargetIndex(IndexInfo targetIndex) {
        this.targetIndex = targetIndex;
    }
}