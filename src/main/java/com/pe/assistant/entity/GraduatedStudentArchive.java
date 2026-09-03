package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "graduated_student_archives", uniqueConstraints =
        @UniqueConstraint(name = "uk_graduated_archive_student_year", columnNames = {"student_id", "graduation_year"}))
public class GraduatedStudentArchive {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Column(name = "graduation_year", nullable = false)
    private Integer graduationYear;
    @Column(name = "grade_name", length = 50)
    private String gradeName;
    @Column(name = "class_name", length = 100)
    private String className;
    @Column(name = "archived_at", nullable = false)
    private LocalDate archivedAt = LocalDate.now();
}
