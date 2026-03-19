package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.LoginRequest;
import com.pe.assistant.dto.LoginResponse;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.service.CurrentUserService;
import com.pe.assistant.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final TeacherRepository teacherRepository;
    private final StudentService studentService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

            Teacher teacher = teacherRepository.findByUsername(auth.getName()).orElse(null);
            if (teacher != null) {
                Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
                String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
                String token = jwtUtil.generateToken(teacher.getUsername(), teacher.getRole(), schoolId);
                return ApiResponse.ok(new LoginResponse(
                        token,
                        teacher.getUsername(),
                        teacher.getName(),
                        teacher.getRole(),
                        schoolId,
                        schoolName,
                        false));
            }

            Student student = studentService.resolveLoginStudent(auth.getName()).orElseThrow();
            Long schoolId = student.getSchool() != null ? student.getSchool().getId() : null;
            String schoolName = student.getSchool() != null ? student.getSchool().getName() : null;
            String token = jwtUtil.generateToken("student:" + student.getId(), "STUDENT", schoolId);
            boolean mustChangePassword = isStudentUsingInitialPassword(student);
            return ApiResponse.ok(new LoginResponse(
                    token,
                    student.getStudentNo(),
                    student.getName(),
                    "STUDENT",
                    schoolId,
                    schoolName,
                    mustChangePassword));
        } catch (BadCredentialsException e) {
            return ApiResponse.error(401, "用户名或密码错误");
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
                    teacher.getName(),
                    teacher.getRole(),
                    schoolId,
                    schoolName,
                    false));
        } catch (Exception ignored) {
            Student student = currentUserService.getCurrentStudent();
            Long schoolId = student.getSchool() != null ? student.getSchool().getId() : null;
            String schoolName = student.getSchool() != null ? student.getSchool().getName() : null;
            boolean mustChangePassword = isStudentUsingInitialPassword(student);
            return ApiResponse.ok(new LoginResponse(
                    null,
                    student.getStudentNo(),
                    student.getName(),
                    "STUDENT",
                    schoolId,
                    schoolName,
                    mustChangePassword));
        }
    }

    private boolean isStudentUsingInitialPassword(Student student) {
        if (student == null || student.getStudentNo() == null || student.getPassword() == null) {
            return false;
        }
        try {
            return passwordEncoder.matches(student.getStudentNo(), student.getPassword());
        } catch (Exception ignored) {
            return false;
        }
    }
}
