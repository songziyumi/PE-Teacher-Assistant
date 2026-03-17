package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.TeacherOperationLog;
import com.pe.assistant.repository.TeacherOperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TeacherOperationLogService {

    private final TeacherOperationLogRepository repo;

    /**
     * 异步写入操作日志，不阻塞主请求响应。
     *
     * @param teacherId   教师 ID
     * @param teacherName 教师姓名
     * @param school      所属学校
     * @param action      操作类型（见 TeacherOperationLog 注释）
     * @param description 人类可读描述
     * @param targetCount 涉及记录数（可为 null）
     */
    @Async
    public void log(Long teacherId, String teacherName, School school,
                    String action, String description, Integer targetCount) {
        try {
            TeacherOperationLog entry = new TeacherOperationLog();
            entry.setTeacherId(teacherId);
            entry.setTeacherName(teacherName);
            entry.setSchool(school);
            entry.setAction(action);
            entry.setDescription(description);
            entry.setTargetCount(targetCount);
            repo.save(entry);
        } catch (Exception ignored) {
            // 日志写入失败不能影响主业务
        }
    }
}
