package com.pe.assistant.service;

import com.pe.assistant.entity.Attendance;
import com.pe.assistant.entity.School;
import com.pe.assistant.repository.AttendanceRepository;
import com.pe.assistant.repository.StudentRepository;
import com.pe.assistant.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceRegressionTest {

    @Mock
    private AttendanceRepository attendanceRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private TeacherRepository teacherRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void findByElectiveClassAndDateShouldRestrictToCurrentSchool() {
        School school = new School();
        school.setId(1L);
        LocalDate date = LocalDate.of(2026, 3, 23);
        Attendance record = new Attendance();

        when(attendanceRepository.findBySchoolAndElectiveClassAndDate(school, "高三/飞盘班", date))
                .thenReturn(List.of(record));

        List<Attendance> result = attendanceService.findByElectiveClassAndDate(school, "高三/飞盘班", date);

        assertEquals(1, result.size());
        assertSame(record, result.get(0));
        verify(attendanceRepository, never()).findByElectiveClassAndDate("高三/飞盘班", date);
    }
}
