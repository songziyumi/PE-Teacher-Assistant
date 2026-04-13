package com.pe.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teachers", indexes = {
        @Index(name = "idx_teacher_email", columnList = "email")
})
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 30)
    private TeacherAccountType accountType = TeacherAccountType.TEACHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_admin_type", length = 20)
    private OrganizationAdminType orgAdminType;

    @Column(length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id")
    private School school;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "managed_org_id")
    private Organization managedOrg;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 100)
    private String specialty;

    @Column(length = 100)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    @Column(name = "email_bound_at")
    private LocalDateTime emailBoundAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "email_notify_enabled", nullable = false)
    private Boolean emailNotifyEnabled = true;

    @Column(name = "photo_url", length = 200)
    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    public TeacherAccountType resolveAccountType() {
        if (accountType != null) {
            return accountType;
        }
        if ("SUPER_ADMIN".equals(role)) {
            return TeacherAccountType.SUPER_ADMIN;
        }
        if ("ADMIN".equals(role)) {
            return TeacherAccountType.SCHOOL_ADMIN;
        }
        if (name != null && name.contains("组织管理员")) {
            return TeacherAccountType.ORG_ADMIN;
        }
        return TeacherAccountType.TEACHER;
    }

    public OrganizationAdminType resolveOrgAdminType() {
        if (orgAdminType != null) {
            return orgAdminType;
        }
        if (resolveAccountType() != TeacherAccountType.ORG_ADMIN || name == null) {
            return null;
        }
        if (name.contains("市级")) {
            return OrganizationAdminType.CITY;
        }
        if (name.contains("县区") || name.contains("区县")) {
            return OrganizationAdminType.DISTRICT;
        }
        if (name.contains("学校")) {
            return OrganizationAdminType.SCHOOL;
        }
        return null;
    }

    public boolean isCourseAssignableTeacher() {
        return resolveAccountType() == TeacherAccountType.TEACHER;
    }

    public String getAccountTypeDisplay() {
        TeacherAccountType resolvedType = resolveAccountType();
        if (resolvedType == TeacherAccountType.ORG_ADMIN) {
            OrganizationAdminType resolvedOrgAdminType = resolveOrgAdminType();
            return resolvedOrgAdminType == null
                    ? resolvedType.getLabel()
                    : resolvedOrgAdminType.getLabel() + resolvedType.getLabel();
        }
        return resolvedType.getLabel();
    }
}
