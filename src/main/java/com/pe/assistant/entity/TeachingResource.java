package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Table(name = "teaching_resources")
@Data
public class TeachingResource {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "title", length = 200, nullable = false)
    private String title; // 资源标题
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 资源描述
    
    @Column(name = "file_url", length = 500)
    private String fileUrl; // 文件URL
    
    @Column(name = "file_type", length = 50)
    private String fileType; // 文件类型：pdf, doc, ppt, video等
    
    @Column(name = "file_size")
    private Long fileSize; // 文件大小(字节)
    
    @Column(name = "category", length = 50)
    private String category; // 分类：教案/课件/训练计划/微课等
    
    @Column(name = "subject", length = 50)
    private String subject; // 科目：篮球/足球/田径等
    
    @Column(name = "grade_level", length = 20)
    private String gradeLevel; // 适用年级
    
    @Column(name = "is_public")
    private Boolean isPublic = false; // 是否公开
    
    @ManyToOne
    @JoinColumn(name = "uploader_id")
    private Teacher uploader; // 上传者
    
    @Column(name = "download_count")
    private Integer downloadCount = 0; // 下载次数
    
    @Column(name = "view_count")
    private Integer viewCount = 0; // 查看次数
    
    @Column(name = "created_at")
    private LocalDate createdAt;
    
    @Column(name = "updated_at")
    private LocalDate updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }
}