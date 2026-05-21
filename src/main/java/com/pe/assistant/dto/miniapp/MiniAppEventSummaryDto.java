package com.pe.assistant.dto.miniapp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppEventSummaryDto {
    private Long id;
    private String name;
    private String status;
    private LocalDateTime round1Start;
    private LocalDateTime round1End;
    private LocalDateTime round2Start;
    private LocalDateTime round2End;
    private LocalDateTime round3Start;
    private LocalDateTime round3End;
    private Boolean inRound1;
    private Boolean inRound2;
    private Boolean inRound3;
}
