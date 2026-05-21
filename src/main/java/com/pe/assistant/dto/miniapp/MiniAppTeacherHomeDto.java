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
public class MiniAppTeacherHomeDto {
    private MiniAppUserDto user;
    private Integer classCount;
    private Long pendingCourseRequestCount;
    private Long unreadMessageCount;
    private List<MiniAppTeacherClassDto> classes;
    private List<MiniAppMessageItemDto> recentMessages;
    private List<MiniAppTeacherActivityDto> recentActivities;
}
