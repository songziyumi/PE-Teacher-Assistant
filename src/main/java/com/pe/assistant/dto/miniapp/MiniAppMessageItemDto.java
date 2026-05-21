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
public class MiniAppMessageItemDto {
    private Long id;
    private String subject;
    private String content;
    private String type;
    private String status;
    private Boolean isRead;
    private LocalDateTime sentAt;
    private String senderType;
    private Long senderId;
    private String senderName;
    private Long relatedCourseId;
    private String relatedCourseName;
}
