package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "teachers")
public class Teacher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String role; // ADMIN or TEACHER

    @Column(length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id")
    private School school; // SUPER_ADMIN 为 null

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ===== 个人主页字段 =====

    @Column(length = 10)
    private String gender;           // 男/女

    @Column(name = "birth_date")
    private LocalDate birthDate;     // 前端计算年龄展示

    @Column(length = 100)
    private String specialty;        // 专业特长

    @Column(length = 100)
    private String email;

    @Column(name = "photo_url", length = 200)
    private String photoUrl;         // 如 /uploads/teachers/3.jpg

    @Column(columnDefinition = "TEXT")
    private String bio;              // 个人简介
}
