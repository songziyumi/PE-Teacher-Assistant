package com.pe.assistant.service;

import com.pe.assistant.entity.TeachingResource;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeachingResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final TeachingResourceRepository teachingResourceRepository;

    public TeachingResource save(TeachingResource resource) {
        return teachingResourceRepository.save(resource);
    }

    public TeachingResource findById(Long id) {
        return teachingResourceRepository.findById(id).orElse(null);
    }

    public List<TeachingResource> findByUploader(Teacher uploader) {
        return teachingResourceRepository.findByUploader(uploader);
    }

    public List<TeachingResource> findPublicResources() {
        return teachingResourceRepository.findByIsPublicTrue();
    }

    public List<TeachingResource> findAvailableResources(Teacher teacher) {
        return teachingResourceRepository.findAvailableResources(teacher);
    }

    @Transactional
    public void delete(Long id) {
        teachingResourceRepository.deleteById(id);
    }

    @Transactional
    public void incrementDownloadCount(Long id) {
        TeachingResource resource = findById(id);
        if (resource != null) {
            resource.setDownloadCount(resource.getDownloadCount() + 1);
            teachingResourceRepository.save(resource);
        }
    }

    @Transactional
    public void incrementViewCount(Long id) {
        TeachingResource resource = findById(id);
        if (resource != null) {
            resource.setViewCount(resource.getViewCount() + 1);
            teachingResourceRepository.save(resource);
        }
    }

    public TeachingResource createResource(Teacher uploader, String title, String description) {
        TeachingResource resource = new TeachingResource();
        resource.setTitle(title);
        resource.setDescription(description);
        resource.setUploader(uploader);
        resource.setIsPublic(false); // 默认私有
        return resource;
    }

    // 处理文件上传（简化版，实际需要集成云存储）
    public String handleFileUpload(MultipartFile file) throws IOException {
        // TODO: 集成腾讯云COS或其他云存储
        // 这里返回一个模拟的URL
        return "/uploads/" + file.getOriginalFilename();
    }

    // 获取文件类型
    public String getFileType(String filename) {
        if (filename == null)
            return "unknown";

        filename = filename.toLowerCase();
        if (filename.endsWith(".pdf"))
            return "pdf";
        if (filename.endsWith(".doc") || filename.endsWith(".docx"))
            return "word";
        if (filename.endsWith(".ppt") || filename.endsWith(".pptx"))
            return "ppt";
        if (filename.endsWith(".xls") || filename.endsWith(".xlsx"))
            return "excel";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".png"))
            return "image";
        if (filename.endsWith(".mp4") || filename.endsWith(".avi") || filename.endsWith(".mov"))
            return "video";

        return "other";
    }

    // 统计教师相关的教学资源数量
    public Long countByTeacher(Teacher teacher) {
        // 统计教师上传的资源
        return (long) teachingResourceRepository.findByUploader(teacher).size();
    }
}