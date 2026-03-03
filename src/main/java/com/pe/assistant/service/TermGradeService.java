package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TermGradeRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TermGradeService {

    private final TermGradeRepository termGradeRepository;
    private final StudentRepository studentRepository;

    // ===== 查询 =====

    public Page<TermGrade> findWithFilters(School school, Long classId, Long gradeId,
                                           String academicYear, String semester,
                                           String keyword, int page, int size) {
        return termGradeRepository.findWithFilters(
                school, classId, gradeId, academicYear, semester, keyword,
                PageRequest.of(page, size));
    }

    public Optional<TermGrade> findById(Long id) {
        return termGradeRepository.findById(id);
    }

    public Optional<TermGrade> findExisting(Student student, String academicYear, String semester) {
        return termGradeRepository.findByStudentAndAcademicYearAndSemester(student, academicYear, semester);
    }

    public List<String> findDistinctAcademicYears(School school) {
        return termGradeRepository.findDistinctAcademicYears(school);
    }

    // ===== 评分计算 =====

    /**
     * 自动计算综合分和等级。
     * 权重：出勤40% + 技能40% + 理论20%，空项按权重比例重新分摊。
     */
    public void computeAndFill(TermGrade g) {
        double sum = 0, weight = 0;
        if (g.getAttendanceScore() != null) { sum += g.getAttendanceScore() * 40; weight += 40; }
        if (g.getSkillScore()       != null) { sum += g.getSkillScore()       * 40; weight += 40; }
        if (g.getTheoryScore()      != null) { sum += g.getTheoryScore()      * 20; weight += 20; }
        if (weight > 0) {
            double total = Math.round(sum / weight * 10.0) / 10.0;
            g.setTotalScore(total);
            g.setLevel(total >= 90 ? "优秀" : total >= 80 ? "良好" : total >= 60 ? "及格" : "不及格");
        } else {
            g.setTotalScore(null);
            g.setLevel(null);
        }
    }

    // ===== 保存 =====

    @Transactional
    public void save(TermGrade g) {
        computeAndFill(g);
        termGradeRepository.save(g);
    }

    @Transactional
    public int saveBatch(List<Student> students, List<TermGrade> records,
                         School school, String academicYear, String semester) {
        int count = 0;
        for (int i = 0; i < students.size(); i++) {
            Student stu = students.get(i);
            TermGrade rec = records.get(i);
            if (rec.getAttendanceScore() == null && rec.getSkillScore() == null
                    && rec.getTheoryScore() == null) continue;

            TermGrade g = findExisting(stu, academicYear, semester).orElse(new TermGrade());
            g.setStudent(stu);
            g.setSchool(school);
            g.setAcademicYear(academicYear);
            g.setSemester(semester);
            if (rec.getAttendanceScore() != null) g.setAttendanceScore(rec.getAttendanceScore());
            if (rec.getSkillScore()       != null) g.setSkillScore(rec.getSkillScore());
            if (rec.getTheoryScore()      != null) g.setTheoryScore(rec.getTheoryScore());
            if (rec.getRemark() != null && !rec.getRemark().isBlank()) g.setRemark(rec.getRemark());
            computeAndFill(g);
            termGradeRepository.save(g);
            count++;
        }
        return count;
    }

    // ===== 删除 =====

    @Transactional
    public void deleteById(Long id) {
        termGradeRepository.deleteById(id);
    }

    @Transactional
    public void deleteByIds(List<Long> ids) {
        ids.forEach(termGradeRepository::deleteById);
    }

    // ===== Excel 导入 =====

    /**
     * 列顺序：学号 | 学年 | 学期 | 出勤分 | 技能分 | 理论分 | 备注
     */
    @Transactional
    public int importFromExcel(MultipartFile file, School school) throws IOException {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String studentNo   = cellStr(row.getCell(0));
                String academicYear = cellStr(row.getCell(1));
                String semester    = cellStr(row.getCell(2));
                if (studentNo.isBlank() || academicYear.isBlank() || semester.isBlank()) continue;
                Optional<Student> stuOpt = studentRepository.findByStudentNoAndSchool(studentNo, school);
                if (stuOpt.isEmpty()) continue;
                Student stu = stuOpt.get();
                TermGrade g = findExisting(stu, academicYear, semester).orElse(new TermGrade());
                g.setStudent(stu);
                g.setSchool(school);
                g.setAcademicYear(academicYear);
                g.setSemester(semester);
                g.setAttendanceScore(cellDouble(row.getCell(3)));
                g.setSkillScore(cellDouble(row.getCell(4)));
                g.setTheoryScore(cellDouble(row.getCell(5)));
                g.setRemark(cellStr(row.getCell(6)));
                computeAndFill(g);
                termGradeRepository.save(g);
                count++;
            }
            return count;
        }
    }

    // ===== Excel 导出 =====

    public byte[] exportToExcel(School school, Long classId, Long gradeId,
                                String academicYear, String semester, String keyword) throws IOException {
        Page<TermGrade> page = findWithFilters(school, classId, gradeId, academicYear, semester, keyword, 0, 5000);
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("期末成绩");
            String[] headers = {"学号","姓名","性别","年级","班级","学年","学期",
                                 "出勤分","技能分","理论分","综合分","等级","备注"};
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) hr.createCell(i).setCellValue(headers[i]);
            int r = 1;
            for (TermGrade g : page.getContent()) {
                Row row = sheet.createRow(r++);
                Student s = g.getStudent();
                row.createCell(0).setCellValue(s.getStudentNo() != null ? s.getStudentNo() : "");
                row.createCell(1).setCellValue(s.getName());
                row.createCell(2).setCellValue(s.getGender() != null ? s.getGender() : "");
                row.createCell(3).setCellValue(s.getSchoolClass().getGrade() != null
                        ? s.getSchoolClass().getGrade().getName() : "");
                row.createCell(4).setCellValue(s.getSchoolClass().getName());
                row.createCell(5).setCellValue(g.getAcademicYear() != null ? g.getAcademicYear() : "");
                row.createCell(6).setCellValue(g.getSemester()     != null ? g.getSemester()     : "");
                if (g.getAttendanceScore() != null) row.createCell(7).setCellValue(g.getAttendanceScore());
                if (g.getSkillScore()      != null) row.createCell(8).setCellValue(g.getSkillScore());
                if (g.getTheoryScore()     != null) row.createCell(9).setCellValue(g.getTheoryScore());
                if (g.getTotalScore()      != null) row.createCell(10).setCellValue(g.getTotalScore());
                row.createCell(11).setCellValue(g.getLevel()  != null ? g.getLevel()  : "");
                row.createCell(12).setCellValue(g.getRemark() != null ? g.getRemark() : "");
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("导入模板");
            String[] headers = {"学号(必填)", "学年(必填,如2025-2026)", "学期(必填,上学期/下学期)",
                                 "出勤分(0-100)", "技能分(0-100)", "理论分(0-100)", "备注"};
            Row hr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) hr.createCell(i).setCellValue(headers[i]);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ===== 工具方法 =====

    private String cellStr(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return cell.getStringCellValue().trim();
    }

    private Double cellDouble(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) return cell.getNumericCellValue();
        try { return Double.parseDouble(cell.getStringCellValue().trim()); } catch (Exception e) { return null; }
    }
}
