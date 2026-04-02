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

    @Transient
    public String getAdminDisplayStatusText() {
        LocalDateTime now = LocalDateTime.now();
        return switch (status) {
            case "ROUND1" -> resolveRoundText("第一轮", round1Start, round1End, now);
            case "PROCESSING" -> "结算中";
            case "ROUND2" -> resolveRoundText("第二轮", round2Start, round2End, now);
            case "CLOSED" -> "已关闭";
            default -> "草稿";
        };
    }

    @Transient
    public String getAdminDisplayStatusBadgeClass() {
        LocalDateTime now = LocalDateTime.now();
        return switch (status) {
            case "ROUND1" -> isBeforeStart(round1Start, now) || isAfterEnd(round1End, now)
                    ? "badge-draft"
                    : "badge-round1";
            case "PROCESSING" -> "badge-processing";
            case "ROUND2" -> isBeforeStart(round2Start, now) || isAfterEnd(round2End, now)
                    ? "badge-draft"
                    : "badge-round2";
            case "CLOSED" -> "badge-closed";
            default -> "badge-draft";
        };
    }

    private String resolveRoundText(String roundName, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (isBeforeStart(start, now)) {
            return roundName + "未开始";
        }
        if (isAfterEnd(end, now)) {
            return roundName + "已结束";
        }
        return roundName + "进行中";
    }

    private boolean isBeforeStart(LocalDateTime start, LocalDateTime now) {
        return start != null && now.isBefore(start);
    }

    private boolean isAfterEnd(LocalDateTime end, LocalDateTime now) {
        return end != null && !now.isBefore(end);
    }
}
