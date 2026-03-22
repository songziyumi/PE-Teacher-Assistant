package com.pe.assistant.repository;

import com.pe.assistant.entity.CompetitionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitionEventRepository extends JpaRepository<CompetitionEvent, Long> {
    List<CompetitionEvent> findByCompetitionIdOrderBySortOrderAscIdAsc(Long competitionId);
}