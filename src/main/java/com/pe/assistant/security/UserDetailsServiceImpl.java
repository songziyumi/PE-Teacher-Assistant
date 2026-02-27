package com.pe.assistant.security;

import com.pe.assistant.entity.Teacher;
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
    private final LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (loginAttemptService.isBlocked(username)) {
            throw new LockedException("账号已被锁定，请 15 分钟后再试");
        }
        Teacher teacher = teacherRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        return new org.springframework.security.core.userdetails.User(
            teacher.getUsername(),
            teacher.getPassword(),
            List.of(new SimpleGrantedAuthority("ROLE_" + teacher.getRole()))
        );
    }
}
