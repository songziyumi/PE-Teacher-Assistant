package com.pe.assistant.controller.api;

import com.pe.assistant.dto.ApiResponse;
import com.pe.assistant.dto.LoginRequest;
import com.pe.assistant.dto.LoginResponse;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthApiController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CurrentUserService currentUserService;
    private final TeacherRepository teacherRepository;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
            // UserDetailsServiceImpl returns Spring Security User, load Teacher separately
            Teacher teacher = teacherRepository.findByUsername(auth.getName()).orElseThrow();
            Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
            String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
            String token = jwtUtil.generateToken(teacher.getUsername(), teacher.getRole(), schoolId);
            return ApiResponse.ok(new LoginResponse(token, teacher.getUsername(),
                    teacher.getName(), teacher.getRole(), schoolId, schoolName));
        } catch (BadCredentialsException e) {
            return ApiResponse.error(401, "用户名或密码错误");
        } catch (Exception e) {
            return ApiResponse.error(401, e.getMessage());
        }
    }

    @GetMapping("/me")
    public ApiResponse<LoginResponse> me() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
        String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
        return ApiResponse.ok(new LoginResponse(null, teacher.getUsername(),
                teacher.getName(), teacher.getRole(), schoolId, schoolName));
    }
}
