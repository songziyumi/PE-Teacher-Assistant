package com.pe.assistant.repository;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseClassCapacity;
import com.pe.assistant.entity.SchoolClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface CourseClassCapacityRepository extends JpaRepository<CourseClassCapacity, Long> {
    List<CourseClassCapacity> findByCourse(Course course);

    Optional<CourseClassCapacity> findByCourseAndSchoolClass(Course course, SchoolClass schoolClass);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CourseClassCapacity c WHERE c.course.id = :courseId AND c.schoolClass.id = :classId")
    Optional<CourseClassCapacity> findByCourseIdAndClassIdForUpdate(
            @Param("courseId") Long courseId,
            @Param("classId") Long classId);

    void deleteByCourse(Course course);
}
