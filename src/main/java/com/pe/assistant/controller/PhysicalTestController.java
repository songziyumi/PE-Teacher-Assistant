package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/physical-tests")
@RequiredArgsConstructor
public class PhysicalTestController {

    private final PhysicalTestService physicalTestService;
    private final StudentService studentService;
    private final ClassService classService;
    private final GradeService gradeService;
    private final CurrentUserService currentUserService;

    @ModelAttribute("currentSchool")
    public School currentSchool() {
        return currentUserService.getCurrentSchool();
    }

    // ===== 列表页 =====
    @GetMapping
    public String list(@RequestParam(required = false) Long gradeId,
                       @RequestParam(required = false) Long classId,
                       @RequestParam(defaultValue = "") String academicYear,
                       @RequestParam(defaultValue = "") String semester,
                       @RequestParam(defaultValue = "") String keyword,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        School school = currentUserService.getCurrentSchool();
        Page<PhysicalTest> testPage = physicalTestService.findWithFilters(
                school, classId, gradeId, academicYear, semester, keyword, page, 15);

        model.addAttribute("testPage", testPage);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("classId", classId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("semester", semester);
        model.addAttribute("keyword", keyword);
        model.addAttribute("grades", gradeService.findAll(school));
        model.addAttribute("classes", classService.findAll(school));
        model.addAttribute("academicYears", physicalTestService.findDistinctAcademicYears(school));
        model.addAttribute("allStudents", studentService.findBySchool(school));
        return "admin/physical-tests";
    }

    // ===== 新增/编辑 =====
    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long studentId,
                       @RequestParam String academicYear,
                       @RequestParam String semester,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate testDate,
                       @RequestParam(required = false) Double height,
                       @RequestParam(required = false) Double weight,
                       @RequestParam(required = false) Integer lungCapacity,
                       @RequestParam(required = false) Double sprint50m,
                       @RequestParam(required = false) Double sitReach,
                       @RequestParam(required = false) Double standingJump,
                       @RequestParam(required = false) Integer pullUps,
                       @RequestParam(required = false) Integer sitUps,
                       @RequestParam(required = false) Double run800m,
                       @RequestParam(required = false) Double run1000m,
                       @RequestParam(defaultValue = "") String remark,
                       RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        try {
            PhysicalTest test = id != null ? physicalTestService.findById(id) : new PhysicalTest();
            test.setSchool(school);
            test.setStudent(studentService.findById(studentId));
            test.setAcademicYear(academicYear);
            test.setSemester(semester);
            test.setTestDate(testDate);
            test.setHeight(height);
            test.setWeight(weight);
            test.setLungCapacity(lungCapacity);
            test.setSprint50m(sprint50m);
            test.setSitReach(sitReach);
            test.setStandingJump(standingJump);
            test.setPullUps(pullUps);
            test.setSitUps(sitUps);
            test.setRun800m(run800m);
            test.setRun1000m(run1000m);
            test.setRemark(remark.isBlank() ? null : remark);
            physicalTestService.save(test);
            ra.addFlashAttribute("success", id != null ? "修改成功" : "添加成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/physical-tests";
    }

    // ===== 删除单条 =====
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        physicalTestService.deleteById(id, currentUserService.getCurrentSchool());
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/physical-tests";
    }

    // ===== 批量删除 =====
    @PostMapping("/delete-batch")
    public String deleteBatch(@RequestParam List<Long> ids, RedirectAttributes ra) {
        physicalTestService.deleteByIds(ids, currentUserService.getCurrentSchool());
        ra.addFlashAttribute("success", "已删除 " + ids.size() + " 条记录");
        return "redirect:/admin/physical-tests";
    }

    // ===== Excel 导入 =====
    @PostMapping("/import")
    public String importExcel(@RequestParam MultipartFile file,
                              @RequestParam String academicYear,
                              @RequestParam String semester,
                              RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "请选择文件");
            return "redirect:/admin/physical-tests";
        }
        School school = currentUserService.getCurrentSchool();
        try {
            validateExcel(file);
            Map<String, Object> result = physicalTestService.importFromExcel(
                    file.getInputStream(), school, academicYear, semester);
            int count        = (int) result.get("count");
            int skipDup      = (int) result.get("skipDup");
            int skipNotFound = (int) result.get("skipNotFound");
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) result.get("errors");

            StringBuilder msg = new StringBuilder("导入完成：新增 ").append(count).append(" 条");
            if (skipDup > 0) msg.append("，重复跳过 ").append(skipDup).append(" 条");
            if (skipNotFound > 0) msg.append("，未找到学生 ").append(skipNotFound).append(" 条");
            if (!errors.isEmpty()) msg.append("，错误 ").append(errors.size()).append(" 条");
            ra.addFlashAttribute("success", msg.toString());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/physical-tests";
    }

    // ===== 下载导入模板 =====
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] data = physicalTestService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=physical_test_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    // ===== Excel 导出 =====
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long gradeId,
                                         @RequestParam(required = false) Long classId,
                                         @RequestParam(defaultValue = "") String academicYear,
                                         @RequestParam(defaultValue = "") String semester,
                                         @RequestParam(defaultValue = "") String keyword) throws IOException {
        School school = currentUserService.getCurrentSchool();
        byte[] data = physicalTestService.exportToExcel(school, classId, gradeId, academicYear, semester, keyword);
        String filename = URLEncoder.encode("体质健康测试数据.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    private void validateExcel(MultipartFile file) throws IOException {
        byte[] magic = new byte[4];
        try (InputStream is = file.getInputStream()) {
            if (is.read(magic) < 4 || magic[0] != 0x50 || magic[1] != 0x4B)
                throw new IllegalArgumentException("文件格式不正确，请上传 .xlsx 文件");
        }
    }
}
