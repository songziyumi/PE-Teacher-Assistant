package com.pe.assistant.dto;

import java.util.List;

public class CourseEventReviewStats {

    private final int round2ConfirmedCount;
    private final int round3RequestCount;
    private final int round3PendingCount;
    private final int round3ApprovedCount;
    private final int round3RejectedCount;
    private final int adminInterventionCount;
    private final int forcedOverflowCount;
    private final List<CourseReviewStat> courseStats;

    public CourseEventReviewStats(int round2ConfirmedCount,
                                  int round3RequestCount,
                                  int round3PendingCount,
                                  int round3ApprovedCount,
                                  int round3RejectedCount,
                                  int adminInterventionCount,
                                  int forcedOverflowCount,
                                  List<CourseReviewStat> courseStats) {
        this.round2ConfirmedCount = round2ConfirmedCount;
        this.round3RequestCount = round3RequestCount;
        this.round3PendingCount = round3PendingCount;
        this.round3ApprovedCount = round3ApprovedCount;
        this.round3RejectedCount = round3RejectedCount;
        this.adminInterventionCount = adminInterventionCount;
        this.forcedOverflowCount = forcedOverflowCount;
        this.courseStats = courseStats;
    }

    public int getRound2ConfirmedCount() {
        return round2ConfirmedCount;
    }

    public int getRound3RequestCount() {
        return round3RequestCount;
    }

    public int getRound3PendingCount() {
        return round3PendingCount;
    }

    public int getRound3ApprovedCount() {
        return round3ApprovedCount;
    }

    public int getRound3RejectedCount() {
        return round3RejectedCount;
    }

    public int getAdminInterventionCount() {
        return adminInterventionCount;
    }

    public int getForcedOverflowCount() {
        return forcedOverflowCount;
    }

    public List<CourseReviewStat> getCourseStats() {
        return courseStats;
    }

    public static class CourseReviewStat {
        private final Long courseId;
        private final String courseName;
        private final String teacherName;
        private final String capacityMode;
        private final int round2ConfirmedCount;
        private final int round3RequestCount;
        private final int round3PendingCount;
        private final int round3ApprovedCount;
        private final int round3RejectedCount;
        private final int adminInterventionCount;
        private final int forcedOverflowCount;

        public CourseReviewStat(Long courseId,
                                String courseName,
                                String teacherName,
                                String capacityMode,
                                int round2ConfirmedCount,
                                int round3RequestCount,
                                int round3PendingCount,
                                int round3ApprovedCount,
                                int round3RejectedCount,
                                int adminInterventionCount,
                                int forcedOverflowCount) {
            this.courseId = courseId;
            this.courseName = courseName;
            this.teacherName = teacherName;
            this.capacityMode = capacityMode;
            this.round2ConfirmedCount = round2ConfirmedCount;
            this.round3RequestCount = round3RequestCount;
            this.round3PendingCount = round3PendingCount;
            this.round3ApprovedCount = round3ApprovedCount;
            this.round3RejectedCount = round3RejectedCount;
            this.adminInterventionCount = adminInterventionCount;
            this.forcedOverflowCount = forcedOverflowCount;
        }

        public Long getCourseId() {
            return courseId;
        }

        public String getCourseName() {
            return courseName;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public String getCapacityMode() {
            return capacityMode;
        }

        public int getRound2ConfirmedCount() {
            return round2ConfirmedCount;
        }

        public int getRound3RequestCount() {
            return round3RequestCount;
        }

        public int getRound3PendingCount() {
            return round3PendingCount;
        }

        public int getRound3ApprovedCount() {
            return round3ApprovedCount;
        }

        public int getRound3RejectedCount() {
            return round3RejectedCount;
        }

        public int getAdminInterventionCount() {
            return adminInterventionCount;
        }

        public int getForcedOverflowCount() {
            return forcedOverflowCount;
        }
    }
}
