package com.pe.assistant.repository;

import com.pe.assistant.entity.CompetitionApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitionApprovalRecordRepository extends JpaRepository<CompetitionApprovalRecord, Long> {
    List<CompetitionApprovalRecord> findByRegistrationIdOrderByCreatedAtAsc(Long registrationId);
}