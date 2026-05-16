package com.pe.assistant.controller.api.miniapp;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.miniapp.MiniAppStudentCourseDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentHomeDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentMessageSummaryDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentMyCourseDto;
import com.pe.assistant.service.MiniAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/miniapp/student")
@RequiredArgsConstructor
public class MiniAppStudentApiController {

    private final MiniAppService miniAppService;

    @GetMapping("/dashboard")
    public ApiResponse<MiniAppStudentHomeDto> dashboard() {
        return ApiResponse.ok(miniAppService.buildStudentHome());
    }

    @GetMapping("/courses")
    public ApiResponse<List<MiniAppStudentCourseDto>> courses() {
        return ApiResponse.ok(miniAppService.listStudentCourses());
    }

    @GetMapping("/my-courses")
    public ApiResponse<List<MiniAppStudentMyCourseDto>> myCourses() {
        return ApiResponse.ok(miniAppService.listStudentMyCourses());
    }

    @GetMapping("/messages/summary")
    public ApiResponse<MiniAppStudentMessageSummaryDto> messageSummary() {
        return ApiResponse.ok(miniAppService.buildStudentMessageSummary());
    }
}
