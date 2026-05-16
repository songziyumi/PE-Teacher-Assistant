package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppStudentCourseDto {
    private Long id;
    private String name;
    private String description;
    private String teacherName;
    private Boolean teacherAssigned;
    private Integer totalCapacity;
    private Integer confirmedCount;
    private Integer remaining;
    private String capacityMode;
    private Boolean confirmed;
    private Integer myPreference;
    private Boolean eligible;
    private String ineligibleMessage;
    private String genderLimit;
    private String genderLimitLabel;
    private Boolean canPrefer;
    private Boolean canSelect;
    private String actionLabel;
    private String actionDisabledReason;
}
