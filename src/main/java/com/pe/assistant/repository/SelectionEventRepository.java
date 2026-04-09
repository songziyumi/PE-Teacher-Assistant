package com.pe.assistant.repository;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SelectionEventRepository extends JpaRepository<SelectionEvent, Long> {
    List<SelectionEvent> findBySchoolOrderByCreatedAtDesc(School school);

    List<SelectionEvent> findByStatusOrderByCreatedAtAsc(String status);

    @Modifying
    @Query("""
            UPDATE SelectionEvent e
               SET e.status = 'PROCESSING',
                   e.lotteryNote = :lotteryNote
             WHERE e.id = :eventId
               AND e.status = 'ROUND1'
            """)
    int markProcessingIfRound1(@Param("eventId") Long eventId,
                               @Param("lotteryNote") String lotteryNote);
}
