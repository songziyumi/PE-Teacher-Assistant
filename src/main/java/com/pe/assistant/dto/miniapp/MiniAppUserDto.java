package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppUserDto {
    private Long id;
    private String username;
    private String loginAlias;
    private String name;
    private String role;
    private Long schoolId;
    private String schoolName;
}
