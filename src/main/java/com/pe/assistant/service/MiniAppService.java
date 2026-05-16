package com.pe.assistant.service;

import com.pe.assistant.dto.miniapp.MiniAppEventSummaryDto;
import com.pe.assistant.dto.miniapp.MiniAppMessageItemDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentCourseDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentHomeDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentMessageSummaryDto;
import com.pe.assistant.dto.miniapp.MiniAppStudentMyCourseDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherActivityDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherClassDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherCourseRequestDashboardDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherCourseRequestItemDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherHomeDto;
import com.pe.assistant.dto.miniapp.MiniAppTeacherStudentDto;
import com.pe.assistant.dto.miniapp.MiniAppUserDto;
import com.pe.assistant.dto.PageDto;
import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.TeacherOperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class MiniAppService {

    private final ClassService classService;
    private final CourseService courseService;
    private final CurrentUserService currentUserService;
    private final MessageService messageService;
    private final SelectionEventRepository selectionEventRepository;
    private final SelectionEventService selectionEventService;
    private final StudentService studentService;
    private final StudentAccountService studentAccountService;
    private final TeacherOperationLogRepository teacherOperationLogRepository;

    public MiniAppUserDto toTeacherUser(Teacher teacher) {
        Long schoolId = teacher.getSchool() != null ? teacher.getSchool().getId() : null;
        String schoolName = teacher.getSchool() != null ? teacher.getSchool().getName() : null;
        return MiniAppUserDto.builder()
                .id(teacher.getId())
                .username(teacher.getUsername())
                .name(teacher.getName())
                .role(teacher.getRole())
                .schoolId(schoolId)
                .schoolName(schoolName)
                .build();
    }

    public MiniAppUserDto toStudentUser(StudentAccount account) {
        Student student = account.getStudent();
        Long schoolId = student != null && student.getSchool() != null ? student.getSchool().getId() : null;
        String schoolName = student != null && student.getSchool() != null ? student.getSchool().getName() : null;
        return MiniAppUserDto.builder()
                .id(student != null ? student.getId() : null)
                .username(account.getLoginId())
                .loginAlias(account.getLoginAlias())
                .name(student != null ? student.getName() : null)
                .role("STUDENT")
                .schoolId(schoolId)
                .schoolName(schoolName)
                .build();
    }

    public MiniAppStudentHomeDto buildStudentHome() {
        Student student = currentUserService.getCurrentStudent();
        StudentAccount account = currentUserService.getCurrentStudentAccount();

        SelectionEvent activeEvent = findActiveEvent(student);
        SelectionEvent selectionEvent = activeEvent != null ? activeEvent : findLatestEventWithSelections(student);
        List<CourseSelection> mySelections = selectionEvent != null
                ? courseService.findMySelections(student, selectionEvent)
                : List.of();

        int currentCourseCount = activeEvent != null
                ? courseService.findActiveCoursesForStudent(activeEvent, student).size()
                : 0;

        int confirmedCourseCount = (int) mySelections.stream()
                .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                .count();
        int pendingSelectionCount = (int) mySelections.stream()
                .filter(selection -> "PENDING".equals(selection.getStatus()) || "DRAFT".equals(selection.getStatus()))
                .count();

        return MiniAppStudentHomeDto.builder()
                .user(toStudentUser(account))
                .currentEvent(toEventSummary(activeEvent))
                .currentCourseCount(currentCourseCount)
                .mySelectionCount(mySelections.size())
                .confirmedCourseCount(confirmedCourseCount)
                .pendingSelectionCount(pendingSelectionCount)
                .requestableCourseCount(countRequestableCourses(student))
                .unreadMessageCount(messageService.getUnreadCount("STUDENT", student.getId()))
                .recentMessages(messageService.getStudentInbox(student).stream()
                        .limit(5)
                        .map(this::toMessageItemDto)
                        .toList())
                .mustChangePassword(studentAccountService.requiresPasswordChange(account))
                .build();
    }

    public MiniAppTeacherHomeDto buildTeacherHome() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<com.pe.assistant.entity.SchoolClass> classes = classService.findByTeacher(teacher);

        return MiniAppTeacherHomeDto.builder()
                .user(toTeacherUser(teacher))
                .classCount(classes.size())
                .pendingCourseRequestCount(messageService.countTeacherCourseRequests(teacher, "PENDING"))
                .unreadMessageCount(messageService.getUnreadCount("TEACHER", teacher.getId()))
                .classes(classes.stream()
                        .map(schoolClass -> MiniAppTeacherClassDto.builder()
                                .id(schoolClass.getId())
                                .name(schoolClass.getName())
                                .type(schoolClass.getType())
                                .gradeName(schoolClass.getGrade() != null ? schoolClass.getGrade().getName() : null)
                                .build())
                        .toList())
                .recentMessages(messageService.getTeacherInbox(teacher, "ALL", false).stream()
                        .limit(5)
                        .map(this::toMessageItemDto)
                        .toList())
                .recentActivities(buildTeacherActivities(teacher))
                .build();
    }

    public List<MiniAppStudentCourseDto> listStudentCourses() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findActiveEvent(student);
        if (event == null) {
            return List.of();
        }

        List<CourseSelection> mySelections = courseService.findMySelections(student, event);
        List<Long> confirmedIds = mySelections.stream()
                .filter(selection -> "CONFIRMED".equals(selection.getStatus()))
                .map(selection -> selection.getCourse().getId())
                .toList();

        return courseService.findActiveCoursesForStudent(event, student).stream()
                .map(course -> toStudentCourseDto(course, student, event, mySelections, confirmedIds))
                .toList();
    }

    public List<MiniAppStudentMyCourseDto> listStudentMyCourses() {
        Student student = currentUserService.getCurrentStudent();
        SelectionEvent event = findLatestEventWithSelections(student);
        if (event == null) {
            event = findActiveEvent(student);
        }
        if (event == null) {
            event = findLatestClosedEvent(student);
        }
        if (event == null) {
            return List.of();
        }

        final SelectionEvent resolvedEvent = event;
        return courseService.findMySelections(student, resolvedEvent).stream()
                .sorted(Comparator.comparing(CourseSelection::getSelectedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseSelection::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(selection -> MiniAppStudentMyCourseDto.builder()
                        .id(selection.getId())
                        .courseId(selection.getCourse().getId())
                        .courseName(selection.getCourse().getName())
                        .teacherName(selection.getCourse().getTeacher() != null ? selection.getCourse().getTeacher().getName() : null)
                        .eventName(resolvedEvent.getName())
                        .preference(selection.getPreference())
                        .round(selection.getRound())
                        .roundLabel(toRoundLabel(selection.getRound()))
                        .status(selection.getStatus())
                        .selectedAt(selection.getSelectedAt())
                        .confirmedAt(selection.getConfirmedAt())
                        .canDrop(courseService.canDropSelection(selection))
                        .build())
                .toList();
    }

    public PageDto<MiniAppTeacherStudentDto> listTeacherClassStudents(Long classId, String keyword, String studentStatus,
                                                                       int page, int size) {
        School school = currentUserService.getCurrentSchool();
        SchoolClass schoolClass = classService.findById(classId);
        List<Student> students;
        if (isElectiveType(schoolClass.getType())) {
            String electiveClassName = (schoolClass.getGrade() != null ? schoolClass.getGrade().getName() + "/" : "") + schoolClass.getName();
            students = studentService.findByElectiveClassForTeacher(school, electiveClassName);
        } else {
            students = studentService.findByClassIdForTeacher(school, classId);
        }

        List<MiniAppTeacherStudentDto> filtered = students.stream()
                .filter(student -> containsIgnoreCase(student.getName(), keyword) || containsIgnoreCase(student.getStudentNo(), keyword))
                .filter(student -> isBlank(studentStatus) || normalizeStudentStatus(studentStatus).equals(normalizeStudentStatus(student.getStudentStatus())))
                .sorted(Comparator.comparing(student -> safeString(student.getStudentNo())))
                .map(this::toTeacherStudentDto)
                .toList();

        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int fromIndex = Math.min(safePage * safeSize, filtered.size());
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        PageImpl<MiniAppTeacherStudentDto> paged = new PageImpl<>(
                filtered.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                filtered.size());
        return PageDto.of(paged);
    }

    public MiniAppStudentMessageSummaryDto buildStudentMessageSummary() {
        Student student = currentUserService.getCurrentStudent();
        List<InternalMessage> messages = messageService.getStudentInbox(student);
        long pendingCourseRequestCount = messages.stream()
                .filter(message -> "COURSE_REQUEST".equals(message.getType()))
                .filter(message -> "PENDING".equals(message.getStatus()))
                .count();
        return MiniAppStudentMessageSummaryDto.builder()
                .unreadCount(messageService.getUnreadCount("STUDENT", student.getId()))
                .pendingCourseRequestCount(pendingCourseRequestCount)
                .recentMessages(messages.stream()
                        .limit(5)
                        .map(this::toMessageItemDto)
                        .toList())
                .build();
    }

    public MiniAppTeacherCourseRequestDashboardDto buildTeacherCourseRequestDashboard() {
        Teacher teacher = currentUserService.getCurrentTeacher();
        List<InternalMessage> pendingItems = messageService.getTeacherCourseRequests(teacher, "PENDING");
        return MiniAppTeacherCourseRequestDashboardDto.builder()
                .pendingCount(messageService.countTeacherCourseRequests(teacher, "PENDING"))
                .approvedCount(messageService.countTeacherCourseRequests(teacher, "APPROVED"))
                .rejectedCount(messageService.countTeacherCourseRequests(teacher, "REJECTED"))
                .pendingItems(pendingItems.stream()
                        .limit(10)
                        .map(this::toCourseRequestItemDto)
                        .toList())
                .recentActivities(buildTeacherActivities(teacher))
                .build();
    }

    public MiniAppEventSummaryDto toEventSummary(SelectionEvent event) {
        if (event == null) {
            return null;
        }
        return MiniAppEventSummaryDto.builder()
                .id(event.getId())
                .name(event.getName())
                .status(event.getStatus())
                .round1Start(event.getRound1Start())
                .round1End(event.getRound1End())
                .round2Start(event.getRound2Start())
                .round2End(event.getRound2End())
                .round3Start(event.getRound3Start())
                .round3End(event.getRound3End())
                .inRound1(selectionEventService.isInRound1(event))
                .inRound2(selectionEventService.isInRound2(event))
                .inRound3(selectionEventService.isInRound3(event))
                .build();
    }

    private SelectionEvent findActiveEvent(Student student) {
        if (student.getSchool() == null) {
            return null;
        }
        List<SelectionEvent> events = selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool());
        SelectionEvent activeEvent = events.stream()
                .filter(event -> !"CLOSED".equals(event.getStatus()))
                .findFirst()
                .orElse(null);
        finalizeRound2IfEnded(activeEvent);
        return selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool()).stream()
                .filter(event -> !"CLOSED".equals(event.getStatus()))
                .filter(event -> selectionEventService.canStudentAccessEvent(event, student))
                .findFirst()
                .orElse(null);
    }

    private SelectionEvent findLatestClosedEvent(Student student) {
        if (student.getSchool() == null) {
            return null;
        }
        SelectionEvent activeEvent = selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool()).stream()
                .filter(event -> !"CLOSED".equals(event.getStatus()))
                .findFirst()
                .orElse(null);
        finalizeRound2IfEnded(activeEvent);
        return selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool()).stream()
                .filter(event -> "CLOSED".equals(event.getStatus()))
                .filter(event -> selectionEventService.canStudentAccessEvent(event, student))
                .findFirst()
                .orElse(null);
    }

    private SelectionEvent findLatestEventWithSelections(Student student) {
        if (student.getSchool() == null) {
            return null;
        }
        return selectionEventRepository.findBySchoolOrderByCreatedAtDesc(student.getSchool()).stream()
                .filter(event -> selectionEventService.canStudentAccessEvent(event, student))
                .filter(event -> !courseService.findMySelections(student, event).isEmpty())
                .findFirst()
                .orElse(null);
    }

    private int countRequestableCourses(Student student) {
        SelectionEvent closedEvent = findLatestClosedEvent(student);
        if (closedEvent == null) {
            return 0;
        }

        boolean hasConfirmed = courseService.findMySelections(student, closedEvent).stream()
                .anyMatch(selection -> "CONFIRMED".equals(selection.getStatus()));
        if (hasConfirmed || !isRound3Open(closedEvent)) {
            return 0;
        }

        return (int) courseService.findByEvent(closedEvent).stream()
                .filter(course -> course.getTeacher() != null)
                .count();
    }

    private boolean isRound3Open(SelectionEvent event) {
        if (event == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return event.getRound3Start() != null
                && event.getRound3End() != null
                && !now.isBefore(event.getRound3Start())
                && now.isBefore(event.getRound3End());
    }

    private void finalizeRound2IfEnded(SelectionEvent event) {
        if (event == null || !"ROUND2".equals(event.getStatus()) || event.getRound2End() == null) {
            return;
        }
        if (!LocalDateTime.now().isBefore(event.getRound2End())) {
            try {
                courseService.finalizeEndedRound2Event(event.getId());
            } catch (IllegalStateException ex) {
                log.warn("miniapp round2 finalize skipped, eventId={}, reason={}", event.getId(), ex.getMessage());
            }
        }
    }

    private MiniAppStudentCourseDto toStudentCourseDto(Course course, Student student, SelectionEvent event,
                                                       List<CourseSelection> mySelections, List<Long> confirmedIds) {
        int confirmedCount = courseService.countConfirmedUniqueEnrollments(course);
        boolean eligible = courseService.isStudentEligibleForCourse(student, course);
        int myPreference = mySelections.stream()
                .filter(selection -> selection.getCourse() != null && Objects.equals(selection.getCourse().getId(), course.getId()))
                .filter(selection -> "DRAFT".equals(selection.getStatus())
                        || "PENDING".equals(selection.getStatus())
                        || "CONFIRMED".equals(selection.getStatus()))
                .map(CourseSelection::getPreference)
                .findFirst()
                .orElse(0);
        boolean confirmed = confirmedIds.contains(course.getId());

        boolean inRound1 = selectionEventService.isInRound1(event);
        boolean inRound2 = selectionEventService.isInRound2(event);
        boolean canPrefer = eligible && inRound1 && !confirmed;
        boolean canSelect = eligible && inRound2 && !confirmed && courseService.getRemainingCapacity(course, student) > 0;

        String actionLabel = null;
        String actionDisabledReason = null;
        if (!eligible) {
            actionDisabledReason = courseService.getIneligibleCourseMessage(course);
        } else if (confirmed) {
            actionLabel = "已选中";
        } else if (canSelect) {
            actionLabel = "立即抢课";
        } else if (canPrefer) {
            actionLabel = myPreference > 0 ? "修改志愿" : "填写志愿";
        } else if (inRound2 && courseService.getRemainingCapacity(course, student) <= 0) {
            actionDisabledReason = "当前课程名额已满";
        } else if (!inRound1 && !inRound2) {
            actionDisabledReason = "当前不在可操作时间内";
        }

        return MiniAppStudentCourseDto.builder()
                .id(course.getId())
                .name(course.getName())
                .description(course.getDescription())
                .teacherName(course.getTeacher() != null ? course.getTeacher().getName() : null)
                .teacherAssigned(course.getTeacher() != null)
                .totalCapacity(course.getTotalCapacity())
                .confirmedCount(confirmedCount)
                .remaining(courseService.getRemainingCapacity(course, student))
                .capacityMode(course.getCapacityMode())
                .confirmed(confirmed)
                .myPreference(myPreference)
                .eligible(eligible)
                .ineligibleMessage(eligible ? null : courseService.getIneligibleCourseMessage(course))
                .genderLimit(courseService.normalizeGenderLimit(course.getGenderLimit()))
                .genderLimitLabel(courseService.getGenderLimitLabel(course.getGenderLimit()))
                .canPrefer(canPrefer)
                .canSelect(canSelect)
                .actionLabel(actionLabel)
                .actionDisabledReason(actionDisabledReason)
                .build();
    }

    private MiniAppTeacherStudentDto toTeacherStudentDto(Student student) {
        return MiniAppTeacherStudentDto.builder()
                .id(student.getId())
                .name(student.getName())
                .studentNo(student.getStudentNo())
                .version(student.getVersion() == null ? -1L : student.getVersion())
                .gender(student.getGender())
                .studentStatus(student.getStudentStatus())
                .electiveClass(student.getElectiveClass())
                .adminClassId(student.getSchoolClass() != null ? student.getSchoolClass().getId() : null)
                .adminClassName(student.getSchoolClass() != null ? student.getSchoolClass().getName() : null)
                .gradeName(student.getSchoolClass() != null && student.getSchoolClass().getGrade() != null
                        ? student.getSchoolClass().getGrade().getName()
                        : null)
                .build();
    }

    private String toRoundLabel(int round) {
        return switch (round) {
            case 1 -> "第一轮";
            case 2 -> "第二轮";
            default -> "第" + round + "轮";
        };
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        if (source == null) {
            return false;
        }
        return source.trim().toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private boolean isElectiveType(String type) {
        if (type == null) {
            return false;
        }
        String value = type.trim();
        return "选修课".equals(value) || value.contains("选修");
    }

    private String normalizeStudentStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return switch (status.trim()) {
            case "外出借读" -> "在外借读";
            case "外校借读" -> "借读";
            default -> status.trim();
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private MiniAppMessageItemDto toMessageItemDto(InternalMessage message) {
        return MiniAppMessageItemDto.builder()
                .id(message.getId())
                .subject(message.getSubject())
                .content(message.getContent())
                .type(message.getType())
                .status(message.getStatus())
                .isRead(message.getIsRead())
                .sentAt(message.getSentAt())
                .senderType(message.getSenderType())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .relatedCourseId(message.getRelatedCourseId())
                .relatedCourseName(message.getRelatedCourseName())
                .build();
    }

    private MiniAppTeacherCourseRequestItemDto toCourseRequestItemDto(InternalMessage message) {
        return MiniAppTeacherCourseRequestItemDto.builder()
                .id(message.getId())
                .studentId(message.getSenderId())
                .studentName(message.getSenderName())
                .courseId(message.getRelatedCourseId())
                .courseName(message.getRelatedCourseName())
                .status(message.getStatus())
                .content(message.getContent())
                .handleRemark(message.getHandleRemark())
                .sentAt(message.getSentAt())
                .handledAt(message.getHandledAt())
                .build();
    }

    private List<MiniAppTeacherActivityDto> buildTeacherActivities(Teacher teacher) {
        List<MiniAppTeacherActivityDto> operationActivities = teacherOperationLogRepository
                .findTop100ByTeacherIdOrderByOperatedAtDesc(teacher.getId()).stream()
                .limit(5)
                .map(log -> MiniAppTeacherActivityDto.builder()
                        .action(log.getAction())
                        .title(log.getAction())
                        .description(log.getDescription())
                        .operatedAt(log.getOperatedAt())
                        .build())
                .toList();
        List<MiniAppTeacherActivityDto> auditActivities = messageService.getTeacherCourseRequests(teacher, "ALL").stream()
                .filter(message -> message.getHandledAt() != null)
                .limit(5)
                .map(message -> MiniAppTeacherActivityDto.builder()
                        .action(message.getStatus())
                        .title("COURSE_REQUEST".equals(message.getType()) ? "选课审批" : message.getType())
                        .description((message.getRelatedCourseName() != null ? message.getRelatedCourseName() : "课程")
                                + (message.getSenderName() != null ? " - " + message.getSenderName() : ""))
                        .operatedAt(message.getHandledAt())
                        .build())
                .toList();
        return java.util.stream.Stream.concat(operationActivities.stream(), auditActivities.stream())
                .sorted(Comparator.comparing(MiniAppTeacherActivityDto::getOperatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();
    }
}
