package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

    public List<Attendance> findByElectiveClassAndDate(School school, String name, LocalDate date) {
        if (school == null) {
            return findByElectiveClassAndDate(name, date);
        }
        return attendanceRepository.findBySchoolAndElectiveClassAndDate(school, name, date);
    }

    public List<Attendance> findByElectiveClassInAndDate(List<String> names, LocalDate date) {
        return attendanceRepository.findByElectiveClassInAndDate(names, date);
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

    @Transactional
    public void deleteAllBySchool(School school) {
        attendanceRepository.deleteAllBySchool(school);
    }

    public List<Attendance> findByClassIdsAndDateRange(List<Long> classIds, LocalDate start, LocalDate end) {
        return attendanceRepository.findByClassIdsAndDateRange(classIds, start, end);
    }

    public List<Attendance> findBySchoolAndFilters(School school, LocalDate start, LocalDate end, Long gradeId, Long classId, String status) {
        return attendanceRepository.findBySchoolAndFilters(school, start, end, gradeId, classId, status);
    }

    public byte[] exportXlsx(List<Attendance> records) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("考勤记录");
            CellStyle headerStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] cols = {"日期", "学号", "姓名", "年级", "班级", "考勤状态"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Attendance a : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(a.getDate() != null ? a.getDate().toString() : "");
                row.createCell(1).setCellValue(a.getStudent().getStudentNo() != null ? a.getStudent().getStudentNo() : "");
                row.createCell(2).setCellValue(a.getStudent().getName());
                SchoolClass sc = a.getStudent().getSchoolClass();
                row.createCell(3).setCellValue(sc != null && sc.getGrade() != null ? sc.getGrade().getName() : "");
                row.createCell(4).setCellValue(sc != null ? sc.getName() : "");
                row.createCell(5).setCellValue(a.getStatus() != null ? a.getStatus() : "");
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
