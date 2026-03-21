package com.pe.assistant.security;

import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.StudentAccountService;
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
    private final StudentAccountService studentAccountService;
    private final LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (!isInternalStudentPrincipal(username) && loginAttemptService.isBlocked(username)) {
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

        StudentAccount account = studentAccountService.resolvePrincipal(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
        if (account.getPasswordHash() == null || account.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("账号或密码错误");
        }
        if (Boolean.FALSE.equals(account.getEnabled())) {
            throw new LockedException("账号已禁用");
        }
        if (studentAccountService.isLocked(account)) {
            throw new LockedException("账号已锁定，请联系管理员");
        }

        return new org.springframework.security.core.userdetails.User(
                "student-account:" + account.getId(),
                account.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    private boolean isInternalStudentPrincipal(String username) {
        return username != null
                && (username.startsWith("student-account:") || username.startsWith("student:"));
    }
}
