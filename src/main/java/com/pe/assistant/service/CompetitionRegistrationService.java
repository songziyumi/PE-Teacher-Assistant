package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.CompetitionApprovalRecordRepository;
import com.pe.assistant.repository.CompetitionRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionRegistrationService {

    private final CompetitionRegistrationRepository registrationRepository;
    private final CompetitionApprovalRecordRepository approvalRecordRepository;
    private final CompetitionService competitionService;
    private final OrganizationScopeService organizationScopeService;
    private final TeacherOperationLogService teacherOperationLogService;

    public List<CompetitionRegistration> listBySchool(Teacher teacher) {
        School school = teacher.getSchool();
        if (school == null || school.getId() == null) {
            return List.of();
        }
        return registrationRepository.findByApplicantSchoolIdOrderByCreatedAtDesc(school.getId());
    }

    public CompetitionRegistration requireVisible(Teacher teacher, Long registrationId) {
        CompetitionRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("报名记录不存在"));
        if (organizationScopeService.canManageSchool(teacher, registration.getApplicantSchool())) {
            return registration;
        }
        throw new IllegalArgumentException("无权查看该报名记录");
    }

    @Transactional
    public CompetitionRegistration createDraft(Teacher teacher, Long competitionId, String remark) {
        Competition competition = competitionService.requireVisible(teacher, competitionId);
        School school = teacher.getSchool();
        if (school == null || school.getOrganization() == null) {
            throw new IllegalArgumentException("当前账号未绑定学校组织");
        }
        if (!organizationScopeService.canManageSchool(teacher, school)) {
            throw new IllegalArgumentException("无权为该学校创建报名");
        }
        if (registrationRepository.findByCompetitionIdAndApplicantSchoolId(competitionId, school.getId()).isPresent()) {
            throw new IllegalArgumentException("该学校已存在报名记录");
        }
        CompetitionRegistration registration = new CompetitionRegistration();
        registration.setCompetition(competition);
        registration.setApplicantOrg(school.getOrganization());
        registration.setApplicantSchool(school);
        registration.setStatus(CompetitionRegistrationStatus.DRAFT);
        registration.setRemark(remark);
        registration.setCurrentApprovalLevel(nextApprovalLevel(competition));
        CompetitionRegistration saved = registrationRepository.save(registration);
        log(teacher, school, "COMPETITION_REGISTRATION_CREATE", "创建赛事报名草稿", 1);
        return saved;
    }

    @Transactional
    public CompetitionRegistration submit(Teacher teacher, Long registrationId) {
        CompetitionRegistration registration = requireVisible(teacher, registrationId);
        if (!isSchoolLevelOperator(teacher, registration)) {
            throw new IllegalArgumentException("仅学校管理员可提交本校报名");
        }
        if (registration.getStatus() != CompetitionRegistrationStatus.DRAFT
                && registration.getStatus() != CompetitionRegistrationStatus.DISTRICT_REJECTED
                && registration.getStatus() != CompetitionRegistrationStatus.CITY_REJECTED) {
            throw new IllegalArgumentException("当前状态不可提交报名");
        }
        registration.setStatus(CompetitionRegistrationStatus.SUBMITTED);
        registration.setSubmittedBy(teacher);
        registration.setSubmittedAt(LocalDateTime.now());
        registration.setCurrentApprovalLevel(nextApprovalLevel(registration.getCompetition()));
        CompetitionRegistration saved = registrationRepository.save(registration);
        log(teacher, registration.getApplicantSchool(), "COMPETITION_REGISTRATION_SUBMIT", "提交赛事报名", 1);
        return saved;
    }

    @Transactional
    public CompetitionRegistration districtReview(Teacher teacher, Long registrationId, String decision, String comment) {
        CompetitionRegistration registration = requireVisible(teacher, registrationId);
        ensureDistrictReviewer(teacher, registration);
        if (registration.getStatus() != CompetitionRegistrationStatus.SUBMITTED) {
            throw new IllegalArgumentException("当前状态不可进行区县审核");
        }
        boolean approved = "APPROVE".equalsIgnoreCase(decision);
        registration.setStatus(approved
                ? CompetitionRegistrationStatus.DISTRICT_APPROVED
                : CompetitionRegistrationStatus.DISTRICT_REJECTED);
        registration.setCurrentApprovalLevel(approved ? "CITY" : "SCHOOL");
        CompetitionRegistration saved = registrationRepository.save(registration);
        saveApprovalRecord(saved, teacher, "DISTRICT", approved ? "APPROVE" : "REJECT", comment);
        log(teacher, registration.getApplicantSchool(), approved ? "COMPETITION_REGISTRATION_DISTRICT_APPROVE" : "COMPETITION_REGISTRATION_DISTRICT_REJECT", approved ? "区县审核通过" : "区县审核驳回", 1);
        return saved;
    }

    @Transactional
    public CompetitionRegistration cityReview(Teacher teacher, Long registrationId, String decision, String comment) {
        CompetitionRegistration registration = requireVisible(teacher, registrationId);
        ensureCityReviewer(teacher, registration);
        if (registration.getStatus() != CompetitionRegistrationStatus.DISTRICT_APPROVED) {
            throw new IllegalArgumentException("当前状态不可进行市级终审");
        }
        boolean approved = "APPROVE".equalsIgnoreCase(decision);
        registration.setStatus(approved
                ? CompetitionRegistrationStatus.CITY_APPROVED
                : CompetitionRegistrationStatus.CITY_REJECTED);
        registration.setCurrentApprovalLevel(approved ? "DONE" : "SCHOOL");
        CompetitionRegistration saved = registrationRepository.save(registration);
        saveApprovalRecord(saved, teacher, "CITY", approved ? "APPROVE" : "REJECT", comment);
        log(teacher, registration.getApplicantSchool(), approved ? "COMPETITION_REGISTRATION_CITY_APPROVE" : "COMPETITION_REGISTRATION_CITY_REJECT", approved ? "市级终审通过" : "市级终审驳回", 1);
        return saved;
    }

    public List<CompetitionApprovalRecord> listApprovalRecords(Long registrationId) {
        return approvalRecordRepository.findByRegistrationIdOrderByCreatedAtAsc(registrationId);
    }

    public Map<String, Object> toMap(CompetitionRegistration registration) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", registration.getId());
        m.put("competitionId", registration.getCompetition() != null ? registration.getCompetition().getId() : null);
        m.put("competitionName", registration.getCompetition() != null ? registration.getCompetition().getName() : null);
        m.put("applicantOrgId", registration.getApplicantOrg() != null ? registration.getApplicantOrg().getId() : null);
        m.put("applicantOrgName", registration.getApplicantOrg() != null ? registration.getApplicantOrg().getName() : null);
        m.put("applicantSchoolId", registration.getApplicantSchool() != null ? registration.getApplicantSchool().getId() : null);
        m.put("applicantSchoolName", registration.getApplicantSchool() != null ? registration.getApplicantSchool().getName() : null);
        m.put("status", registration.getStatus());
        m.put("submittedBy", registration.getSubmittedBy() != null ? registration.getSubmittedBy().getName() : null);
        m.put("submittedAt", registration.getSubmittedAt());
        m.put("currentApprovalLevel", registration.getCurrentApprovalLevel());
        m.put("remark", registration.getRemark());
        return m;
    }

    public Map<String, Object> toApprovalMap(CompetitionApprovalRecord record) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", record.getId());
        m.put("registrationId", record.getRegistration() != null ? record.getRegistration().getId() : null);
        m.put("approvalLevel", record.getApprovalLevel());
        m.put("approverId", record.getApprover() != null ? record.getApprover().getId() : null);
        m.put("approverName", record.getApprover() != null ? record.getApprover().getName() : null);
        m.put("approverOrgId", record.getApproverOrg() != null ? record.getApproverOrg().getId() : null);
        m.put("approverOrgName", record.getApproverOrg() != null ? record.getApproverOrg().getName() : null);
        m.put("decision", record.getDecision());
        m.put("comment", record.getComment());
        m.put("createdAt", record.getCreatedAt());
        return m;
    }

    private boolean isSchoolLevelOperator(Teacher teacher, CompetitionRegistration registration) {
        return teacher != null
                && teacher.getSchool() != null
                && registration.getApplicantSchool() != null
                && teacher.getSchool().getId().equals(registration.getApplicantSchool().getId());
    }

    private void ensureDistrictReviewer(Teacher teacher, CompetitionRegistration registration) {
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        Organization schoolOrg = registration.getApplicantOrg();
        Organization districtOrg = schoolOrg != null ? schoolOrg.getParent() : null;
        if (managedOrg == null || districtOrg == null || !managedOrg.getId().equals(districtOrg.getId())) {
            throw new IllegalArgumentException("仅对应区县管理员可审核该报名");
        }
    }

    private void ensureCityReviewer(Teacher teacher, CompetitionRegistration registration) {
        Organization managedOrg = organizationScopeService.resolveManagedOrg(teacher);
        Organization schoolOrg = registration.getApplicantOrg();
        Organization districtOrg = schoolOrg != null ? schoolOrg.getParent() : null;
        Organization cityOrg = districtOrg != null ? districtOrg.getParent() : null;
        if (managedOrg == null || cityOrg == null || !managedOrg.getId().equals(cityOrg.getId())) {
            throw new IllegalArgumentException("仅对应市级管理员可终审该报名");
        }
    }

    private void saveApprovalRecord(CompetitionRegistration registration, Teacher teacher, String level, String decision, String comment) {
        CompetitionApprovalRecord record = new CompetitionApprovalRecord();
        record.setRegistration(registration);
        record.setApprovalLevel(level);
        record.setApprover(teacher);
        record.setApproverOrg(organizationScopeService.resolveManagedOrg(teacher));
        record.setDecision(decision);
        record.setComment(comment);
        approvalRecordRepository.save(record);
    }

    private String nextApprovalLevel(Competition competition) {
        return switch (competition.getLevel()) {
            case CITY -> "DISTRICT";
            case DISTRICT -> "DISTRICT";
            case SCHOOL -> "SCHOOL";
        };
    }

    private void log(Teacher teacher, School school, String action, String description, int count) {
        teacherOperationLogService.log(teacher.getId(), teacher.getName(), school, action, description, count);
    }
}