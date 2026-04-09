package com.pe.assistant.repository;

import com.pe.assistant.entity.CompetitionRegistrationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRegistrationItemRepository extends JpaRepository<CompetitionRegistrationItem, Long> {
    List<CompetitionRegistrationItem> findByRegistrationIdOrderByIdAsc(Long registrationId);
    Optional<CompetitionRegistrationItem> findByRegistrationIdAndStudentIdAndCompetitionEventId(Long registrationId, Long studentId, Long competitionEventId);
}