package com.pe.assistant.repository;

import com.pe.assistant.entity.EventStudent;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface EventStudentRepository extends JpaRepository<EventStudent, Long> {
    List<EventStudent> findByEvent(SelectionEvent event);

    Optional<EventStudent> findByEventAndStudent(SelectionEvent event, Student student);

    boolean existsByEventAndStudent(SelectionEvent event, Student student);

    void deleteByStudent(Student student);

    void deleteByEvent(SelectionEvent event);

    boolean existsByEvent(SelectionEvent event);

    @Query("SELECT es.student FROM EventStudent es WHERE es.event = :event")
    List<Student> findStudentsByEvent(@Param("event") SelectionEvent event);

    @Modifying
    @Query("DELETE FROM EventStudent es WHERE es.student.school = :school")
    void deleteAllBySchool(@Param("school") School school);
}
