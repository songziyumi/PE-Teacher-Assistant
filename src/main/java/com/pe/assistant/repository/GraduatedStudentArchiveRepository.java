package com.pe.assistant.repository;

import com.pe.assistant.entity.GraduatedStudentArchive;
import com.pe.assistant.entity.School;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface GraduatedStudentArchiveRepository extends JpaRepository<GraduatedStudentArchive, Long> {
    @Query("select a from GraduatedStudentArchive a where a.student.school = :school and (:year is null or a.graduationYear = :year) order by a.graduationYear desc, a.student.studentNo")
    List<GraduatedStudentArchive> findBySchoolAndYear(@Param("school") School school, @Param("year") Integer year);
    boolean existsByStudentIdAndGraduationYear(Long studentId, Integer graduationYear);
}
