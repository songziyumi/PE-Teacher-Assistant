package com.pe.assistant.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "selection_events")
public class SelectionEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "round1_start")
    private LocalDateTime round1Start;

    @Column(name = "round1_end")
    private LocalDateTime round1End;

    @Column(name = "round2_start")
    private LocalDateTime round2Start;

    @Column(name = "round2_end")
    private LocalDateTime round2End;

    /** DRAFT / ROUND1 / PROCESSING / ROUND2 / CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** 异步抽签进度说明，如 "正在处理：篮球 (2/5)" */
    @Column(name = "lottery_note", length = 200)
    private String lotteryNote;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
