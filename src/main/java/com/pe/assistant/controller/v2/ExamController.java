package com.pe.assistant.controller.v2;

import com.pe.assistant.entity.ExamRecord;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.ExamService;
import com.pe.assistant.service.StudentService;
import com.pe.assistant.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/v2/exam")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    @GetMapping
    public String examDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        model.addAttribute("teacher", teacher);
        return "v2/exam/dashboard";
    }

    @GetMapping("/class/{classId}")
    public String classExamRecords(@PathVariable Long classId,
            @RequestParam(required = false) String examName,
            Model model) {
        List<ExamRecord> records;
        if (examName != null && !examName.isEmpty()) {
            // TODO: 根据考试名称筛选
            records = examService.findByClassId(classId);
        } else {
            records = examService.findByClassId(classId);
        }
        model.addAttribute("records", records);
        model.addAttribute("classId", classId);
        return "v2/exam/class-list";
    }

    @GetMapping("/record/{studentId}")
    public String studentExamRecords(@PathVariable Long studentId, Model model) {
        Student student = studentService.findById(studentId);
        List<ExamRecord> records = examService.findByStudent(student);
        model.addAttribute("student", student);
        model.addAttribute("records", records);
        return "v2/exam/student-history";
    }

    @GetMapping("/add/{studentId}")
    public String addExamForm(@PathVariable Long studentId,
            @RequestParam(required = false) String examName,
            Model model) {
        Student student = studentService.findById(studentId);
        ExamRecord record = examService.createExamRecord(student,
                examName != null ? examName : "期末考试成绩",
                LocalDate.now());
        model.addAttribute("record", record);
        model.addAttribute("student", student);
        return "v2/exam/add-form";
    }

    @PostMapping("/save")
    public String saveExam(@ModelAttribute ExamRecord record,
            RedirectAttributes redirectAttributes) {
        // 计算总分
        BigDecimal totalScore = examService.calculateTotalScore(record);
        record.setTotalScore(totalScore);

        // 判断是否及格（假设60分及格）
        BigDecimal passScore = new BigDecimal("60.0");
        record.setIsPassed(totalScore != null && totalScore.compareTo(passScore) >= 0);

        examService.save(record);

        // 更新排名
        if (record.getStudent() != null && record.getStudent().getSchoolClass() != null) {
            examService.updateRankings(record.getExamName(), record.getStudent().getSchoolClass().getId());
        }

        redirectAttributes.addFlashAttribute("success", "考试成绩保存成功！");
        return "redirect:/v2/exam/record/" + record.getStudent().getId();
    }

    @GetMapping("/edit/{id}")
    public String editExamForm(@PathVariable Long id, Model model) {
        ExamRecord record = examService.findById(id);
        model.addAttribute("record", record);
        return "v2/exam/edit-form";
    }

    @PostMapping("/update")
    public String updateExam(@ModelAttribute ExamRecord record,
            RedirectAttributes redirectAttributes) {
        // 重新计算总分和及格状态
        BigDecimal totalScore = examService.calculateTotalScore(record);
        record.setTotalScore(totalScore);

        BigDecimal passScore = new BigDecimal("60.0");
        record.setIsPassed(totalScore != null && totalScore.compareTo(passScore) >= 0);

        examService.save(record);

        // 更新排名
        if (record.getStudent() != null && record.getStudent().getSchoolClass() != null) {
            examService.updateRankings(record.getExamName(), record.getStudent().getSchoolClass().getId());
        }

        redirectAttributes.addFlashAttribute("success", "成绩更新成功！");
        return "redirect:/v2/exam/record/" + record.getStudent().getId();
    }

    @PostMapping("/delete/{id}")
    public String deleteExam(@PathVariable Long id,
            @RequestParam Long studentId,
            RedirectAttributes redirectAttributes) {
        examService.delete(id);
        redirectAttributes.addFlashAttribute("success", "成绩记录删除成功！");
        return "redirect:/v2/exam/record/" + studentId;
    }

    @GetMapping("/statistics/{classId}")
    public String examStatistics(@PathVariable Long classId,
            @RequestParam String examName,
            Model model) {
        List<ExamRecord> records = examService.findByClassId(classId);
        // TODO: 实现统计计算逻辑
        model.addAttribute("records", records);
        model.addAttribute("examName", examName);
        return "v2/exam/statistics";
    }

    @GetMapping("/import")
    public String importForm() {
        return "v2/exam/import-form";
    }

    @PostMapping("/import")
    public String importData(@RequestParam("file") String fileData,
            RedirectAttributes redirectAttributes) {
        // TODO: 实现Excel导入逻辑
        redirectAttributes.addFlashAttribute("success", "成绩数据导入成功！");
        return "redirect:/v2/exam";
    }
}