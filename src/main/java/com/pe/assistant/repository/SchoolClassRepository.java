package com.pe.assistant.repository;

import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findByTeacher(Teacher teacher);
    List<SchoolClass> findByGradeId(Long gradeId);
    boolean existsByNameAndGradeId(String name, Long gradeId);
}
