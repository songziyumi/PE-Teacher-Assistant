package com.pe.assistant.repository;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.TermGrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TermGradeRepository extends JpaRepository<TermGrade, Long> {

    @Query(value = "SELECT DISTINCT t FROM TermGrade t " +
                   "WHERE t.school = :school " +
                   "AND (:classId IS NULL OR t.student.schoolClass.id = :classId) " +
                   "AND (:gradeId IS NULL OR t.student.schoolClass.grade.id = :gradeId) " +
                   "AND (:academicYear = '' OR t.academicYear = :academicYear) " +
                   "AND (:semester = '' OR t.semester = :semester) " +
                   "AND (:keyword = '' OR t.student.name LIKE CONCAT('%',:keyword,'%') " +
                   "  OR t.student.studentNo LIKE CONCAT('%',:keyword,'%'))",
           countQuery = "SELECT COUNT(DISTINCT t) FROM TermGrade t " +
                        "WHERE t.school = :school " +
                        "AND (:classId IS NULL OR t.student.schoolClass.id = :classId) " +
                        "AND (:gradeId IS NULL OR t.student.schoolClass.grade.id = :gradeId) " +
                        "AND (:academicYear = '' OR t.academicYear = :academicYear) " +
                        "AND (:semester = '' OR t.semester = :semester) " +
                        "AND (:keyword = '' OR t.student.name LIKE CONCAT('%',:keyword,'%') " +
                        "  OR t.student.studentNo LIKE CONCAT('%',:keyword,'%'))")
    Page<TermGrade> findWithFilters(@Param("school") School school,
                                    @Param("classId") Long classId,
                                    @Param("gradeId") Long gradeId,
                                    @Param("academicYear") String academicYear,
                                    @Param("semester") String semester,
                                    @Param("keyword") String keyword,
                                    Pageable pageable);

    Optional<TermGrade> findByStudentAndAcademicYearAndSemester(
            Student student, String academicYear, String semester);

    @Query("SELECT DISTINCT t.academicYear FROM TermGrade t WHERE t.school = :school ORDER BY t.academicYear DESC")
    List<String> findDistinctAcademicYears(@Param("school") School school);

    @Modifying
    @Query("DELETE FROM TermGrade t WHERE t.school = :school")
    void deleteAllBySchool(@Param("school") School school);
}
