package com.pe.assistant.repository;

import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {
    List<SchoolClass> findByTeacher(Teacher teacher);
    List<SchoolClass> findByTeacherAndType(Teacher teacher, String type);
    List<SchoolClass> findByGradeId(Long gradeId);
    boolean existsByNameAndGradeId(String name, Long gradeId);

    @Query("SELECT c FROM SchoolClass c LEFT JOIN c.grade g WHERE " +
           "c.name LIKE CONCAT('%', :keyword, '%') OR g.name LIKE CONCAT('%', :keyword, '%')")
    Page<SchoolClass> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT c FROM SchoolClass c LEFT JOIN c.grade g WHERE " +
           "(:type IS NULL OR :type = '' OR c.type = :type) AND " +
           "(:gradeId IS NULL OR g.id = :gradeId) AND " +
           "(:name IS NULL OR :name = '' OR c.name LIKE CONCAT('%', :name, '%'))")
    Page<SchoolClass> findByFilters(@Param("type") String type,
                                    @Param("gradeId") Long gradeId,
                                    @Param("name") String name,
                                    Pageable pageable);
}
