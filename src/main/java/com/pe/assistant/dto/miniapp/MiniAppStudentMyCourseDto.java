package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppStudentMyCourseDto {
    private Long id;
    private Long courseId;
    private String courseName;
    private String teacherName;
    private String eventName;
    private Integer preference;
    private Integer round;
    private String roundLabel;
    private String status;
    private LocalDateTime selectedAt;
    private LocalDateTime confirmedAt;
    private Boolean canDrop;
}
