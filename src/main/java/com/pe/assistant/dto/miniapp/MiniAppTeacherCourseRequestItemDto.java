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
public class MiniAppTeacherCourseRequestItemDto {
    private Long id;
    private String studentName;
    private Long studentId;
    private String courseName;
    private Long courseId;
    private String status;
    private String content;
    private String handleRemark;
    private LocalDateTime sentAt;
    private LocalDateTime handledAt;
}
