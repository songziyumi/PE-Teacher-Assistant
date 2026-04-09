package com.pe.assistant.entity;

public enum CompetitionStatus {
    DRAFT("草稿"),
    REGISTRATION_OPEN("报名中"),
    REGISTRATION_CLOSED("报名截止"),
    UNDER_REVIEW("审核中"),
    READY("待开始"),
    ONGOING("进行中"),
    FINISHED("已结束"),
    ARCHIVED("已归档");

    private final String label;

    CompetitionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}