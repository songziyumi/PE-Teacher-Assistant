package com.pe.assistant.repository;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findBySchoolClassIdOrderByStudentNo(Long classId);
    long countBySchoolClassId(Long classId);
    java.util.Optional<Student> findByStudentNo(String studentNo);
    java.util.Optional<Student> findByStudentNoAndSchool(String studentNo, School school);

    @Query(value = "SELECT DISTINCT s FROM Student s LEFT JOIN s.schoolClass sc LEFT JOIN sc.grade g WHERE " +
           "s.school = :school AND " +
           "(:classId IS NULL OR sc.id = :classId) AND " +
           "(:gradeId IS NULL OR g.id = :gradeId) AND " +
           "(:name IS NULL OR :name = '' OR s.name LIKE CONCAT('%', :name, '%')) AND " +
           "(:studentNo IS NULL OR :studentNo = '' OR s.studentNo LIKE CONCAT('%', :studentNo, '%')) AND " +
           "(:idCard IS NULL OR :idCard = '' OR s.idCard LIKE CONCAT('%', :idCard, '%')) AND " +
           "(:electiveClass IS NULL OR :electiveClass = '' OR s.electiveClass = :electiveClass)",
           countQuery = "SELECT COUNT(DISTINCT s) FROM Student s LEFT JOIN s.schoolClass sc LEFT JOIN sc.grade g WHERE " +
           "s.school = :school AND " +
           "(:classId IS NULL OR sc.id = :classId) AND " +
           "(:gradeId IS NULL OR g.id = :gradeId) AND " +
           "(:name IS NULL OR :name = '' OR s.name LIKE CONCAT('%', :name, '%')) AND " +
           "(:studentNo IS NULL OR :studentNo = '' OR s.studentNo LIKE CONCAT('%', :studentNo, '%')) AND " +
           "(:idCard IS NULL OR :idCard = '' OR s.idCard LIKE CONCAT('%', :idCard, '%')) AND " +
           "(:electiveClass IS NULL OR :electiveClass = '' OR s.electiveClass = :electiveClass)")
    Page<Student> findWithFilters(@Param("school") School school,
                                  @Param("classId") Long classId,
                                  @Param("gradeId") Long gradeId,
                                  @Param("name") String name,
                                  @Param("studentNo") String studentNo,
                                  @Param("idCard") String idCard,
                                  @Param("electiveClass") String electiveClass,
                                  Pageable pageable);

    List<Student> findByElectiveClassOrderByStudentNo(String electiveClass);
    List<Student> findByElectiveClassInOrderByStudentNo(List<String> names);

    @Query("SELECT DISTINCT s.electiveClass FROM Student s WHERE s.school = :school AND s.schoolClass.teacher = :teacher AND s.electiveClass IS NOT NULL AND s.electiveClass <> ''")
    List<String> findElectiveClassNamesByTeacher(@Param("school") School school, @Param("teacher") Teacher teacher);

    @Query("SELECT DISTINCT s.electiveClass FROM Student s WHERE s.school = :school AND s.electiveClass IS NOT NULL AND s.electiveClass <> ''")
    List<String> findAllElectiveClassNames(@Param("school") School school);
}
