package com.pe.assistant.config;

import com.pe.assistant.security.JwtAuthFilter;
import com.pe.assistant.security.JwtUtil;
import com.pe.assistant.security.LoginAttemptService;
import com.pe.assistant.security.UserDetailsServiceImpl;
import com.pe.assistant.service.StudentAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final com.pe.assistant.security.UserDetailsServiceImpl userDetailsService;
    private final com.pe.assistant.security.LoginAttemptService loginAttemptService;
    private final com.pe.assistant.security.JwtUtil jwtUtil;
    private final com.pe.assistant.service.StudentAccountService studentAccountService;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .requestMatchers("/api/teacher/**").hasAnyRole("TEACHER", "ADMIN", "ORG_ADMIN")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "ORG_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new com.pe.assistant.security.JwtAuthFilter(jwtUtil, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setContentType("application/json;charset=UTF-8");
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.getWriter().write("{\"code\":401,\"message\":\"未授权，请先登录\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setContentType("application/json;charset=UTF-8");
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.getWriter().write("{\"code\":403,\"message\":\"权限不足\"}");
                        }));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/icons/**", "/manifest.json", "/sw.js",
                                "/offline.html", "/uploads/**").permitAll()
                        .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "ORG_ADMIN")
                        .requestMatchers("/student/**").hasRole("STUDENT")
                        .requestMatchers("/teacher/profile/**").hasAnyRole("TEACHER", "ADMIN", "ORG_ADMIN")
                        .requestMatchers("/teacher/messages/**").hasAnyRole("TEACHER", "ADMIN", "ORG_ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureHandler(authenticationFailureHandler())
                        .successHandler(authenticationSuccessHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .sessionManagement(session -> session
                        .sessionFixation().migrateSession()
                        .maximumSessions(1))
                .userDetailsService(userDetailsService);
        return http.build();
    }

    private AuthenticationFailureHandler authenticationFailureHandler() {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) -> {
            String username = request.getParameter("username");
            if (username != null) {
                username = username.trim();
                if (!(exception instanceof LockedException || exception.getCause() instanceof LockedException)) {
                    loginAttemptService.loginFailed(username);
                }
                if (loginAttemptService.isBlocked(username)) {
                    request.getSession().setAttribute("LOCKED", true);
                    response.sendRedirect("/login");
                    return;
                }
            }
            request.getSession().setAttribute("LOGIN_ERROR", true);
            response.sendRedirect("/login");
        };
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            String loginInput = request.getParameter("username");
            if (loginInput != null && !loginInput.isBlank()) {
                loginAttemptService.loginSucceeded(loginInput.trim());
            }

            boolean isSuperAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
            boolean isStudent = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
            boolean isOrgAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ORG_ADMIN"));
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isSuperAdmin) {
                response.sendRedirect("/super-admin/schools");
                return;
            }
            if (isStudent) {
                boolean forcePasswordChange = studentAccountService.resolvePrincipal(authentication.getName())
                        .map(studentAccountService::requiresPasswordChange)
                        .orElse(false);
                response.sendRedirect(forcePasswordChange ? "/student/password?force=true" : "/student/courses");
                return;
            }
            if (isOrgAdmin) {
                response.sendRedirect("/admin/competitions");
                return;
            }
            if (isAdmin) {
                response.sendRedirect("/admin");
                return;
            }
            response.sendRedirect("/teacher/profile");
        };
    }
}
