package com.pe.assistant.controller.v2;

import com.pe.assistant.entity.Teacher;
import com.pe.assistant.entity.TeachingResource;
import com.pe.assistant.service.ResourceService;
import com.pe.assistant.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/v2/resources")
@RequiredArgsConstructor
public class ResourceController {
    
    private final ResourceService resourceService;
    private final TeacherService teacherService;
    
    @GetMapping
    public String resourceLibrary(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam(required = false) String category,
                                 Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        List<TeachingResource> resources;
        
        if (category != null && !category.isEmpty()) {
            resources = resourceService.findAvailableResources(teacher);
            // TODO: 按分类筛选
        } else {
            resources = resourceService.findAvailableResources(teacher);
        }
        
        model.addAttribute("teacher", teacher);
        model.addAttribute("resources", resources);
        model.addAttribute("categories", getResourceCategories());
        return "v2/resources/library";
    }
    
    @GetMapping("/my")
    public String myResources(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        List<TeachingResource> resources = resourceService.findByUploader(teacher);
        model.addAttribute("teacher", teacher);
        model.addAttribute("resources", resources);
        return "v2/resources/my-resources";
    }
    
    @GetMapping("/public")
    public String publicResources(Model model) {
        List<TeachingResource> resources = resourceService.findPublicResources();
        model.addAttribute("resources", resources);
        return "v2/resources/public-resources";
    }
    
    @GetMapping("/add")
    public String addResourceForm(Model model) {
        model.addAttribute("resource", new TeachingResource());
        model.addAttribute("categories", getResourceCategories());
        model.addAttribute("subjects", getSportSubjects());
        model.addAttribute("gradeLevels", getGradeLevels());
        return "v2/resources/add-form";
    }
    
    @PostMapping("/save")
    public String saveResource(@ModelAttribute TeachingResource resource,
                              @RequestParam(value = "file", required = false) MultipartFile file,
                              @AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes redirectAttributes) throws IOException {
        
        Teacher teacher = teacherService.findByUsername(userDetails.getUsername());
        resource.setUploader(teacher);
        
        if (file != null && !file.isEmpty()) {
            // 处理文件上传
            String fileUrl = resourceService.handleFileUpload(file);
            resource.setFileUrl(fileUrl);
            resource.setFileType(resourceService.getFileType(file.getOriginalFilename()));
            resource.setFileSize(file.getSize());
        }
        
        resourceService.save(resource);
        redirectAttributes.addFlashAttribute("success", "教学资源保存成功！");
        return "redirect:/v2/resources/my";
    }
    
    @GetMapping("/edit/{id}")
    public String editResourceForm(@PathVariable Long id, Model model) {
        TeachingResource resource = resourceService.findById(id);
        model.addAttribute("resource", resource);
        model.addAttribute("categories", getResourceCategories());
        model.addAttribute("subjects", getSportSubjects());
        model.addAttribute("gradeLevels", getGradeLevels());
        return "v2/resources/edit-form";
    }
    
    @PostMapping("/update")
    public String updateResource(@ModelAttribute TeachingResource resource,
                                @RequestParam(value = "file", required = false) MultipartFile file,
                                RedirectAttributes redirectAttributes) throws IOException {
        
        TeachingResource existingResource = resourceService.findById(resource.getId());
        if (existingResource != null) {
            // 保留原有上传者信息
            resource.setUploader(existingResource.getUploader());
            
            if (file != null && !file.isEmpty()) {
                // 更新文件
                String fileUrl = resourceService.handleFileUpload(file);
                resource.setFileUrl(fileUrl);
                resource.setFileType(resourceService.getFileType(file.getOriginalFilename()));
                resource.setFileSize(file.getSize());
            } else {
                // 保留原有文件信息
                resource.setFileUrl(existingResource.getFileUrl());
                resource.setFileType(existingResource.getFileType());
                resource.setFileSize(existingResource.getFileSize());
            }
            
            resourceService.save(resource);
            redirectAttributes.addFlashAttribute("success", "资源更新成功！");
        }
        
        return "redirect:/v2/resources/my";
    }
    
    @PostMapping("/delete/{id}")
    public String deleteResource(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        resourceService.delete(id);
        redirectAttributes.addFlashAttribute("success", "资源删除成功！");
        return "redirect:/v2/resources/my";
    }
    
    @GetMapping("/view/{id}")
    public String viewResource(@PathVariable Long id, Model model) {
        TeachingResource resource = resourceService.findById(id);
        if (resource != null) {
            resourceService.incrementViewCount(id);
            model.addAttribute("resource", resource);
            return "v2/resources/view";
        }
        return "redirect:/v2/resources";
    }
    
    @GetMapping("/download/{id}")
    public String downloadResource(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        TeachingResource resource = resourceService.findById(id);
        if (resource != null) {
            resourceService.incrementDownloadCount(id);
            // TODO: 实现文件下载逻辑
            redirectAttributes.addFlashAttribute("success", "开始下载：" + resource.getTitle());
        }
        return "redirect:/v2/resources/view/" + id;
    }
    
    // 辅助方法
    private String[] getResourceCategories() {
        return new String[] {"教案", "课件", "训练计划", "微课", "教学视频", "测试题", "其他"};
    }
    
    private String[] getSportSubjects() {
        return new String[] {"篮球", "足球", "排球", "乒乓球", "羽毛球", "田径", "体操", "武术", "游泳", "其他"};
    }
    
    private String[] getGradeLevels() {
        return new String[] {"一年级", "二年级", "三年级", "四年级", "五年级", "六年级", 
                           "七年级", "八年级", "九年级", "高一", "高二", "高三", "通用"};
    }
}