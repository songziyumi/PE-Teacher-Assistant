package com.pe.assistant.repository;

import com.pe.assistant.entity.ExamRecord;
import com.pe.assistant.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExamRecordRepository extends JpaRepository<ExamRecord, Long> {
    
    List<ExamRecord> findByStudent(Student student);
    
    List<ExamRecord> findByStudentAndExamDateBetween(Student student, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT e FROM ExamRecord e WHERE e.student IN :students AND e.examName = :examName")
    List<ExamRecord> findByStudentsAndExamName(@Param("students") List<Student> students, @Param("examName") String examName);
    
    @Query("SELECT e FROM ExamRecord e WHERE e.student.schoolClass.id = :classId ORDER BY e.examDate DESC")
    List<ExamRecord> findByClassId(@Param("classId") Long classId);
    
    @Query("SELECT e FROM ExamRecord e WHERE e.student.schoolClass.grade.id = :gradeId ORDER BY e.examDate DESC")
    List<ExamRecord> findByGradeId(@Param("gradeId") Long gradeId);
    
    @Query("SELECT e FROM ExamRecord e WHERE e.examName = :examName AND e.student.schoolClass.id = :classId ORDER BY e.totalScore DESC")
    List<ExamRecord> findByExamNameAndClassIdOrderByScore(@Param("examName") String examName, @Param("classId") Long classId);
}