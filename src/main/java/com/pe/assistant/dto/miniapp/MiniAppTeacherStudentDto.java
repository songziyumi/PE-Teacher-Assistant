package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppTeacherStudentDto {
    private Long id;
    private String name;
    private String studentNo;
    private Long version;
    private String gender;
    private String studentStatus;
    private String electiveClass;
    private Long adminClassId;
    private String adminClassName;
    private String gradeName;
}
