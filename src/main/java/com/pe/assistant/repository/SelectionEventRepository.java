package com.pe.assistant.repository;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SelectionEventRepository extends JpaRepository<SelectionEvent, Long> {
    List<SelectionEvent> findBySchoolOrderByCreatedAtDesc(School school);

    List<SelectionEvent> findByStatusOrderByCreatedAtAsc(String status);
}
