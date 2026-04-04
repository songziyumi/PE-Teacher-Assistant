package com.pe.assistant.entity;

public enum OrganizationAdminType {
    CITY("市级"),
    DISTRICT("县区"),
    SCHOOL("学校");

    private final String label;

    OrganizationAdminType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
