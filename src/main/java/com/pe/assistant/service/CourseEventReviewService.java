package com.pe.assistant.service;

import com.pe.assistant.dto.CourseEventReviewStats;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseOverflowAudit;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.repository.CourseOverflowAuditRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.InternalMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CourseEventReviewService {

    private final CourseSelectionRepository courseSelectionRepository;
    private final InternalMessageRepository internalMessageRepository;
    private final CourseOverflowAuditRepository courseOverflowAuditRepository;

    public CourseEventReviewStats buildReviewStats(SelectionEvent event, List<Course> courses) {
        if (event == null || courses == null || courses.isEmpty()) {
            return new CourseEventReviewStats(0, 0, 0, 0, 0, 0, 0, List.of());
        }

        List<CourseSelection> selections = courseSelectionRepository.findByEvent(event);
        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .toList();

        List<InternalMessage> courseRequests = courseIds.isEmpty()
                ? List.of()
                : internalMessageRepository.findByTypeAndRelatedCourseIdInOrderBySentAtDesc("COURSE_REQUEST", courseIds);
        List<CourseOverflowAudit> overflowAudits = courseOverflowAuditRepository.findByEventIdOrderByCreatedAtDesc(event.getId());

        Map<Long, Integer> round2ConfirmedByCourse = new LinkedHashMap<>();
        Map<Long, Integer> round0ConfirmedByCourse = new LinkedHashMap<>();
        for (CourseSelection selection : selections) {
            if (!"CONFIRMED".equals(selection.getStatus()) || selection.getCourse() == null || selection.getCourse().getId() == null) {
                continue;
            }
            Long courseId = selection.getCourse().getId();
            if (selection.getRound() == 2) {
                round2ConfirmedByCourse.merge(courseId, 1, Integer::sum);
            } else if (selection.getRound() == 0) {
                round0ConfirmedByCourse.merge(courseId, 1, Integer::sum);
            }
        }

        Map<Long, Integer> requestCountByCourse = new LinkedHashMap<>();
        Map<Long, Integer> requestPendingByCourse = new LinkedHashMap<>();
        Map<Long, Integer> requestApprovedByCourse = new LinkedHashMap<>();
        Map<Long, Integer> requestRejectedByCourse = new LinkedHashMap<>();
        for (InternalMessage request : courseRequests) {
            if (request.getRelatedCourseId() == null) {
                continue;
            }
            Long courseId = request.getRelatedCourseId();
            requestCountByCourse.merge(courseId, 1, Integer::sum);
            String status = request.getStatus();
            if ("APPROVED".equals(status)) {
                requestApprovedByCourse.merge(courseId, 1, Integer::sum);
            } else if ("REJECTED".equals(status)) {
                requestRejectedByCourse.merge(courseId, 1, Integer::sum);
            } else {
                requestPendingByCourse.merge(courseId, 1, Integer::sum);
            }
        }

        Map<Long, Integer> overflowByCourse = new LinkedHashMap<>();
        for (CourseOverflowAudit audit : overflowAudits) {
            if (audit.getCourseId() == null) {
                continue;
            }
            overflowByCourse.merge(audit.getCourseId(), 1, Integer::sum);
        }

        List<CourseEventReviewStats.CourseReviewStat> courseStats = new ArrayList<>();
        int round2ConfirmedCount = 0;
        int round3RequestCount = 0;
        int round3PendingCount = 0;
        int round3ApprovedCount = 0;
        int round3RejectedCount = 0;
        int adminInterventionCount = 0;
        int forcedOverflowCount = overflowAudits.size();

        for (Course course : courses) {
            Long courseId = course.getId();
            int round2Confirmed = round2ConfirmedByCourse.getOrDefault(courseId, 0);
            int round3Requests = requestCountByCourse.getOrDefault(courseId, 0);
            int round3Pending = requestPendingByCourse.getOrDefault(courseId, 0);
            int round3Approved = requestApprovedByCourse.getOrDefault(courseId, 0);
            int round3Rejected = requestRejectedByCourse.getOrDefault(courseId, 0);
            int round0Confirmed = round0ConfirmedByCourse.getOrDefault(courseId, 0);
            int forcedOverflow = overflowByCourse.getOrDefault(courseId, 0);
            int adminIntervention = Math.max(0, round0Confirmed - round3Approved);

            round2ConfirmedCount += round2Confirmed;
            round3RequestCount += round3Requests;
            round3PendingCount += round3Pending;
            round3ApprovedCount += round3Approved;
            round3RejectedCount += round3Rejected;
            adminInterventionCount += adminIntervention;

            courseStats.add(new CourseEventReviewStats.CourseReviewStat(
                    courseId,
                    course.getName(),
                    course.getTeacher() != null ? course.getTeacher().getName() : "-",
                    "PER_CLASS".equals(course.getCapacityMode()) ? "按班名额" : "全局名额",
                    round2Confirmed,
                    round3Requests,
                    round3Pending,
                    round3Approved,
                    round3Rejected,
                    adminIntervention,
                    forcedOverflow
            ));
        }

        return new CourseEventReviewStats(
                round2ConfirmedCount,
                round3RequestCount,
                round3PendingCount,
                round3ApprovedCount,
                round3RejectedCount,
                adminInterventionCount,
                forcedOverflowCount,
                courseStats
        );
    }
}
