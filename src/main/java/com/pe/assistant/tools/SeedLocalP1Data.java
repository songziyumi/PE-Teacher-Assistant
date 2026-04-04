package com.pe.assistant.tools;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseRequestAudit;
import com.pe.assistant.entity.Grade;
import com.pe.assistant.entity.InternalMessage;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.SchoolClass;
import com.pe.assistant.entity.SelectionEvent;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.StudentAccount;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseRequestAuditRepository;
import com.pe.assistant.repository.GradeRepository;
import com.pe.assistant.repository.InternalMessageRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.SchoolRepository;
import com.pe.assistant.repository.SelectionEventRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import com.pe.assistant.service.StudentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@Profile("seed")
@RequiredArgsConstructor
public class SeedLocalP1Data implements CommandLineRunner {

    private static final String ADMIN_CLASS_TYPE = "\u884c\u653f\u73ed"; // 行政班
    private static final String ELECTIVE_CLASS_TYPE = "\u9009\u4fee\u8bfe"; // 选修课

    private static final List<String> STUDENT_STATUSES = List.of(
            "\u5728\u7c4d", // 在籍
            "\u4f11\u5b66", // 休学
            "\u6bd5\u4e1a", // 毕业
            "\u5728\u5916\u501f\u8bfb", // 在外借读
            "\u501f\u8bfb" // 借读
    );

    private final SchoolRepository schoolRepository;
    private final TeacherRepository teacherRepository;
    private final GradeRepository gradeRepository;
    private final SchoolClassRepository classRepository;
    private final StudentRepository studentRepository;
    private final SelectionEventRepository eventRepository;
    private final CourseRepository courseRepository;
    private final InternalMessageRepository messageRepository;
    private final CourseRequestAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudentAccountService studentAccountService;

    @Value("${app.seed.p1.enabled:true}")
    private boolean seedEnabled;

    @Value("${app.seed.p1.student-count:30}")
    private int studentCount;

    @Value("${app.seed.p1.course-request-count:12}")
    private int courseRequestCount;

    @Value("${app.seed.p1.general-message-count:6}")
    private int generalMessageCount;

    @Value("${app.seed.p1.force-messages:false}")
    private boolean forceMessages;

    @Value("${app.seed.p1.exit:true}")
    private boolean exitAfterSeed;

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            return;
        }

        School school = ensureSchool();
        Teacher teacher = ensureTeacher(school);

        Grade grade1 = ensureGrade(school, "G1");
        Grade grade2 = ensureGrade(school, "G2");

        SchoolClass adminA = ensureClass(school, grade1, "G1-A", ADMIN_CLASS_TYPE, teacher);
        SchoolClass adminB = ensureClass(school, grade1, "G1-B", ADMIN_CLASS_TYPE, teacher);
        SchoolClass electiveBasketball = ensureClass(school, grade1, "Basketball", ELECTIVE_CLASS_TYPE, teacher);
        SchoolClass electiveSoccer = ensureClass(school, grade2, "Soccer", ELECTIVE_CLASS_TYPE, teacher);

        List<Student> seededStudents = ensureStudents(
                school,
                List.of(adminA, adminB),
                List.of(storedElectiveName(electiveBasketball), storedElectiveName(electiveSoccer))
        );

        SelectionEvent event = ensureEvent(school, "P1 Seed Event 2026");
        Course course = ensureCourse(school, event, teacher, "P1 Seed Course - Basketball");

        ensureSeedMessages(teacher, school, course, seededStudents);

        if (exitAfterSeed) {
            System.exit(0);
        }
    }

    private School ensureSchool() {
        return schoolRepository.findByCode("JSQJZX")
                .orElseGet(() -> {
                    School s = new School();
                    s.setName("P1 Seed School");
                    s.setCode("P1SEED");
                    s.setEnabled(true);
                    return schoolRepository.save(s);
                });
    }

    private Teacher ensureTeacher(School school) {
        return teacherRepository.findByUsername("teacher_p1")
                .orElseGet(() -> {
                    Teacher t = new Teacher();
                    t.setUsername("teacher_p1");
                    t.setPassword(passwordEncoder.encode("Teacher@2026"));
                    t.setName("Teacher P1");
                    t.setRole("TEACHER");
                    t.setSchool(school);
                    t.setPhone("13800000001");
                    return teacherRepository.save(t);
                });
    }

    private Grade ensureGrade(School school, String name) {
        return gradeRepository.findByNameAndSchool(name, school)
                .orElseGet(() -> {
                    Grade g = new Grade();
                    g.setName(name);
                    g.setSchool(school);
                    return gradeRepository.save(g);
                });
    }

    private SchoolClass ensureClass(
            School school,
            Grade grade,
            String name,
            String type,
            Teacher teacher
    ) {
        List<SchoolClass> existing = classRepository.findBySchool(school);
        for (SchoolClass c : existing) {
            if (name.equalsIgnoreCase(c.getName())
                    && safeEquals(type, c.getType())
                    && ((c.getGrade() == null && grade == null)
                    || (c.getGrade() != null && grade != null && c.getGrade().getId().equals(grade.getId())))) {
                if (c.getTeacher() == null && teacher != null) {
                    c.setTeacher(teacher);
                    classRepository.save(c);
                }
                return c;
            }
        }

        SchoolClass c = new SchoolClass();
        c.setName(name);
        c.setType(type);
        c.setGrade(grade);
        c.setTeacher(teacher);
        c.setSchool(school);
        return classRepository.save(c);
    }

    private List<Student> ensureStudents(
            School school,
            List<SchoolClass> adminClasses,
            List<String> electiveNames
    ) {
        List<Student> created = new ArrayList<>();
        List<Student> existingSeed = studentRepository.findBySchoolOrderByStudentNo(school).stream()
                .filter(s -> s.getStudentNo() != null && s.getStudentNo().startsWith("P1"))
                .toList();
        existingSeed.forEach(this::ensureStudentAccount);
        if (existingSeed.size() >= studentCount) {
            return existingSeed;
        }

        created.addAll(existingSeed);
        int createdCount = existingSeed.size();
        int index = 1;
        while (createdCount < studentCount) {
            String studentNo = String.format(Locale.ROOT, "P1%04d", index);
            Optional<Student> existing = studentRepository.findByStudentNoAndSchool(studentNo, school);
            if (existing.isPresent()) {
                Student existingStudent = existing.get();
                ensureStudentAccount(existingStudent);
                created.add(existingStudent);
                index++;
                continue;
            }

            Student s = new Student();
            s.setName(String.format(Locale.ROOT, "P1 Student %02d", index));
            s.setGender(index % 2 == 0 ? "\u7537" : "\u5973");
            s.setStudentNo(studentNo);
            s.setSchool(school);
            s.setStudentStatus(STUDENT_STATUSES.get(index % STUDENT_STATUSES.size()));

            SchoolClass targetClass = adminClasses.get(index % adminClasses.size());
            s.setSchoolClass(targetClass);

            if (!electiveNames.isEmpty() && index % 3 == 0) {
                s.setElectiveClass(electiveNames.get(index % electiveNames.size()));
            }

            Student saved = studentRepository.save(s);
            ensureStudentAccount(saved);
            created.add(saved);
            createdCount++;
            index++;
        }
        return created;
    }

    private void ensureStudentAccount(Student student) {
        Optional<StudentAccount> existing = studentAccountService.findByStudent(student);
        if (existing.isPresent()) {
            StudentAccount account = existing.get();
            if (Boolean.FALSE.equals(account.getEnabled())
                    || account.getPasswordHash() == null
                    || account.getPasswordHash().isBlank()) {
                studentAccountService.regenerateAccount(student);
            }
            return;
        }
        studentAccountService.generateAccount(student);
    }

    private SelectionEvent ensureEvent(School school, String name) {
        return eventRepository.findBySchoolOrderByCreatedAtDesc(school).stream()
                .filter(e -> name.equalsIgnoreCase(e.getName()))
                .findFirst()
                .orElseGet(() -> {
                    SelectionEvent event = new SelectionEvent();
                    event.setSchool(school);
                    event.setName(name);
                    event.setStatus("ROUND2");
                    event.setRound1Start(LocalDateTime.now().minusDays(30));
                    event.setRound1End(LocalDateTime.now().minusDays(15));
                    event.setRound2Start(LocalDateTime.now().minusDays(7));
                    event.setRound2End(LocalDateTime.now().plusDays(7));
                    return eventRepository.save(event);
                });
    }

    private Course ensureCourse(School school, SelectionEvent event, Teacher teacher, String name) {
        return courseRepository.findByEventOrderByNameAsc(event).stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Course course = new Course();
                    course.setSchool(school);
                    course.setEvent(event);
                    course.setName(name);
                    course.setDescription("Seed course for P1 batch approval tests.");
                    course.setTeacher(teacher);
                    course.setCapacityMode("GLOBAL");
                    course.setTotalCapacity(200);
                    course.setCurrentCount(0);
                    course.setStatus("ACTIVE");
                    return courseRepository.save(course);
                });
    }

    private void ensureSeedMessages(
            Teacher teacher,
            School school,
            Course course,
            List<Student> students
    ) {
        boolean hasSeedMessages = messageRepository
                .findByRecipientTypeAndRecipientIdOrderBySentAtDesc("TEACHER", teacher.getId())
                .stream()
                .anyMatch(m -> m.getSubject() != null && m.getSubject().startsWith("P1 Seed"));
        if (hasSeedMessages && !forceMessages) {
            return;
        }

        int requestCount = Math.min(courseRequestCount, students.size());
        for (int i = 0; i < requestCount; i++) {
            Student s = students.get(i);
            InternalMessage msg = new InternalMessage();
            msg.setSchool(school);
            msg.setSenderType("STUDENT");
            msg.setSenderId(s.getId());
            msg.setSenderName(s.getName());
            msg.setRecipientType("TEACHER");
            msg.setRecipientId(teacher.getId());
            msg.setRecipientName(teacher.getName());
            msg.setSubject(String.format(Locale.ROOT, "P1 Seed Request %s", s.getStudentNo()));
            msg.setContent(String.format(Locale.ROOT, "Seed course request from %s", s.getName()));
            msg.setType("COURSE_REQUEST");
            msg.setRelatedCourseId(course.getId());
            msg.setRelatedCourseName(course.getName());

            String status = "PENDING";
            if (i % 5 == 3) {
                status = "APPROVED";
            } else if (i % 5 == 4) {
                status = "REJECTED";
            }
            msg.setStatus(status);
            if (!"PENDING".equals(status)) {
                msg.setIsRead(true);
                msg.setHandledById(teacher.getId());
                msg.setHandledByName(teacher.getName());
                msg.setHandledAt(LocalDateTime.now().minusDays(1));
                msg.setHandleRemark("seed");
            }
            msg.setSentAt(LocalDateTime.now().minusDays(2).plusHours(i));
            InternalMessage saved = messageRepository.save(msg);

            if (!"PENDING".equals(status)) {
                CourseRequestAudit audit = new CourseRequestAudit();
                audit.setSchool(school);
                audit.setRequestMessageId(saved.getId());
                audit.setAction("APPROVED".equals(status) ? "APPROVE" : "REJECT");
                audit.setBeforeStatus("PENDING");
                audit.setAfterStatus(status);
                audit.setOperatorTeacherId(teacher.getId());
                audit.setOperatorTeacherName(teacher.getName());
                audit.setSenderId(s.getId());
                audit.setSenderName(s.getName());
                audit.setRelatedCourseId(course.getId());
                audit.setRelatedCourseName(course.getName());
                audit.setRemark("seed");
                audit.setHandledAt(saved.getHandledAt());
                auditRepository.save(audit);
            }
        }

        for (int i = 0; i < generalMessageCount; i++) {
            InternalMessage msg = new InternalMessage();
            msg.setSchool(school);
            msg.setSenderType("SYSTEM");
            msg.setSenderId(0L);
            msg.setSenderName("System");
            msg.setRecipientType("TEACHER");
            msg.setRecipientId(teacher.getId());
            msg.setRecipientName(teacher.getName());
            msg.setSubject(String.format(Locale.ROOT, "P1 Seed Notice %02d", i + 1));
            msg.setContent("This is a seed notice for message filtering tests.");
            msg.setType("GENERAL");
            msg.setIsRead(i % 2 == 0);
            msg.setSentAt(LocalDateTime.now().minusHours(12).plusMinutes(i * 10L));
            messageRepository.save(msg);
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String storedElectiveName(SchoolClass schoolClass) {
        if (schoolClass == null) return null;
        if (schoolClass.getGrade() != null) {
            return schoolClass.getGrade().getName() + "/" + schoolClass.getName();
        }
        return schoolClass.getName();
    }
}
