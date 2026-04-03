package com.pe.assistant.repository;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SelectionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByEventOrderByNameAsc(SelectionEvent event);

    List<Course> findByEventAndStatusOrderByNameAsc(SelectionEvent event, String status);

    List<Course> findBySchoolAndTeacherIsNotNullOrderByNameAsc(School school);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Course c WHERE c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE Course c
               SET c.currentCount = c.currentCount + 1
             WHERE c.id = :id
               AND c.currentCount < c.totalCapacity
            """)
    int incrementCurrentCountIfAvailable(@Param("id") Long id);

    @Modifying
    @Query("""
            UPDATE Course c
               SET c.currentCount = c.currentCount + 1
             WHERE c.id = :id
            """)
    int incrementCurrentCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Course c SET c.currentCount = 0 WHERE c.school = :school")
    void resetCountsBySchool(@Param("school") School school);
}
