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
public class MiniAppStudentHomeDto {
    private MiniAppUserDto user;
    private MiniAppEventSummaryDto currentEvent;
    private Integer currentCourseCount;
    private Integer mySelectionCount;
    private Integer confirmedCourseCount;
    private Integer pendingSelectionCount;
    private Integer requestableCourseCount;
    private Long unreadMessageCount;
    private List<MiniAppMessageItemDto> recentMessages;
    private Boolean mustChangePassword;
}
