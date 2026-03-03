package com.pe.assistant.controller;

import com.pe.assistant.entity.*;
import com.pe.assistant.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/admin/term-grades")
@RequiredArgsConstructor
public class TermGradeController {

    private final TermGradeService termGradeService;
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
        Page<TermGrade> gradePage = termGradeService.findWithFilters(
                school, classId, gradeId, academicYear, semester, keyword, page, 20);

        model.addAttribute("gradePage", gradePage);
        model.addAttribute("gradeId", gradeId);
        model.addAttribute("classId", classId);
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("semester", semester);
        model.addAttribute("keyword", keyword);
        model.addAttribute("grades", gradeService.findAll(school));
        model.addAttribute("classes", classService.findAll(school));
        model.addAttribute("academicYears", termGradeService.findDistinctAcademicYears(school));
        return "admin/term-grades";
    }

    // ===== 保存（新增/编辑） =====

    @PostMapping("/save")
    public String save(@RequestParam(required = false) Long id,
                       @RequestParam Long studentId,
                       @RequestParam String academicYear,
                       @RequestParam String semester,
                       @RequestParam(required = false) Double attendanceScore,
                       @RequestParam(required = false) Double skillScore,
                       @RequestParam(required = false) Double theoryScore,
                       @RequestParam(required = false) String remark,
                       RedirectAttributes ra) {
        School school = currentUserService.getCurrentSchool();
        TermGrade g = (id != null && id > 0)
                ? termGradeService.findById(id).orElse(new TermGrade())
                : new TermGrade();
        g.setStudent(studentService.findById(studentId));
        g.setSchool(school);
        g.setAcademicYear(academicYear);
        g.setSemester(semester);
        g.setAttendanceScore(attendanceScore);
        g.setSkillScore(skillScore);
        g.setTheoryScore(theoryScore);
        g.setRemark(remark);
        termGradeService.save(g);
        ra.addFlashAttribute("success", "保存成功");
        return "redirect:/admin/term-grades";
    }

    // ===== 删除 =====

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        termGradeService.deleteById(id);
        ra.addFlashAttribute("success", "删除成功");
        return "redirect:/admin/term-grades";
    }

    @PostMapping("/delete-batch")
    public String deleteBatch(@RequestParam List<Long> ids, RedirectAttributes ra) {
        termGradeService.deleteByIds(ids);
        ra.addFlashAttribute("success", "批量删除 " + ids.size() + " 条记录");
        return "redirect:/admin/term-grades";
    }

    // ===== Excel 导入 =====

    @PostMapping("/import")
    public String importExcel(@RequestParam MultipartFile file, RedirectAttributes ra) {
        try {
            byte[] magic = file.getBytes();
            if (magic.length < 4 || magic[0] != 0x50 || magic[1] != 0x4B) {
                ra.addFlashAttribute("error", "文件格式错误，请上传 .xlsx 文件");
                return "redirect:/admin/term-grades";
            }
            int count = termGradeService.importFromExcel(file, currentUserService.getCurrentSchool());
            ra.addFlashAttribute("success", "导入成功 " + count + " 条记录");
        } catch (IOException e) {
            ra.addFlashAttribute("error", "导入失败：" + e.getMessage());
        }
        return "redirect:/admin/term-grades";
    }

    // ===== 导出 / 模板 =====

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws IOException {
        byte[] bytes = termGradeService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                        URLEncoder.encode("期末成绩导入模板.xlsx", StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long gradeId,
                                          @RequestParam(required = false) Long classId,
                                          @RequestParam(defaultValue = "") String academicYear,
                                          @RequestParam(defaultValue = "") String semester,
                                          @RequestParam(defaultValue = "") String keyword) throws IOException {
        School school = currentUserService.getCurrentSchool();
        byte[] bytes = termGradeService.exportToExcel(school, classId, gradeId, academicYear, semester, keyword);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" +
                        URLEncoder.encode("期末成绩.xlsx", StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
