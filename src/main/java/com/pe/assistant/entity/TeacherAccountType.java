package com.pe.assistant.entity;

public enum TeacherAccountType {
    TEACHER("教师"),
    SCHOOL_ADMIN("学校管理员"),
    ORG_ADMIN("组织管理员"),
    SUPER_ADMIN("超级管理员");

    private final String label;

    TeacherAccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
