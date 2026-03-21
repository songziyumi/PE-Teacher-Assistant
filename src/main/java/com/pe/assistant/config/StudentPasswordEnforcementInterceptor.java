package com.pe.assistant.config;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class StudentPasswordEnforcementInterceptor implements HandlerInterceptor {

    private final CurrentUserService currentUserService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/student")) {
            return true;
        }
        if (uri.startsWith("/student/password")) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return true;
        }
        boolean isStudent = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_STUDENT".equals(a.getAuthority()));
        if (!isStudent) {
            return true;
        }

        StudentAccount account = currentUserService.getCurrentStudentAccount();
        if (account != null && Boolean.TRUE.equals(account.getPasswordResetRequired())) {
            response.sendRedirect("/student/password?force=true");
            return false;
        }
        return true;
    }
}
