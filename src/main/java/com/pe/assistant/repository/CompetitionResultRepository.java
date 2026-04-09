package com.pe.assistant.repository;

import com.pe.assistant.entity.CompetitionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompetitionResultRepository extends JpaRepository<CompetitionResult, Long> {
    List<CompetitionResult> findByCompetitionIdOrderByCompetitionEventIdAscRankNoAscIdAsc(Long competitionId);
    List<CompetitionResult> findByCompetitionIdAndCompetitionEventIdOrderByRankNoAscIdAsc(Long competitionId, Long competitionEventId);
    Optional<CompetitionResult> findByCompetitionIdAndCompetitionEventIdAndStudentId(Long competitionId, Long competitionEventId, Long studentId);
}