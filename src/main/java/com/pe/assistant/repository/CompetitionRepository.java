package com.pe.assistant.repository;

import com.pe.assistant.entity.Competition;
import com.pe.assistant.entity.CompetitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Optional<Competition> findByCode(String code);
    boolean existsByCode(String code);
    List<Competition> findByHostOrgIdInOrderByCreatedAtDesc(List<Long> hostOrgIds);
    List<Competition> findByHostOrgIdInAndStatusOrderByCreatedAtDesc(List<Long> hostOrgIds, CompetitionStatus status);
}