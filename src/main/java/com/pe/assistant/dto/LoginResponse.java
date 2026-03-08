package com.pe.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String username;
    private String name;
    private String role;
    private Long schoolId;
    private String schoolName;
    private Boolean mustChangePassword;
}
