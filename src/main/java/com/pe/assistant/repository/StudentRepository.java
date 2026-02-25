package com.pe.assistant.repository;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findBySchoolClassId(Long classId);
    long countBySchoolClassId(Long classId);
    List<Student> findByElectiveClass(String electiveClass);

    @Query("SELECT DISTINCT s.electiveClass FROM Student s WHERE s.schoolClass.teacher = :teacher AND s.electiveClass IS NOT NULL AND s.electiveClass <> ''")
    List<String> findElectiveClassNamesByTeacher(@Param("teacher") Teacher teacher);

    @Query("SELECT DISTINCT s.electiveClass FROM Student s WHERE s.electiveClass IS NOT NULL AND s.electiveClass <> ''")
    List<String> findAllElectiveClassNames();
}
