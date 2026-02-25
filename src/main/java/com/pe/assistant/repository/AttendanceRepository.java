package com.pe.assistant.repository;

import com.pe.assistant.entity.Attendance;
import com.pe.assistant.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findByStudentOrderByDateDesc(Student student);
    Optional<Attendance> findByStudentAndDate(Student student, LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.student.schoolClass.id = :classId AND a.date = :date")
    List<Attendance> findByClassIdAndDate(@Param("classId") Long classId, @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.student.schoolClass.id = :classId")
    List<Attendance> findByClassId(@Param("classId") Long classId);

    @Query("SELECT a FROM Attendance a WHERE a.status = '缺勤' AND a.date BETWEEN :start AND :end")
    List<Attendance> findAbsentBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student.id = :studentId AND a.status = :status")
    long countByStudentIdAndStatus(@Param("studentId") Long studentId, @Param("status") String status);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student.schoolClass.id = :classId AND a.status = :status")
    long countByClassIdAndStatus(@Param("classId") Long classId, @Param("status") String status);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student.schoolClass.id = :classId")
    long countByClassId(@Param("classId") Long classId);

    @Query("SELECT a FROM Attendance a WHERE a.student.electiveClass = :name AND a.date = :date")
    List<Attendance> findByElectiveClassAndDate(@Param("name") String name, @Param("date") LocalDate date);

    @Query("SELECT a FROM Attendance a WHERE a.student.electiveClass = :name")
    List<Attendance> findByElectiveClass(@Param("name") String name);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student.electiveClass = :name AND a.status = :status")
    long countByElectiveClassAndStatus(@Param("name") String name, @Param("status") String status);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.student.electiveClass = :name")
    long countByElectiveClass(@Param("name") String name);
}
