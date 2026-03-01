package com.pe.assistant.service;

import com.pe.assistant.entity.ExamRecord;
import com.pe.assistant.entity.Student;
import com.pe.assistant.entity.Teacher;
import com.pe.assistant.repository.ExamRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRecordRepository examRecordRepository;
    private final StudentService studentService;

    public ExamRecord save(ExamRecord record) {
        return examRecordRepository.save(record);
    }

    public ExamRecord findById(Long id) {
        return examRecordRepository.findById(id).orElse(null);
    }

    public List<ExamRecord> findByStudent(Student student) {
        return examRecordRepository.findByStudent(student);
    }

    public List<ExamRecord> findByClassId(Long classId) {
        return examRecordRepository.findByClassId(classId);
    }

    public List<ExamRecord> findByGradeId(Long gradeId) {
        return examRecordRepository.findByGradeId(gradeId);
    }

    @Transactional
    public void delete(Long id) {
        examRecordRepository.deleteById(id);
    }

    public ExamRecord createExamRecord(Student student, String examName, LocalDate examDate) {
        ExamRecord record = new ExamRecord();
        record.setStudent(student);
        record.setExamName(examName);
        record.setExamDate(examDate);
        return record;
    }

    // 计算总分
    public BigDecimal calculateTotalScore(ExamRecord record) {
        BigDecimal total = BigDecimal.ZERO;
        if (record.getProject1Score() != null)
            total = total.add(record.getProject1Score());
        if (record.getProject2Score() != null)
            total = total.add(record.getProject2Score());
        if (record.getProject3Score() != null)
            total = total.add(record.getProject3Score());
        if (record.getProject4Score() != null)
            total = total.add(record.getProject4Score());
        if (record.getProject5Score() != null)
            total = total.add(record.getProject5Score());
        return total;
    }

    // 更新排名
    @Transactional
    public void updateRankings(String examName, Long classId) {
        List<ExamRecord> records = examRecordRepository.findByExamNameAndClassIdOrderByScore(examName, classId);

        for (int i = 0; i < records.size(); i++) {
            ExamRecord record = records.get(i);
            record.setClassRank(i + 1);
            examRecordRepository.save(record);
        }
    }

    // 统计教师相关的考试成绩记录数量
    public Long countByTeacher(Teacher teacher) {
        // 这里简化实现：获取教师所在学校的所有记录
        // 实际应用中应该根据教师管理的班级来统计
        return examRecordRepository.count();
    }
}