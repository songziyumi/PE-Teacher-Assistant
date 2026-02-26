package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final TeacherService teacherService;
    private final ClassService classService;
    private final StudentService studentService;
    private final GradeService gradeService;
    private final AttendanceService attendanceService;

    // ===== Dashboard =====
    @GetMapping
    public String adminDashboard(Model model) {
        model.addAttribute("teacherCount", teacherService.findAll().size());
        model.addAttribute("classCount", classService.findAll().size());
        model.addAttribute("gradeCount", gradeService.findAll().size());
        return "admin/dashboard";
    }

    // ===== 年级管理 =====
    @GetMapping("/grades")
    public String grades(Model model) {
        model.addAttribute("grades", gradeService.findAll());
        return "admin/grades";
    }

    @PostMapping("/grades/add")
    public String addGrade(@RequestParam String name, RedirectAttributes ra) {
        try { gradeService.create(name); ra.addFlashAttribute("success", "年级添加成功"); }
        catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/grades";
    }

    @PostMapping("/grades/edit/{id}")
    public String editGrade(@PathVariable Long id, @RequestParam String name, RedirectAttributes ra) {
        gradeService.update(id, name);
        ra.addFlashAttribute("success", "修改成功");
        return "redirect:/admin/grades";
    }

    @PostMapping("/grades/delete/{id}")
    public String deleteGrade(@PathVariable Long id, RedirectAttributes ra) {
        gradeService.delete(id);
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/grades";
    }

    // ===== 班级管理 =====
    @GetMapping("/classes")
    public String classes(@RequestParam(defaultValue = "") String type,
                          @RequestParam(required = false) Long gradeId,
                          @RequestParam(defaultValue = "") String name,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Page<SchoolClass> classPage = classService.findByFilters(type, gradeId, name, page, 15);
        model.addAttribute("classPage", classPage);
        model.addAttribute("type", type);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("name", name);
        model.addAttribute("grades", gradeService.findAll());
        return "admin/classes";
    }

    @PostMapping("/classes/add")
    public String addClass(@RequestParam String name, @RequestParam String type,
                           @RequestParam(required = false) Long gradeId, RedirectAttributes ra) {
        try {
            if ("选修课".equals(type)) {
                classService.createElective(name, gradeId);
            } else {
                if (gradeId == null) throw new IllegalArgumentException("行政班必须选择年级");
                classService.create(name, gradeId);
            }
            ra.addFlashAttribute("success", "班级添加成功");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/classes";
    }

    @PostMapping("/classes/assign/{id}")
    public String assignTeacher(@PathVariable Long id, @RequestParam Long teacherId, RedirectAttributes ra) {
        classService.assignTeacher(id, teacherId);
        ra.addFlashAttribute("success", "教师分配成功");
        return "redirect:/admin/classes";
    }

    @PostMapping("/classes/delete/{id}")
    public String deleteClass(@PathVariable Long id, RedirectAttributes ra) {
        classService.delete(id);
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/classes";
    }

    @PostMapping("/classes/delete-all")
    public String deleteAllClasses(RedirectAttributes ra) {
        classService.deleteAll();
        ra.addFlashAttribute("success", "已删除全部班级数据");
        return "redirect:/admin/classes";
    }

    // ===== 教师管理 =====
    @GetMapping("/teachers")
    public String teachers(@RequestParam(required = false) String name,
                           @RequestParam(required = false) String username,
                           @RequestParam(required = false) String phone,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        int pageSize = 15;
        Page<Teacher> teacherPage = teacherService.findByFilters(name, username, phone, page, pageSize);
        List<com.pe.assistant.entity.SchoolClass> allClasses = classService.findAll();
        model.addAttribute("teacherPage", teacherPage);
        model.addAttribute("teachers", teacherPage.getContent());
        model.addAttribute("searchName", name);
        model.addAttribute("searchUsername", username);
        model.addAttribute("searchPhone", phone);
        model.addAttribute("classes", allClasses);
        // 每个教师已分配的班级名称列表（供表格显示）
        Map<Long, List<String>> teacherClassNames = new HashMap<>();
        // 每个教师已分配的班级 id（String key，避免 Thymeleaf inline JS 序列化 Long 加 L 后缀）
        Map<String, List<Long>> teacherClassIds = new HashMap<>();
        for (com.pe.assistant.entity.SchoolClass c : allClasses) {
            if (c.getTeacher() != null) {
                Long tid = c.getTeacher().getId();
                String label = (c.getGrade() != null ? c.getGrade().getName() + " " : "") + c.getName();
                teacherClassNames.computeIfAbsent(tid, k -> new ArrayList<>()).add(label);
                teacherClassIds.computeIfAbsent(String.valueOf(tid), k -> new ArrayList<>()).add(c.getId());
            }
        }
        model.addAttribute("teacherClassIds", teacherClassIds);
        model.addAttribute("teacherClassNames", teacherClassNames);
        return "admin/teachers";
    }

    @PostMapping("/teachers/add")
    public String addTeacher(@RequestParam String name,
                             @RequestParam String password,
                             @RequestParam String phone,
                             @RequestParam(required = false) List<Long> classIds,
                             RedirectAttributes ra) {
        try {
            Teacher newTeacher = teacherService.create(phone, name, password, "TEACHER", phone);
            if (classIds != null) {
                for (Long classId : classIds) {
                    classService.assignTeacher(classId, newTeacher.getId());
                }
            }
            ra.addFlashAttribute("success", "教师添加成功");
        } catch (Exception e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/teachers";
    }

    @PostMapping("/teachers/reset-password/{id}")
    public String resetPassword(@PathVariable Long id, @RequestParam String newPassword, RedirectAttributes ra) {
        teacherService.resetPassword(id, newPassword);
        ra.addFlashAttribute("success", "密码重置成功");
        return "redirect:/admin/teachers";
    }

    @PostMapping("/teachers/delete/{id}")
    public String deleteTeacher(@PathVariable Long id, RedirectAttributes ra) {
        teacherService.delete(id);
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/teachers";
    }

    @PostMapping("/teachers/assign/{id}")
    public String assignClasses(@PathVariable Long id,
                                @RequestParam(required = false) List<Long> classIds,
                                RedirectAttributes ra) {
        teacherService.assignClasses(id, classIds);
        ra.addFlashAttribute("success", "班级分配成功");
        return "redirect:/admin/teachers";
    }

    @PostMapping("/teachers/delete-all")
    public String deleteAllTeachers(RedirectAttributes ra) {
        teacherService.deleteAll();
        ra.addFlashAttribute("success", "已删除全部教师数据");
        return "redirect:/admin/teachers";
    }

    // ===== 学生管理 =====
    @GetMapping("/students")
    public String students(@RequestParam(required = false) Long gradeId,
                           @RequestParam(required = false) Long classId,
                           @RequestParam(defaultValue = "") String name,
                           @RequestParam(defaultValue = "") String studentNo,
                           @RequestParam(defaultValue = "") String idCard,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Long effectiveClassId = classId;
        String electiveClass = null;
        if (classId != null) {
            SchoolClass sc = classService.findById(classId);
            if ("选修课".equals(sc.getType())) {
                effectiveClassId = null;
                electiveClass = sc.getName();
            }
        }
        Page<Student> studentPage = studentService.findWithFilters(effectiveClassId, gradeId, name, studentNo, idCard, electiveClass, page, 15);
        model.addAttribute("studentPage", studentPage);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("classId", classId);
        model.addAttribute("name", name);
        model.addAttribute("studentNo", studentNo);
        model.addAttribute("idCard", idCard);
        model.addAttribute("grades", gradeService.findAll());
        model.addAttribute("classes", classService.findAll());
        model.addAttribute("electiveClasses", classService.findAll().stream()
            .filter(c -> "选修课".equals(c.getType())).toList());
        return "admin/students";
    }

    @GetMapping("/students/class/{classId}")
    public String studentsByClass(@PathVariable Long classId,
                                  @RequestParam(defaultValue = "") String name,
                                  @RequestParam(defaultValue = "") String studentNo,
                                  @RequestParam(defaultValue = "") String idCard,
                                  @RequestParam(defaultValue = "0") int page,
                                  Model model) {
        return "redirect:/admin/students?classId=" + classId +
               "&name=" + name + "&studentNo=" + studentNo + "&idCard=" + idCard + "&page=" + page;
    }

    @PostMapping("/students/delete-all")
    public String deleteAllStudents(RedirectAttributes ra) {
        studentService.deleteAll();
        ra.addFlashAttribute("success", "已删除全部学生数据");
        return "redirect:/admin/students";
    }

    @PostMapping("/students/add")
    public String addStudent(@RequestParam String name, @RequestParam String gender,
                             @RequestParam String studentNo, @RequestParam String idCard,
                             @RequestParam String electiveClass, @RequestParam Long classId,
                             RedirectAttributes ra) {
        studentService.create(name, gender, studentNo, idCard, electiveClass, classId);
        ra.addFlashAttribute("success", "学生添加成功");
        return "redirect:/admin/students";
    }

    @PostMapping("/students/edit/{id}")
    public String editStudent(@PathVariable Long id, @RequestParam String name,
                              @RequestParam String gender, @RequestParam String studentNo,
                              @RequestParam String idCard, @RequestParam String electiveClass,
                              @RequestParam Long classId, RedirectAttributes ra) {
        studentService.update(id, name, gender, studentNo, idCard, electiveClass);
        ra.addFlashAttribute("success", "修改成功");
        return "redirect:/admin/students";
    }

    @PostMapping("/students/delete/{id}")
    public String deleteStudent(@PathVariable Long id, @RequestParam Long classId, RedirectAttributes ra) {
        studentService.delete(id);
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/students";
    }

    // ===== 统计 =====
    @GetMapping("/stats")
    public String stats(@RequestParam(required = false) Long gradeId,
                        @RequestParam(required = false) Long classId,
                        @RequestParam(defaultValue = "") String name,
                        @RequestParam(defaultValue = "") String studentNo,
                        @RequestParam(defaultValue = "") String idCard,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        Long effectiveClassId = classId;
        String electiveClass = null;
        if (classId != null) {
            SchoolClass sc = classService.findById(classId);
            if ("选修课".equals(sc.getType())) {
                effectiveClassId = null;
                electiveClass = sc.getName();
            }
        }
        Page<Student> studentPage = studentService.findWithFilters(effectiveClassId, gradeId, name, studentNo, idCard, electiveClass, page, 15);
        List<Map<String, Object>> studentStats = new ArrayList<>();
        for (Student s : studentPage.getContent()) {
            Map<String, Object> stat = attendanceService.getStudentStats(s.getId());
            stat.put("studentName", s.getName());
            stat.put("studentNo", s.getStudentNo());
            stat.put("className", s.getSchoolClass() != null
                ? (s.getSchoolClass().getGrade() != null
                    ? s.getSchoolClass().getGrade().getName() + " " + s.getSchoolClass().getName()
                    : s.getSchoolClass().getName())
                : "");
            studentStats.add(stat);
        }
        model.addAttribute("studentStats", studentStats);
        model.addAttribute("studentPage", studentPage);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("classId", classId);
        model.addAttribute("name", name);
        model.addAttribute("studentNo", studentNo);
        model.addAttribute("idCard", idCard);
        model.addAttribute("grades", gradeService.findAll());
        model.addAttribute("classes", classService.findAll());
        return "admin/stats";
    }

    @GetMapping("/stats/absent")
    public String absentQuery(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                              Model model) {
        if (start == null) start = LocalDate.now();
        if (end == null)   end   = LocalDate.now();
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        model.addAttribute("records", attendanceService.findAbsentOrLeaveBetween(start, end));
        return "admin/absent";
    }

    @GetMapping("/stats/absent/export")
    public void exportAbsent(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                             HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String filename = start.equals(end)
            ? start + "缺勤、请假名单.csv"
            : start + "至" + end + "缺勤、请假名单.csv";
        response.setHeader("Content-Disposition",
            "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        List<Attendance> records = attendanceService.findAbsentOrLeaveBetween(start, end);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.println("日期,姓名,班级,年级,状态");
            for (Attendance a : records) {
                writer.printf("%s,%s,%s,%s,%s%n",
                    a.getDate(), a.getStudent().getName(),
                    a.getStudent().getSchoolClass().getName(),
                    a.getStudent().getSchoolClass().getGrade() != null ? a.getStudent().getSchoolClass().getGrade().getName() : "",
                    a.getStatus());
            }
        }
    }

    // ===== Excel 导入 =====
    @GetMapping("/import")
    public String importPage() {
        return "admin/import";
    }

    @GetMapping("/import/template/classes")
    public void downloadClassTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=classes_template.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("班级");
            Row header = sheet.createRow(0);
            String[] cols = {"班级名称", "类型", "年级"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            // 示例行
            Object[][] samples = {{"1班", "行政班", "高一"}, {"篮球", "选修课", ""}};
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++)
                    row.createCell(c).setCellValue(samples[r][c].toString());
            }
            wb.write(response.getOutputStream());
        }
    }

    @PostMapping("/import/classes")
    public String importClasses(@RequestParam MultipartFile file, RedirectAttributes ra) {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header) col.put(c.getStringCellValue().trim(), c.getColumnIndex());
            int count = 0, skip = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String name      = cellStr(row, col.getOrDefault("班级名称", -1));
                String type      = cellStr(row, col.getOrDefault("类型", -1));
                String gradeName = cellStr(row, col.getOrDefault("年级", -1));
                if (name.isBlank()) { skip++; continue; }
                if (type.isBlank()) type = "行政班";
                try {
                    if ("选修课".equals(type)) {
                        Grade grade = gradeName.isBlank() ? null : gradeService.findByName(gradeName);
                        classService.createElective(name, grade != null ? grade.getId() : null);
                    } else {
                        Grade grade = gradeService.findByName(gradeName);
                        if (grade == null) { errors.add("第" + (i+1) + "行：年级「" + gradeName + "」不存在"); skip++; continue; }
                        classService.create(name, grade.getId());
                    }
                    count++;
                } catch (Exception e) { errors.add("第" + (i+1) + "行：" + e.getMessage()); skip++; }
            }
            String msg = "成功导入 " + count + " 个班级" + (skip > 0 ? "，跳过 " + skip + " 条" : "");
            if (!errors.isEmpty()) {
                ra.addFlashAttribute("error", msg + "\n失败原因：\n" + String.join("\n", errors));
            } else {
                ra.addFlashAttribute("success", msg);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @GetMapping("/import/template/students")
    public void downloadStudentTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=students_template.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("学生");
            Row header = sheet.createRow(0);
            String[] cols = {"年级", "班级", "姓名", "性别", "学号", "身份证号", "选修课"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            // 示例行
            Row example = sheet.createRow(1);
            String[] sample = {"高一", "1班", "张三", "男", "20240001", "110101200001011234", "篮球"};
            for (int i = 0; i < sample.length; i++) example.createCell(i).setCellValue(sample[i]);
            wb.write(response.getOutputStream());
        }
    }

    @GetMapping("/import/template/teachers")
    public void downloadTeacherTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=teachers_template.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("教师");
            Row header = sheet.createRow(0);
            String[] cols = {"姓名", "手机号", "密码", "行政班", "选修课"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
            // 示例行
            Row example = sheet.createRow(1);
            String[] sample = {"李老师", "13800138001", "123456", "高一1班,高一2班", "篮球,足球"};
            for (int i = 0; i < sample.length; i++) example.createCell(i).setCellValue(sample[i]);
            wb.write(response.getOutputStream());
        }
    }

    @PostMapping("/import/teachers")
    public String importTeachers(@RequestParam MultipartFile file, RedirectAttributes ra) {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header) col.put(c.getStringCellValue().trim(), c.getColumnIndex());
            List<SchoolClass> allClasses = classService.findAll();
            int count = 0, skip = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String name        = cellStr(row, col.getOrDefault("姓名", -1));
                String phone       = cellStr(row, col.getOrDefault("手机号", -1));
                String password    = cellStr(row, col.getOrDefault("密码", -1));
                String adminClass  = cellStr(row, col.getOrDefault("行政班", -1));
                String electiveClass = cellStr(row, col.getOrDefault("选修课", -1));
                if (phone.isBlank()) { skip++; continue; }
                if (password.isBlank()) password = "123456";
                try {
                    Teacher t = teacherService.create(phone, name, password, "TEACHER", phone);
                    if (!adminClass.isBlank()) {
                        for (String ac : adminClass.split("[,，]")) {
                            String acTrim = ac.trim();
                            allClasses.stream()
                                .filter(c -> "行政班".equals(c.getType()) && (
                                    acTrim.equals(c.getName()) ||
                                    acTrim.equals((c.getGrade() != null ? c.getGrade().getName() : "") + c.getName())
                                ))
                                .findFirst()
                                .ifPresent(c -> classService.assignTeacher(c.getId(), t.getId()));
                        }
                    }
                    if (!electiveClass.isBlank()) {
                        for (String ec : electiveClass.split("[,，]")) {
                            String ecTrim = ec.trim();
                            allClasses.stream()
                                .filter(c -> "选修课".equals(c.getType()) && (
                                    ecTrim.equals(c.getName()) ||
                                    ecTrim.equals((c.getGrade() != null ? c.getGrade().getName() : "") + c.getName())
                                ))
                                .findFirst()
                                .ifPresent(c -> classService.assignTeacher(c.getId(), t.getId()));
                        }
                    }
                    count++;
                } catch (IllegalArgumentException e) { errors.add("第" + (i+1) + "行：" + e.getMessage()); skip++; }
            }
            String msg = "成功导入 " + count + " 名教师" + (skip > 0 ? "，跳过 " + skip + " 条" : "");
            if (!errors.isEmpty()) {
                ra.addFlashAttribute("error", msg + "\n失败原因：\n" + String.join("\n", errors));
            } else {
                ra.addFlashAttribute("success", msg);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @PostMapping("/import/students")
    public String importStudents(@RequestParam MultipartFile file, RedirectAttributes ra) {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header) col.put(c.getStringCellValue().trim(), c.getColumnIndex());
            List<SchoolClass> classes = classService.findAll();
            int count = 0, skip = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String gradeName    = cellStr(row, col.getOrDefault("年级", -1));
                String className    = cellStr(row, col.getOrDefault("班级", -1));
                String name         = cellStr(row, col.getOrDefault("姓名", -1));
                String gender       = cellStr(row, col.getOrDefault("性别", -1));
                String studentNo    = cellStr(row, col.getOrDefault("学号", -1));
                String idCard       = cellStr(row, col.getOrDefault("身份证号", -1));
                String electiveClass= cellStr(row, col.getOrDefault("选修课", -1));
                SchoolClass sc = classes.stream()
                    .filter(c -> c.getName().equals(className)
                            && c.getGrade() != null
                            && c.getGrade().getName().equals(gradeName))
                    .findFirst().orElse(null);
                if (sc == null) {
                    errors.add("第" + (i+1) + "行：找不到班级「" + gradeName + " " + className + "」");
                    skip++; continue;
                }
                try {
                    studentService.create(name, gender, studentNo, idCard, electiveClass, sc.getId());
                    count++;
                } catch (Exception e) { errors.add("第" + (i+1) + "行：" + e.getMessage()); skip++; }
            }
            String msg = "成功导入 " + count + " 名学生" + (skip > 0 ? "，跳过 " + skip + " 条" : "");
            if (!errors.isEmpty()) {
                ra.addFlashAttribute("error", msg + "\n失败原因：\n" + String.join("\n", errors));
            } else {
                ra.addFlashAttribute("success", msg);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    @GetMapping("/import/template/elective")
    public void downloadElectiveTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=elective_template.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("选修课");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("选修课");
            Row example = sheet.createRow(1);
            example.createCell(0).setCellValue("20240001");
            example.createCell(1).setCellValue("篮球");
            wb.write(response.getOutputStream());
        }
    }

    @PostMapping("/import/elective")
    public String importElective(@RequestParam MultipartFile file, RedirectAttributes ra) {
        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            Map<String, Integer> col = new HashMap<>();
            for (Cell c : header) col.put(c.getStringCellValue().trim(), c.getColumnIndex());
            int count = 0, skip = 0;
            List<String> errors = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String studentNo   = cellStr(row, col.getOrDefault("学号", -1));
                String electiveClass = cellStr(row, col.getOrDefault("选修课", -1));
                if (studentNo.isBlank()) { skip++; continue; }
                try {
                    studentService.updateElectiveByStudentNo(studentNo, electiveClass.isBlank() ? null : electiveClass);
                    count++;
                } catch (Exception e) { errors.add("第" + (i+1) + "行：" + e.getMessage()); skip++; }
            }
            String msg = "成功更新 " + count + " 名学生的选修课" + (skip > 0 ? "，跳过 " + skip + " 条" : "");
            if (!errors.isEmpty()) {
                ra.addFlashAttribute("error", msg + "\n失败原因：\n" + String.join("\n", errors));
            } else {
                ra.addFlashAttribute("success", msg);
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/import";
    }

    private String cellStr(Row row, int idx) {
        if (idx < 0 || row == null) return "";
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default      -> "";
        };
    }
}
