package com.pe.assistant.service;

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

import java.util.Optional;

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
}
