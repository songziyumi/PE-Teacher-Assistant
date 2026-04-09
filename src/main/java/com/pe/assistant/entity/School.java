package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "schools")
public class School {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(length = 200)
    private String address;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "show_suspended_on_teacher_page", nullable = false)
    private Boolean showSuspendedOnTeacherPage = true;

    @Column(name = "show_outgoing_borrow_on_teacher_page", nullable = false)
    private Boolean showOutgoingBorrowOnTeacherPage = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}