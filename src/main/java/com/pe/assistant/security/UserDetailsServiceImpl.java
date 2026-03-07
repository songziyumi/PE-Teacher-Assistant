package com.pe.assistant.security;

import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final TeacherRepository teacherRepository;
    private final StudentRepository studentRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (loginAttemptService.isBlocked(username)) {
            throw new LockedException("账号已被锁定，请 15 分钟后再试");
        }
        // 先查教师/管理员，再查学生（学生用学号登录）
        Teacher teacher = teacherRepository.findByUsername(username).orElse(null);
        if (teacher != null) {
            return new org.springframework.security.core.userdetails.User(
                teacher.getUsername(),
                teacher.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + teacher.getRole()))
            );
        }
        Student student = studentRepository.findByStudentNo(username)
            .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        if (student.getPassword() == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        if (Boolean.FALSE.equals(student.getEnabled())) {
            throw new LockedException("账号已被禁用");
        }
        return new org.springframework.security.core.userdetails.User(
            student.getStudentNo(),
            student.getPassword(),
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }
}
