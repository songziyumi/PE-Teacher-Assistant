package com.pe.assistant.controller.v2;

import com.pe.assistant.entity.HealthTestRecord;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.HealthTestService;
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
@RequestMapping("/v2/health-test")
@RequiredArgsConstructor
public class HealthTestController {

    private final HealthTestService healthTestService;
    private final TeacherService teacherService;
    private final StudentService studentService;

    @GetMapping
    public String healthTestDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        model.addAttribute("teacher", teacher);
        return "v2/health-test/dashboard";
    }

    @GetMapping("/class/{classId}")
    public String classHealthTest(@PathVariable Long classId, Model model) {
        List<HealthTestRecord> records = healthTestService.findByClassId(classId);
        model.addAttribute("records", records);
        return "v2/health-test/class-list";
    }

    @GetMapping("/record/{studentId}")
    public String studentHealthTest(@PathVariable Long studentId, Model model) {
        Student student = studentService.findById(studentId);
        List<HealthTestRecord> records = healthTestService.findByStudent(student);
        model.addAttribute("student", student);
        model.addAttribute("records", records);
        return "v2/health-test/student-history";
    }

    @GetMapping("/add/{studentId}")
    public String addHealthTestForm(@PathVariable Long studentId, Model model) {
        Student student = studentService.findById(studentId);
        HealthTestRecord record = healthTestService.createHealthTestRecord(student, LocalDate.now());
        model.addAttribute("record", record);
        model.addAttribute("student", student);
        return "v2/health-test/add-form";
    }

    @PostMapping("/save")
    public String saveHealthTest(@ModelAttribute HealthTestRecord record,
            RedirectAttributes redirectAttributes) {
        // 计算BMI
        if (record.getHeight() != null && record.getWeight() != null) {
            BigDecimal bmi = healthTestService.calculateBMI(record.getHeight(), record.getWeight());
            record.setBmi(bmi);
        }

        // 计算等级
        String gradeLevel = healthTestService.calculateGradeLevel(record);
        record.setGradeLevel(gradeLevel);

        healthTestService.save(record);
        redirectAttributes.addFlashAttribute("success", "体质健康测试记录保存成功！");
        return "redirect:/v2/health-test/record/" + record.getStudent().getId();
    }

    @GetMapping("/edit/{id}")
    public String editHealthTestForm(@PathVariable Long id, Model model) {
        HealthTestRecord record = healthTestService.findById(id);
        model.addAttribute("record", record);
        return "v2/health-test/edit-form";
    }

    @PostMapping("/update")
    public String updateHealthTest(@ModelAttribute HealthTestRecord record,
            RedirectAttributes redirectAttributes) {
        // 重新计算BMI和等级
        if (record.getHeight() != null && record.getWeight() != null) {
            BigDecimal bmi = healthTestService.calculateBMI(record.getHeight(), record.getWeight());
            record.setBmi(bmi);
        }

        String gradeLevel = healthTestService.calculateGradeLevel(record);
        record.setGradeLevel(gradeLevel);

        healthTestService.save(record);
        redirectAttributes.addFlashAttribute("success", "记录更新成功！");
        return "redirect:/v2/health-test/record/" + record.getStudent().getId();
    }

    @PostMapping("/delete/{id}")
    public String deleteHealthTest(@PathVariable Long id,
            @RequestParam Long studentId,
            RedirectAttributes redirectAttributes) {
        healthTestService.delete(id);
        redirectAttributes.addFlashAttribute("success", "记录删除成功！");
        return "redirect:/v2/health-test/record/" + studentId;
    }

    @GetMapping("/import")
    public String importForm() {
        return "v2/health-test/import-form";
    }

    @PostMapping("/import")
    public String importData(@RequestParam("file") String fileData,
            RedirectAttributes redirectAttributes) {
        // TODO: 实现Excel导入逻辑
        redirectAttributes.addFlashAttribute("success", "数据导入成功！");
        return "redirect:/v2/health-test";
    }
}