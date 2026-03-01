package com.pe.assistant.service;

import com.pe.assistant.entity.HealthTestRecord;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.HealthTestRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthTestService {

    private final HealthTestRecordRepository healthTestRecordRepository;
    private final StudentService studentService;

    public HealthTestRecord save(HealthTestRecord record) {
        return healthTestRecordRepository.save(record);
    }

    public HealthTestRecord findById(Long id) {
        return healthTestRecordRepository.findById(id).orElse(null);
    }

    public List<HealthTestRecord> findByStudent(Student student) {
        return healthTestRecordRepository.findByStudent(student);
    }

    public List<HealthTestRecord> findByClassId(Long classId) {
        return healthTestRecordRepository.findByClassId(classId);
    }

    public List<HealthTestRecord> findByGradeId(Long gradeId) {
        return healthTestRecordRepository.findByGradeId(gradeId);
    }

    @Transactional
    public void delete(Long id) {
        healthTestRecordRepository.deleteById(id);
    }

    public HealthTestRecord createHealthTestRecord(Student student, LocalDate testDate) {
        HealthTestRecord record = new HealthTestRecord();
        record.setStudent(student);
        record.setTestDate(testDate);
        return record;
    }

    // 计算BMI
    public BigDecimal calculateBMI(BigDecimal height, BigDecimal weight) {
        if (height == null || weight == null || height.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal heightInMeter = height.divide(BigDecimal.valueOf(100.0), 2, RoundingMode.HALF_UP);
        BigDecimal heightSquared = heightInMeter.multiply(heightInMeter);
        return weight.divide(heightSquared, 2, RoundingMode.HALF_UP);
    }

    // 根据教育部标准计算等级
    public String calculateGradeLevel(HealthTestRecord record) {
        // TODO: 实现教育部标准的等级计算逻辑
        // 这里需要根据性别、年级、各项成绩计算总分和等级
        return "待计算";
    }

    // 统计教师相关的体质测试记录数量
    public Long countByTeacher(Teacher teacher) {
        // 这里简化实现：获取教师所在学校的所有记录
        // 实际应用中应该根据教师管理的班级来统计
        return healthTestRecordRepository.count();
    }
}