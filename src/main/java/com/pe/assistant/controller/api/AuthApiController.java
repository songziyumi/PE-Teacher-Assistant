package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.LoginRequest;
import com.pe.assistant.dto.LoginResponse;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.security.LoginAttemptService;
import com.pe.assistant.security.LoginPrincipalResolver;
import com.pe.assistant.service.CurrentUserService;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CurrentUserService currentUserService;
    private final StudentAccountService studentAccountService;
    private final LoginAttemptService loginAttemptService;
    private final LoginPrincipalResolver loginPrincipalResolver;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        String loginInput = req.getUsername() != null ? req.getUsername().trim() : "";
        String attemptKey = loginPrincipalResolver.resolveAttemptKey(loginInput);
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            if (!attemptKey.isBlank()) {
                loginAttemptService.loginSucceeded(attemptKey);
            }

            Teacher teacher = loginPrincipalResolver.findTeacher(auth.getName()).orElse(null);
            if (teacher != null) {
                Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
                String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
                String token = jwtUtil.generateToken(teacher.getUsername(), teacher.getRole(), schoolId);
                return ApiResponse.ok(new LoginResponse(
                        token,
                        teacher.getUsername(),
                        null,
                        teacher.getName(),
                        teacher.getRole(),
                        schoolId,
                        schoolName,
                        false));
            }

            StudentAccount account = studentAccountService.resolvePrincipal(auth.getName()).orElseThrow();
            studentAccountService.markLoginSuccess(account);
            Student student = account.getStudent();
            Long schoolId = student.getSchool() != null ? student.getSchool().getId() : null;
            String schoolName = student.getSchool() != null ? student.getSchool().getName() : null;
            String token = jwtUtil.generateToken("student-account:" + account.getId(), "STUDENT", schoolId);
            return ApiResponse.ok(new LoginResponse(
                    token,
                    account.getLoginId(),
                    account.getLoginAlias(),
                    student.getName(),
                    "STUDENT",
                    schoolId,
                    schoolName,
                    studentAccountService.requiresPasswordChange(account)));
        } catch (LockedException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (BadCredentialsException e) {
            if (!attemptKey.isBlank()) {
                loginAttemptService.loginFailed(attemptKey);
            }
            return ApiResponse.error(401, "账号或密码错误");
        } catch (Exception e) {
            return ApiResponse.error(401, "登录失败，请联系管理员检查学生账号数据");
        }
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        try {
            Teacher teacher = currentUserService.getCurrentTeacher();
            Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
            String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
            return ApiResponse.ok(new LoginResponse(
                    null,
                    teacher.getUsername(),
                    null,
                    teacher.getName(),
                    teacher.getRole(),
                    schoolId,
                    schoolName,
                    false));
        } catch (Exception ignored) {
            Student student = currentUserService.getCurrentStudent();
            StudentAccount account = currentUserService.getCurrentStudentAccount();
            Long schoolId = student.getSchool() != null ? student.getSchool().getId() : null;
            String schoolName = student.getSchool() != null ? student.getSchool().getName() : null;
            return ApiResponse.ok(new LoginResponse(
                    null,
                    account != null ? account.getLoginId() : null,
                    account != null ? account.getLoginAlias() : null,
                    student.getName(),
                    "STUDENT",
                    schoolId,
                    schoolName,
                    studentAccountService.requiresPasswordChange(account)));
        }
    }
}
