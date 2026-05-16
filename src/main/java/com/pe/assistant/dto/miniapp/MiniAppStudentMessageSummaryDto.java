package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppStudentMessageSummaryDto {
    private Long unreadCount;
    private Long pendingCourseRequestCount;
    private List<MiniAppMessageItemDto> recentMessages;
}
