package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public List<Attendance> findByStudent(Long studentId) {
        Student s = studentRepository.findById(studentId).orElseThrow();
        return attendanceRepository.findByStudentOrderByDateDesc(s);
    }

    public List<Attendance> findByClassAndDate(Long classId, LocalDate date) {
        return attendanceRepository.findByClassIdAndDate(classId, date);
    }

    @Transactional
    public void saveAttendance(Long classId, LocalDate date, Map<Long, String> statusMap, String username) {
        saveAttendanceByUsername(date, statusMap, username);
    }

    @Transactional
    public void saveAttendanceByUsername(LocalDate date, Map<Long, String> statusMap, String username) {
        Teacher teacher = teacherRepository.findByUsername(username).orElseThrow();
        for (Map.Entry<Long, String> entry : statusMap.entrySet()) {
            Student student = studentRepository.findById(entry.getKey()).orElse(null);
            if (student == null) continue;
            Attendance a = attendanceRepository.findByStudentAndDate(student, date)
                .orElse(new Attendance());
            a.setStudent(student);
            a.setDate(date);
            a.setStatus(entry.getValue());
            a.setTeacher(teacher);
            attendanceRepository.save(a);
        }
    }

    @Transactional
    public void updateRecord(Long id, String status) {
        Attendance a = attendanceRepository.findById(id).orElseThrow();
        a.setStatus(status);
        attendanceRepository.save(a);
    }

    public Map<String, Object> getClassStats(Long classId) {
        long total = attendanceRepository.countByClassId(classId);
        long present = attendanceRepository.countByClassIdAndStatus(classId, "出勤");
        long absent = attendanceRepository.countByClassIdAndStatus(classId, "缺勤");
        long leave = attendanceRepository.countByClassIdAndStatus(classId, "请假");
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("leave", leave);
        stats.put("rate", total > 0 ? String.format("%.1f", present * 100.0 / total) : "0.0");
        return stats;
    }

    public Map<String, Object> getStudentStats(Long studentId) {
        long present = attendanceRepository.countByStudentIdAndStatus(studentId, "出勤");
        long absent = attendanceRepository.countByStudentIdAndStatus(studentId, "缺勤");
        long leave = attendanceRepository.countByStudentIdAndStatus(studentId, "请假");
        long total = present + absent + leave;
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("leave", leave);
        stats.put("rate", total > 0 ? String.format("%.1f", present * 100.0 / total) : "0.0");
        return stats;
    }

    public List<Attendance> findByElectiveClassAndDate(String name, LocalDate date) {
        return attendanceRepository.findByElectiveClassAndDate(name, date);
    }

    public Map<String, Object> getElectiveClassStats(String name) {
        long total = attendanceRepository.countByElectiveClass(name);
        long present = attendanceRepository.countByElectiveClassAndStatus(name, "出勤");
        long absent = attendanceRepository.countByElectiveClassAndStatus(name, "缺勤");
        long leave = attendanceRepository.countByElectiveClassAndStatus(name, "请假");
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("present", present);
        stats.put("absent", absent);
        stats.put("leave", leave);
        stats.put("rate", total > 0 ? String.format("%.1f", present * 100.0 / total) : "0.0");
        return stats;
    }

    public List<Attendance> findAbsentBetween(School school, LocalDate start, LocalDate end) {
        return attendanceRepository.findAbsentBetween(school, start, end);
    }

    public List<Attendance> findAbsentOrLeaveBetween(School school, LocalDate start, LocalDate end) {
        return attendanceRepository.findAbsentOrLeaveBetween(school, start, end);
    }
}
