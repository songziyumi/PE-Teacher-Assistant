package com.pe.assistant.controller.api.miniapp;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.PageDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherCourseRequestDashboardDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherHomeDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherStudentDto;
import com.pe.assistant.service.MiniAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/teacher")
@RequiredArgsConstructor
public class MiniAppTeacherApiController {

    private final MiniAppService miniAppService;

    @GetMapping("/dashboard")
    public ApiResponse<MiniAppTeacherHomeDto> dashboard() {
        return ApiResponse.ok(miniAppService.buildTeacherHome());
    }

    @GetMapping("/classes/{classId}/students")
    public ApiResponse<PageDto<MiniAppTeacherStudentDto>> students(@PathVariable Long classId,
                                                                   @RequestParam(defaultValue = "") String keyword,
                                                                   @RequestParam(required = false) String studentStatus,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(miniAppService.listTeacherClassStudents(classId, keyword, studentStatus, page, size));
    }

    @GetMapping("/course-requests/dashboard")
    public ApiResponse<MiniAppTeacherCourseRequestDashboardDto> courseRequestDashboard() {
        return ApiResponse.ok(miniAppService.buildTeacherCourseRequestDashboard());
    }
}
