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
public class MiniAppTeacherCourseRequestDashboardDto {
    private Long pendingCount;
    private Long approvedCount;
    private Long rejectedCount;
    private List<MiniAppTeacherCourseRequestItemDto> pendingItems;
    private List<MiniAppTeacherActivityDto> recentActivities;
}
