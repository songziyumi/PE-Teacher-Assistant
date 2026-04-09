package com.pe.assistant.service;

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
import com.pe.assistant.repository.StudentAccountRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TermGradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceEditRegressionTest {

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
    @Mock
    private StudentAccountRepository studentAccountRepository;
    @Mock
    private StudentReferenceCleanupService studentReferenceCleanupService;
    @Mock
    private TeacherPermissionService teacherPermissionService;

    @InjectMocks
    private StudentService studentService;

    @Test
    void updateShouldReturnReadableErrorWhenStudentMissing() {
        when(studentRepository.findById(100L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "男", "S-100", "IDCARD", null, 1L, "在籍"));

        assertEquals("学生不存在", error.getMessage());
        verify(studentRepository, never()).saveAndFlush(any(Student.class));
    }

    @Test
    void updateShouldReturnReadableErrorWhenClassMissing() {
        Student current = new Student();
        current.setId(100L);
        current.setStudentNo("S-100");

        when(studentRepository.findById(100L)).thenReturn(Optional.of(current));
        when(classRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> studentService.update(100L, "Name", "男", "S-100", "IDCARD", null, 999L, "在籍"));

        assertEquals("班级不存在", error.getMessage());
        verify(studentRepository, never()).saveAndFlush(any(Student.class));
    }
}
