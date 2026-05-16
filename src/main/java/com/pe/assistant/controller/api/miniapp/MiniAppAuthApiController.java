package com.pe.assistant.controller.api.miniapp;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.LoginRequest;
import com.pe.assistant.dto.miniapp.MiniAppAuthResponse;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.security.LoginAttemptService;
import com.pe.assistant.security.LoginPrincipalResolver;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.MiniAppService;
import com.pe.assistant.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/auth")
@RequiredArgsConstructor
public class MiniAppAuthApiController {

    private final AuthenticationManager authenticationManager;
    private final CurrentUserService currentUserService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final LoginPrincipalResolver loginPrincipalResolver;
    private final MiniAppService miniAppService;
    private final StudentAccountService studentAccountService;

    @PostMapping("/login")
    public ApiResponse<MiniAppAuthResponse> login(@RequestBody LoginRequest request) {
        String loginInput = request.getUsername() != null ? request.getUsername().trim() : "";
        String attemptKey = loginPrincipalResolver.resolveAttemptKey(loginInput);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
            if (!attemptKey.isBlank()) {
                loginAttemptService.loginSucceeded(attemptKey);
            }

            Teacher teacher = loginPrincipalResolver.findTeacher(authentication.getName()).orElse(null);
            if (teacher != null) {
                Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
                String token = jwtUtil.generateToken(teacher.getUsername(), teacher.getRole(), schoolId);
                return ApiResponse.ok(MiniAppAuthResponse.builder()
                        .token(token)
                        .user(miniAppService.toTeacherUser(teacher))
                        .mustChangePassword(false)
                        .build());
            }

            StudentAccount account = studentAccountService.resolvePrincipal(authentication.getName()).orElseThrow();
            studentAccountService.markLoginSuccess(account);
            Long schoolId = account.getStudent() != null && account.getStudent().getSchool() != null
                    ? account.getStudent().getSchool().getId()
                    : null;
            String token = jwtUtil.generateToken("student-account:" + account.getId(), "STUDENT", schoolId);
            return ApiResponse.ok(MiniAppAuthResponse.builder()
                    .token(token)
                    .user(miniAppService.toStudentUser(account))
                    .mustChangePassword(studentAccountService.requiresPasswordChange(account))
                    .build());
        } catch (LockedException ex) {
            return ApiResponse.error(401, ex.getMessage());
        } catch (BadCredentialsException ex) {
            if (!attemptKey.isBlank()) {
                loginAttemptService.loginFailed(attemptKey);
            }
            return ApiResponse.error(401, "Invalid username or password");
        } catch (Exception ex) {
            return ApiResponse.error(401, "Login failed");
        }
    }

    @GetMapping("/me")
    public ApiResponse<MiniAppAuthResponse> me() {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            return ApiResponse.ok(MiniAppAuthResponse.builder()
                    .user(miniAppService.toTeacherUser(teacher))
                    .mustChangePassword(false)
                    .build());
        } catch (Exception ignored) {
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            return ApiResponse.ok(MiniAppAuthResponse.builder()
                    .user(miniAppService.toStudentUser(account))
                    .mustChangePassword(studentAccountService.requiresPasswordChange(account))
                    .build());
        }
    }
}
