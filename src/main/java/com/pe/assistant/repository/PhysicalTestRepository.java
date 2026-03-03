package com.pe.assistant.repository;

import com.pe.assistant.entity.PhysicalTest;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PhysicalTestRepository extends JpaRepository<PhysicalTest, Long> {

    Optional<PhysicalTest> findByStudentAndAcademicYearAndSemester(
            Student student, String academicYear, String semester);

    @Query(value = "SELECT DISTINCT pt FROM PhysicalTest pt " +
           "LEFT JOIN pt.student s LEFT JOIN s.schoolClass sc LEFT JOIN sc.grade g WHERE " +
           "pt.school = :school AND " +
           "(:classId IS NULL OR sc.id = :classId) AND " +
           "(:gradeId IS NULL OR g.id = :gradeId) AND " +
           "(:academicYear IS NULL OR :academicYear = '' OR pt.academicYear = :academicYear) AND " +
           "(:semester IS NULL OR :semester = '' OR pt.semester = :semester) AND " +
           "(:keyword IS NULL OR :keyword = '' OR s.name LIKE CONCAT('%', :keyword, '%') OR s.studentNo LIKE CONCAT('%', :keyword, '%'))",
           countQuery = "SELECT COUNT(DISTINCT pt) FROM PhysicalTest pt " +
           "LEFT JOIN pt.student s LEFT JOIN s.schoolClass sc LEFT JOIN sc.grade g WHERE " +
           "pt.school = :school AND " +
           "(:classId IS NULL OR sc.id = :classId) AND " +
           "(:gradeId IS NULL OR g.id = :gradeId) AND " +
           "(:academicYear IS NULL OR :academicYear = '' OR pt.academicYear = :academicYear) AND " +
           "(:semester IS NULL OR :semester = '' OR pt.semester = :semester) AND " +
           "(:keyword IS NULL OR :keyword = '' OR s.name LIKE CONCAT('%', :keyword, '%') OR s.studentNo LIKE CONCAT('%', :keyword, '%'))")
    Page<PhysicalTest> findWithFilters(@Param("school") School school,
                                       @Param("classId") Long classId,
                                       @Param("gradeId") Long gradeId,
                                       @Param("academicYear") String academicYear,
                                       @Param("semester") String semester,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    @Query("SELECT DISTINCT pt.academicYear FROM PhysicalTest pt WHERE pt.school = :school ORDER BY pt.academicYear DESC")
    List<String> findDistinctAcademicYears(@Param("school") School school);

    @Modifying
    @Query("DELETE FROM PhysicalTest pt WHERE pt.school = :school")
    void deleteAllBySchool(@Param("school") School school);

    long countBySchool(School school);
}
