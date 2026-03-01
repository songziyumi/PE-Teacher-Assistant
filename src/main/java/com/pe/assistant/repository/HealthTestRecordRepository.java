package com.pe.assistant.repository;

import com.pe.assistant.entity.HealthTestRecord;
import com.pe.assistant.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HealthTestRecordRepository extends JpaRepository<HealthTestRecord, Long> {
    
    List<HealthTestRecord> findByStudent(Student student);
    
    List<HealthTestRecord> findByStudentAndTestDateBetween(Student student, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT h FROM HealthTestRecord h WHERE h.student IN :students AND h.testDate = :testDate")
    List<HealthTestRecord> findByStudentsAndTestDate(@Param("students") List<Student> students, @Param("testDate") LocalDate testDate);
    
    @Query("SELECT h FROM HealthTestRecord h WHERE h.student.schoolClass.id = :classId ORDER BY h.testDate DESC")
    List<HealthTestRecord> findByClassId(@Param("classId") Long classId);
    
    @Query("SELECT h FROM HealthTestRecord h WHERE h.student.schoolClass.grade.id = :gradeId ORDER BY h.testDate DESC")
    List<HealthTestRecord> findByGradeId(@Param("gradeId") Long gradeId);
}