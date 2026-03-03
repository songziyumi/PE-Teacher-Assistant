package com.pe.assistant.service;

import com.pe.assistant.entity.*;
import com.pe.assistant.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PhysicalTestService {

    private final PhysicalTestRepository physicalTestRepository;
    private final StudentRepository studentRepository;
    private final PhysicalScoringService scoringService;

    public Page<PhysicalTest> findWithFilters(School school, Long classId, Long gradeId,
                                              String academicYear, String semester,
                                              String keyword, int page, int size) {
        return physicalTestRepository.findWithFilters(school, classId, gradeId,
                academicYear, semester, keyword,
                PageRequest.of(page, size, Sort.by("id").descending()));
    }

    public PhysicalTest findById(Long id) {
        return physicalTestRepository.findById(id).orElseThrow();
    }

    public List<String> findDistinctAcademicYears(School school) {
        return physicalTestRepository.findDistinctAcademicYears(school);
    }

    public Optional<PhysicalTest> findExisting(Student student, String academicYear, String semester) {
        return physicalTestRepository.findByStudentAndAcademicYearAndSemester(student, academicYear, semester);
    }

    @Transactional
    public void save(PhysicalTest test) {
        // 自动计算 BMI + 评分
        Student stu = test.getStudent();
        String gender = stu.getGender();
        String gradeName = (stu.getSchoolClass() != null && stu.getSchoolClass().getGrade() != null)
                ? stu.getSchoolClass().getGrade().getName() : "";
        scoringService.computeAndFill(test, gender, gradeName);
        physicalTestRepository.save(test);
    }

    /**
     * 批量保存（教师录入全班）：已有记录则覆盖，没有则新增。
     * @param students   班级学生列表（保持顺序与参数列表对应）
     * @param records    与 students 对应的测试数据（null 表示该学生本次未录入）
     */
    @Transactional
    public int saveBatch(List<Student> students, List<PhysicalTest> records, School school,
                         String academicYear, String semester) {
        int count = 0;
        for (int i = 0; i < students.size(); i++) {
            PhysicalTest rec = (i < records.size()) ? records.get(i) : null;
            if (rec == null) continue;
            // 跳过全空记录
            if (isAllEmpty(rec)) continue;

            Student stu = students.get(i);
            PhysicalTest existing = physicalTestRepository
                    .findByStudentAndAcademicYearAndSemester(stu, academicYear, semester)
                    .orElse(new PhysicalTest());

            existing.setStudent(stu);
            existing.setSchool(school);
            existing.setAcademicYear(academicYear);
            existing.setSemester(semester);
            if (rec.getTestDate() != null) existing.setTestDate(rec.getTestDate());
            if (rec.getHeight() != null)      existing.setHeight(rec.getHeight());
            if (rec.getWeight() != null)      existing.setWeight(rec.getWeight());
            if (rec.getLungCapacity() != null) existing.setLungCapacity(rec.getLungCapacity());
            if (rec.getSprint50m() != null)   existing.setSprint50m(rec.getSprint50m());
            if (rec.getSitReach() != null)    existing.setSitReach(rec.getSitReach());
            if (rec.getStandingJump() != null) existing.setStandingJump(rec.getStandingJump());
            if (rec.getPullUps() != null)     existing.setPullUps(rec.getPullUps());
            if (rec.getSitUps() != null)      existing.setSitUps(rec.getSitUps());
            if (rec.getRun800m() != null)     existing.setRun800m(rec.getRun800m());
            if (rec.getRun1000m() != null)    existing.setRun1000m(rec.getRun1000m());
            if (rec.getRemark() != null && !rec.getRemark().isBlank())
                existing.setRemark(rec.getRemark());

            save(existing);
            count++;
        }
        return count;
    }

    private boolean isAllEmpty(PhysicalTest r) {
        return r.getHeight() == null && r.getWeight() == null && r.getLungCapacity() == null
            && r.getSprint50m() == null && r.getSitReach() == null && r.getStandingJump() == null
            && r.getPullUps() == null && r.getSitUps() == null
            && r.getRun800m() == null && r.getRun1000m() == null;
    }

    @Transactional
    public void deleteById(Long id, School school) {
        PhysicalTest test = physicalTestRepository.findById(id).orElseThrow();
        if (!school.getId().equals(test.getSchool().getId())) return;
        physicalTestRepository.delete(test);
    }

    @Transactional
    public void deleteByIds(List<Long> ids, School school) {
        for (Long id : ids) deleteById(id, school);
    }

    /** Excel 批量导入，返回 {count, skipDup, skipNotFound, errors} */
    @Transactional
    public Map<String, Object> importFromExcel(InputStream is, School school,
                                               String academicYear, String semester) throws IOException {
        int count = 0, skipDup = 0, skipNotFound = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(is)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                errors.add("Excel 文件为空");
                return buildResult(count, skipDup, skipNotFound, errors);
            }

            Map<String, Integer> colIdx = new HashMap<>();
            for (Cell c : header) colIdx.put(c.getStringCellValue().trim(), c.getColumnIndex());

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String studentNo = str(row, colIdx.get("学号"));
                if (studentNo == null || studentNo.isBlank()) continue;

                Optional<Student> stuOpt = studentRepository.findByStudentNoAndSchool(studentNo, school);
                if (stuOpt.isEmpty()) { skipNotFound++; continue; }
                Student stu = stuOpt.get();

                // 查重
                if (physicalTestRepository.findByStudentAndAcademicYearAndSemester(stu, academicYear, semester).isPresent()) {
                    skipDup++; continue;
                }

                try {
                    PhysicalTest test = new PhysicalTest();
                    test.setStudent(stu);
                    test.setSchool(school);
                    test.setAcademicYear(academicYear);
                    test.setSemester(semester);

                    String dateStr = str(row, colIdx.get("测试日期"));
                    if (dateStr != null && !dateStr.isBlank()) {
                        try { test.setTestDate(LocalDate.parse(dateStr)); } catch (Exception ignored) {}
                    }

                    test.setHeight(num(row, colIdx.get("身高(cm)")));
                    test.setWeight(num(row, colIdx.get("体重(kg)")));
                    test.setLungCapacity(numInt(row, colIdx.get("肺活量(mL)")));
                    test.setSprint50m(num(row, colIdx.get("50米跑(秒)")));
                    test.setSitReach(num(row, colIdx.get("坐位体前屈(cm)")));
                    test.setStandingJump(num(row, colIdx.get("立定跳远(cm)")));

                    if ("男".equals(stu.getGender())) {
                        test.setPullUps(numInt(row, colIdx.get("引体向上(个)")));
                        test.setRun1000m(num(row, colIdx.get("1000米跑(秒)")));
                    } else {
                        test.setSitUps(numInt(row, colIdx.get("仰卧起坐(个/分钟)")));
                        test.setRun800m(num(row, colIdx.get("800米跑(秒)")));
                    }

                    save(test);
                    count++;
                } catch (Exception e) {
                    errors.add("第 " + (r + 1) + " 行：" + e.getMessage());
                }
            }
        }

        return buildResult(count, skipDup, skipNotFound, errors);
    }

    /** 导出 Excel，返回 byte[] */
    public byte[] exportToExcel(School school, Long classId, Long gradeId,
                                String academicYear, String semester, String keyword) throws IOException {
        // 查询全量（不分页，最多 5000 条）
        Page<PhysicalTest> page = physicalTestRepository.findWithFilters(school, classId, gradeId,
                academicYear, semester, keyword, PageRequest.of(0, 5000, Sort.by("id").descending()));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("体质健康测试");
            String[] headers = {"学号", "姓名", "性别", "班级", "年级", "测试日期",
                    "身高(cm)", "体重(kg)", "BMI", "肺活量(mL)",
                    "50米跑(秒)", "坐位体前屈(cm)", "立定跳远(cm)",
                    "引体向上(个)", "仰卧起坐(个/分钟)", "1000米跑(秒)", "800米跑(秒)",
                    "总分", "等级", "备注"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);

            int r = 1;
            for (PhysicalTest t : page.getContent()) {
                Row row = sheet.createRow(r++);
                Student s = t.getStudent();
                String className = s.getSchoolClass() != null ? s.getSchoolClass().getName() : "";
                String gradeName = (s.getSchoolClass() != null && s.getSchoolClass().getGrade() != null)
                        ? s.getSchoolClass().getGrade().getName() : "";
                row.createCell(0).setCellValue(nvl(s.getStudentNo()));
                row.createCell(1).setCellValue(s.getName());
                row.createCell(2).setCellValue(nvl(s.getGender()));
                row.createCell(3).setCellValue(className);
                row.createCell(4).setCellValue(gradeName);
                row.createCell(5).setCellValue(t.getTestDate() != null ? t.getTestDate().toString() : "");
                setNum(row, 6, t.getHeight());
                setNum(row, 7, t.getWeight());
                setNum(row, 8, t.getBmi());
                setNumInt(row, 9, t.getLungCapacity());
                setNum(row, 10, t.getSprint50m());
                setNum(row, 11, t.getSitReach());
                setNum(row, 12, t.getStandingJump());
                setNumInt(row, 13, t.getPullUps());
                setNumInt(row, 14, t.getSitUps());
                setNum(row, 15, t.getRun1000m());
                setNum(row, 16, t.getRun800m());
                setNum(row, 17, t.getTotalScore());
                row.createCell(18).setCellValue(nvl(t.getLevel()));
                row.createCell(19).setCellValue(nvl(t.getRemark()));
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 生成导入模板 */
    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("体质健康测试");
            String[] headers = {"学号", "测试日期", "身高(cm)", "体重(kg)", "肺活量(mL)",
                    "50米跑(秒)", "坐位体前屈(cm)", "立定跳远(cm)",
                    "引体向上(个)", "仰卧起坐(个/分钟)", "1000米跑(秒)", "800米跑(秒)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);
            // 示例行
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("20230101");
            sample.createCell(1).setCellValue("2025-09-01");
            sample.createCell(2).setCellValue(175.0);
            sample.createCell(3).setCellValue(65.0);
            sample.createCell(4).setCellValue(4200);
            sample.createCell(5).setCellValue(7.2);
            sample.createCell(6).setCellValue(18.0);
            sample.createCell(7).setCellValue(210.0);
            sample.createCell(8).setCellValue(8);
            sample.createCell(9).setCellValue(0);
            sample.createCell(10).setCellValue(255.0);
            sample.createCell(11).setCellValue(0);
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ===== 内部工具方法 =====

    private String str(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        if (c.getCellType() == CellType.STRING) return c.getStringCellValue().trim();
        if (c.getCellType() == CellType.NUMERIC) return String.valueOf((long) c.getNumericCellValue());
        if (c.getCellType() == CellType.FORMULA) {
            CellType type = c.getCachedFormulaResultType();
            if (type == CellType.STRING) return c.getStringCellValue().trim();
            if (type == CellType.NUMERIC) return String.valueOf((long) c.getNumericCellValue());
        }
        return null;
    }

    private Double num(Row row, Integer idx) {
        if (idx == null) return null;
        Cell c = row.getCell(idx);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return c.getNumericCellValue();
        if (c.getCellType() == CellType.FORMULA && c.getCachedFormulaResultType() == CellType.NUMERIC)
            return c.getNumericCellValue();
        if (c.getCellType() == CellType.STRING) {
            try { return Double.parseDouble(c.getStringCellValue().trim()); } catch (Exception e) { return null; }
        }
        return null;
    }

    private Integer numInt(Row row, Integer idx) {
        Double d = num(row, idx);
        return d == null ? null : d.intValue();
    }

    private void setNum(Row row, int col, Double val) {
        if (val != null) row.createCell(col).setCellValue(val);
        else row.createCell(col).setCellValue("");
    }

    private void setNumInt(Row row, int col, Integer val) {
        if (val != null) row.createCell(col).setCellValue(val);
        else row.createCell(col).setCellValue("");
    }

    private String nvl(String s) { return s != null ? s : ""; }

    private Map<String, Object> buildResult(int count, int skipDup, int skipNotFound, List<String> errors) {
        Map<String, Object> r = new HashMap<>();
        r.put("count", count);
        r.put("skipDup", skipDup);
        r.put("skipNotFound", skipNotFound);
        r.put("errors", errors);
        return r;
    }
}
