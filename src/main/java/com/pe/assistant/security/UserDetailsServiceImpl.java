package com.pe.assistant.security;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final TeacherRepository teacherRepository;
    private final StudentService studentService;
    private final LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (loginAttemptService.isBlocked(username)) {
            throw new LockedException("账号已被锁定，请 15 分钟后再试");
        }
        Teacher teacher = teacherRepository.findByUsername(username).orElse(null);
        if (teacher != null) {
            return new org.springframework.security.core.userdetails.User(
                    teacher.getUsername(),
                    teacher.getPassword(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + teacher.getRole()))
            );
        }
        Student student = studentService.resolveStudentPrincipal(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        if (student.getPassword() == null || student.getPassword().isBlank()) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(student.getEnabled())) {
            throw new LockedException("账号已被禁用");
        }
        return new org.springframework.security.core.userdetails.User(
                username.startsWith("student:") ? username : student.getStudentNo(),
                student.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
