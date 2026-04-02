package com.pe.assistant.service;

import com.pe.assistant.entity.Course;
import com.pe.assistant.entity.CourseSelection;
import com.pe.assistant.entity.School;
import com.pe.assistant.entity.Student;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.CourseClassCapacityRepository;
import com.pe.assistant.repository.CourseRepository;
import com.pe.assistant.repository.CourseSelectionRepository;
import com.pe.assistant.repository.EventStudentRepository;
import com.pe.assistant.repository.ExamRecordRepository;
import com.pe.assistant.repository.HealthTestRecordRepository;
import com.pe.assistant.repository.PhysicalTestRepository;
import com.pe.assistant.repository.SchoolClassRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TermGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceRegressionTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolClassRepository classRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private TermGradeRepository termGradeRepository;
    @Mock
    private PhysicalTestRepository physicalTestRepository;
    @Mock
    private HealthTestRecordRepository healthTestRecordRepository;
    @Mock
    private ExamRecordRepository examRecordRepository;
    @Mock
    private CourseSelectionRepository courseSelectionRepository;
    @Mock
    private EventStudentRepository eventStudentRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseClassCapacityRepository courseClassCapacityRepository;

    @InjectMocks
    private StudentService studentService;

    @Test
    void updateShouldFailWhenStudentNoDuplicatedInSameSchool() {
        School school = new School();
        school.setId(1L);
        Student current = new Student();
        current.setId(100L);
        current.setSchool(school);
        current.setStudentNo("S-100");

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));
        when(studentRepository.existsByStudentNoAndSchoolAndIdNot("S-200", school, 100L))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "M", "S-200", "IDCARD", null, null, null));

        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenNameBlank() {
        Student current = new Student();
        current.setId(100L);

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "   ", "M", "S-200", "IDCARD", null, null, null));

        assertEquals("学生姓名不能为空", error.getMessage());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenStudentNoBlank() {
        Student current = new Student();
        current.setId(100L);

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "M", "   ", "IDCARD", null, null, null));

        assertEquals("学号不能为空", error.getMessage());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenStudentNoTooLong() {
        Student current = new Student();
        current.setId(100L);

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "M", "S".repeat(51), "IDCARD", null, null, null));

        assertEquals("学号不能超过50个字符", error.getMessage());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateShouldFailWhenStudentNoContainsWhitespace() {
        Student current = new Student();
        current.setId(100L);

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "M", "S 200", "IDCARD", null, null, null));

        assertEquals("学号不能包含空格", error.getMessage());
        verify(studentRepository, never()).save(any());
    }

    @Test
    void updateShouldTranslateDatabaseUniqueConstraintToReadableMessage() {
        School school = new School();
        school.setId(1L);
        Student current = new Student();
        current.setId(100L);
        current.setSchool(school);
        current.setStudentNo("S-100");

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));
        when(studentRepository.existsByStudentNoAndSchoolAndIdNot("S-200", school, 100L))
                .thenReturn(false);
        when(studentRepository.saveAndFlush(any(Student.class)))
                .thenThrow(new DataIntegrityViolationException("uk_students_school_student_no"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "M", "S-200", "IDCARD", null, null, null));

        assertEquals("学号已存在", error.getMessage());
    }

    @Test
    void updateShouldFailWhenExpectedVersionStale() {
        Student current = new Student();
        current.setId(100L);
        current.setVersion(3L);

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> studentService.update(100L, "Name", "M", "S-200", "IDCARD", null, null, null, 2L));

        assertEquals("该学生已被其他设备修改，请刷新后重试", error.getMessage());
        verify(studentRepository, never()).save(any());
    }
    @Test
    void findByElectiveClassForTeacherShouldRestrictToCurrentSchool() {
        School school = new School();
        school.setId(1L);
        Student student = new Student();
        student.setId(10L);
        student.setSchool(school);

        when(studentRepository.findBySchoolAndElectiveClassOrderByStudentNo(school, "高三/飞盘班"))
                .thenReturn(List.of(student));

        List<Student> result = studentService.findByElectiveClassForTeacher(school, "高三/飞盘班");

        assertEquals(1, result.size());
        assertSame(student, result.get(0));
        verify(studentRepository, never()).findByElectiveClassOrderByStudentNo("高三/飞盘班");
    }
    @Test
    void syncElectiveClassesForStudentsShouldBackfillFromLatestConfirmedCourse() {
        Student student = new Student();
        student.setId(1L);
        student.setName("Tom");

        Course oldCourse = new Course();
        oldCourse.setId(10L);
        oldCourse.setName("旧课程");

        Course latestCourse = new Course();
        latestCourse.setId(11L);
        latestCourse.setName("高二/篮球");

        CourseSelection oldConfirmed = new CourseSelection();
        oldConfirmed.setId(101L);
        oldConfirmed.setStudent(student);
        oldConfirmed.setCourse(oldCourse);
        oldConfirmed.setStatus("CONFIRMED");
        oldConfirmed.setConfirmedAt(java.time.LocalDateTime.now().minusDays(2));

        CourseSelection latestConfirmed = new CourseSelection();
        latestConfirmed.setId(102L);
        latestConfirmed.setStudent(student);
        latestConfirmed.setCourse(latestCourse);
        latestConfirmed.setStatus("CONFIRMED");
        latestConfirmed.setConfirmedAt(java.time.LocalDateTime.now().minusHours(1));

        when(courseSelectionRepository.findByStudent(student))
                .thenReturn(List.of(oldConfirmed, latestConfirmed));

        int updated = studentService.syncElectiveClassesForStudents(List.of(student));

        assertEquals(1, updated);
        assertEquals("高二/篮球", student.getElectiveClass());
        verify(studentRepository).save(student);
    }
}
