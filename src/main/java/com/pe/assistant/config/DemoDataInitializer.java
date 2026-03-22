package com.pe.assistant.config;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final CompetitionRepository competitionRepository;
    private final CompetitionEventRepository competitionEventRepository;
    private final CompetitionRegistrationRepository competitionRegistrationRepository;
    private final CompetitionRegistrationItemRepository competitionRegistrationItemRepository;
    private final CompetitionApprovalRecordRepository competitionApprovalRecordRepository;
    private final CompetitionResultRepository competitionResultRepository;
    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;

    @Value("${app.demo.seed-enabled:false}")
    private boolean demoSeedEnabled;

    @Override
    public void run(String... args) {
        if (!demoSeedEnabled) {
            return;
        }
        if (competitionRepository.existsByCode("DEMO-CITY-SPORTS-2026")) {
            return;
        }

        Teacher admin = teacherRepository.findByUsername("admin").orElse(null);
        School school = admin != null ? admin.getSchool() : schoolRepository.findByName("江苏省清江中学").orElse(null);
        if (admin == null || school == null || school.getOrganization() == null) {
            return;
        }

        Organization schoolOrg = school.getOrganization();
        Organization districtOrg = schoolOrg.getParent();
        Organization cityOrg = districtOrg != null ? districtOrg.getParent() : null;
        if (districtOrg == null || cityOrg == null) {
            return;
        }

        Competition competition = new Competition();
        competition.setName("2026 示例市级体育比赛");
        competition.setCode("DEMO-CITY-SPORTS-2026");
        competition.setLevel(CompetitionLevel.CITY);
        competition.setHostOrg(cityOrg);
        competition.setUndertakeOrg(districtOrg);
        competition.setSchoolYear("2025-2026");
        competition.setTerm("SECOND");
        competition.setStatus(CompetitionStatus.UNDER_REVIEW);
        competition.setRegistrationStartAt(LocalDateTime.now().minusDays(15));
        competition.setRegistrationEndAt(LocalDateTime.now().plusDays(15));
        competition.setCompetitionStartAt(LocalDateTime.now().plusDays(30));
        competition.setCompetitionEndAt(LocalDateTime.now().plusDays(32));
        competition.setDescription("用于开发演示的默认赛事数据");
        competition.setCreatedBy(admin);
        competition = competitionRepository.save(competition);

        CompetitionEvent event100m = new CompetitionEvent();
        event100m.setCompetition(competition);
        event100m.setName("男子100米");
        event100m.setEventCode("M100");
        event100m.setGenderLimit("MALE");
        event100m.setGroupRule("DEFAULT");
        event100m.setTeamOrIndividual("INDIVIDUAL");
        event100m.setMaxEntriesPerSchool(2);
        event100m.setMaxEntriesPerDistrict(10);
        event100m.setSortOrder(10);
        event100m = competitionEventRepository.save(event100m);

        CompetitionEvent eventLongJump = new CompetitionEvent();
        eventLongJump.setCompetition(competition);
        eventLongJump.setName("男子跳远");
        eventLongJump.setEventCode("MLJ");
        eventLongJump.setGenderLimit("MALE");
        eventLongJump.setGroupRule("DEFAULT");
        eventLongJump.setTeamOrIndividual("INDIVIDUAL");
        eventLongJump.setMaxEntriesPerSchool(2);
        eventLongJump.setMaxEntriesPerDistrict(10);
        eventLongJump.setSortOrder(20);
        eventLongJump = competitionEventRepository.save(eventLongJump);

        CompetitionRegistration registration = new CompetitionRegistration();
        registration.setCompetition(competition);
        registration.setApplicantOrg(schoolOrg);
        registration.setApplicantSchool(school);
        registration.setStatus(CompetitionRegistrationStatus.CITY_APPROVED);
        registration.setSubmittedBy(admin);
        registration.setSubmittedAt(LocalDateTime.now().minusDays(5));
        registration.setCurrentApprovalLevel("DONE");
        registration.setRemark("示例报名数据");
        registration = competitionRegistrationRepository.save(registration);

        List<Student> students = studentRepository.findBySchoolOrderByStudentNo(school).stream()
                .limit(2)
                .toList();
        if (!students.isEmpty()) {
            CompetitionRegistrationItem item1 = new CompetitionRegistrationItem();
            item1.setRegistration(registration);
            item1.setStudent(students.get(0));
            item1.setCompetitionEvent(event100m);
            item1.setRoleType("ATHLETE");
            item1.setSeedResult("12.31");
            competitionRegistrationItemRepository.save(item1);

            CompetitionResult result1 = new CompetitionResult();
            result1.setCompetition(competition);
            result1.setCompetitionEvent(event100m);
            result1.setStudent(students.get(0));
            result1.setSchool(school);
            result1.setDistrictOrg(districtOrg);
            result1.setResultValue("12.31");
            result1.setRankNo(1);
            result1.setScorePoints(BigDecimal.valueOf(9.0));
            result1.setRecordStatus("PUBLISHED");
            result1.setEnteredBy(admin);
            result1.setVerifiedBy(admin);
            result1.setVerifiedAt(LocalDateTime.now().minusDays(1));
            competitionResultRepository.save(result1);
        }
        if (students.size() > 1) {
            CompetitionRegistrationItem item2 = new CompetitionRegistrationItem();
            item2.setRegistration(registration);
            item2.setStudent(students.get(1));
            item2.setCompetitionEvent(eventLongJump);
            item2.setRoleType("ATHLETE");
            item2.setSeedResult("5.82");
            competitionRegistrationItemRepository.save(item2);
        }

        CompetitionApprovalRecord districtApprove = new CompetitionApprovalRecord();
        districtApprove.setRegistration(registration);
        districtApprove.setApprovalLevel("DISTRICT");
        districtApprove.setApprover(admin);
        districtApprove.setApproverOrg(districtOrg);
        districtApprove.setDecision("APPROVE");
        districtApprove.setComment("示例区县审核通过");
        competitionApprovalRecordRepository.save(districtApprove);

        CompetitionApprovalRecord cityApprove = new CompetitionApprovalRecord();
        cityApprove.setRegistration(registration);
        cityApprove.setApprovalLevel("CITY");
        cityApprove.setApprover(admin);
        cityApprove.setApproverOrg(cityOrg);
        cityApprove.setDecision("APPROVE");
        cityApprove.setComment("示例市级终审通过");
        competitionApprovalRecordRepository.save(cityApprove);
    }
}