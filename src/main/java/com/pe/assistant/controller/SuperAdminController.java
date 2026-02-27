package com.pe.assistant.controller;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;

@Controller
@RequestMapping("/super-admin")
@RequiredArgsConstructor
public class SuperAdminController {

    private final SchoolService schoolService;

    @GetMapping
    public String index() {
        return "redirect:/super-admin/schools";
    }

    @GetMapping("/schools")
    public String schools(Model model) {
        List<School> schools = schoolService.findAll();
        model.addAttribute("schools", schools);
        // 每个学校的管理员
        java.util.Map<Long, Teacher> adminMap = new java.util.HashMap<>();
        for (School s : schools) {
            Teacher admin = schoolService.findAdminBySchool(s);
            if (admin != null) adminMap.put(s.getId(), admin);
        }
        model.addAttribute("adminMap", adminMap);
        return "super-admin/schools";
    }

    @GetMapping("/schools/add")
    public String addSchoolForm() {
        return "super-admin/school-form";
    }

    @PostMapping("/schools/add")
    public String addSchool(@RequestParam String name,
                            @RequestParam String code,
                            @RequestParam(required = false) String address,
                            @RequestParam(required = false) String contactPhone,
                            RedirectAttributes ra) {
        try {
            schoolService.create(name, code, address, contactPhone);
            ra.addFlashAttribute("success", "学校「" + name + "」创建成功");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/super-admin/schools";
    }

    @GetMapping("/schools/{id}/edit")
    public String editSchoolForm(@PathVariable Long id, Model model) {
        model.addAttribute("school", schoolService.findById(id));
        return "super-admin/school-edit-form";
    }

    @PostMapping("/schools/{id}/edit")
    public String editSchool(@PathVariable Long id,
                             @RequestParam String name,
                             @RequestParam String code,
                             @RequestParam(required = false) String address,
                             @RequestParam(required = false) String contactPhone,
                             RedirectAttributes ra) {
        try {
            schoolService.update(id, name, code, address, contactPhone);
            ra.addFlashAttribute("success", "学校信息已更新");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/super-admin/schools";
    }

    @PostMapping("/schools/{id}/toggle")
    public String toggleSchool(@PathVariable Long id, RedirectAttributes ra) {
        try {
            schoolService.toggleEnabled(id);
            ra.addFlashAttribute("success", "学校状态已切换");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败：" + e.getMessage());
        }
        return "redirect:/super-admin/schools";
    }

    @PostMapping("/schools/{id}/delete")
    public String deleteSchool(@PathVariable Long id, RedirectAttributes ra) {
        try {
            schoolService.delete(id);
            ra.addFlashAttribute("success", "学校已删除");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/super-admin/schools";
    }

    @GetMapping("/schools/{id}/admin")
    public String adminForm(@PathVariable Long id, Model model) {
        School school = schoolService.findById(id);
        Teacher existingAdmin = schoolService.findAdminBySchool(school);
        model.addAttribute("school", school);
        model.addAttribute("existingAdmin", existingAdmin);
        return "super-admin/admin-form";
    }

    @PostMapping("/schools/{id}/admin")
    public String createAdmin(@PathVariable Long id,
                              @RequestParam String username,
                              @RequestParam String password,
                              RedirectAttributes ra) {
        try {
            schoolService.createOrResetAdmin(id, username, password);
            ra.addFlashAttribute("success", "管理员账号已创建/重置");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/super-admin/schools";
    }
}
