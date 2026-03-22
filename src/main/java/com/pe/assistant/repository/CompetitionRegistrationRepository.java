package com.pe.assistant.repository;

import com.pe.assistant.entity.CompetitionRegistration;
import com.pe.assistant.entity.CompetitionRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRegistrationRepository extends JpaRepository<CompetitionRegistration, Long> {
    Optional<CompetitionRegistration> findByCompetitionIdAndApplicantSchoolId(Long competitionId, Long schoolId);
    List<CompetitionRegistration> findByApplicantSchoolIdOrderByCreatedAtDesc(Long schoolId);
    List<CompetitionRegistration> findByCompetitionIdAndStatusOrderByCreatedAtDesc(Long competitionId, CompetitionRegistrationStatus status);
}