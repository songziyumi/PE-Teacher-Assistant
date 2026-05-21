package com.pe.assistant.controller.api.miniapp;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.service.MiniAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class MiniAppCommonApiController {

    private final MiniAppService miniAppService;

    @GetMapping("/home")
    public ApiResponse<?> home() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority()));
        if (isStudent) {
            return ApiResponse.ok(miniAppService.buildStudentHome());
        }
        return ApiResponse.ok(miniAppService.buildTeacherHome());
    }
}
