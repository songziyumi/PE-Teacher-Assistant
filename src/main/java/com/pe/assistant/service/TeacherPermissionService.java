package com.pe.assistant.service;

import com.pe.assistant.entity.School;
import com.pe.assistant.entity.TeacherPermission;
import com.pe.assistant.repository.TeacherPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TeacherPermissionService {

    private final TeacherPermissionRepository repo;

    /** 获取或初始化学校的权限配置（首次调用自动用默认值建表） */
    @Transactional
    public TeacherPermission getOrCreate(School school) {
        return repo.findBySchool(school).orElseGet(() -> {
            TeacherPermission p = new TeacherPermission();
            p.setSchool(school);
            if (school != null) {
                p.setShowSuspendedOnTeacherPage(!Boolean.FALSE.equals(school.getShowSuspendedOnTeacherPage()));
                p.setShowOutgoingBorrowOnTeacherPage(!Boolean.FALSE.equals(school.getShowOutgoingBorrowOnTeacherPage()));
            }
            return repo.save(p);
        });
    }

    /** 批量更新权限配置（key = 字段名, value = 是否开启） */
    @Transactional
    public TeacherPermission update(School school, Map<String, Boolean> config) {
        TeacherPermission p = getOrCreate(school);
        if (config.containsKey("editStudentName"))      p.setEditStudentName(config.get("editStudentName"));
        if (config.containsKey("editStudentGender"))    p.setEditStudentGender(config.get("editStudentGender"));
        if (config.containsKey("editStudentNo"))        p.setEditStudentNo(config.get("editStudentNo"));
        if (config.containsKey("editStudentStatus"))    p.setEditStudentStatus(config.get("editStudentStatus"));
        if (config.containsKey("editStudentClass"))     p.setEditStudentClass(config.get("editStudentClass"));
        if (config.containsKey("editStudentElectiveClass")) p.setEditStudentElectiveClass(config.get("editStudentElectiveClass"));
        if (config.containsKey("attendanceEdit"))       p.setAttendanceEdit(config.get("attendanceEdit"));
        if (config.containsKey("physicalTestEdit"))     p.setPhysicalTestEdit(config.get("physicalTestEdit"));
        if (config.containsKey("termGradeEdit"))        p.setTermGradeEdit(config.get("termGradeEdit"));
        if (config.containsKey("batchOperation"))       p.setBatchOperation(config.get("batchOperation"));
        if (config.containsKey("showSuspendedOnTeacherPage")) {
            p.setShowSuspendedOnTeacherPage(Boolean.TRUE.equals(config.get("showSuspendedOnTeacherPage")));
        }
        if (config.containsKey("showOutgoingBorrowOnTeacherPage")) {
            p.setShowOutgoingBorrowOnTeacherPage(Boolean.TRUE.equals(config.get("showOutgoingBorrowOnTeacherPage")));
        }
        if (config.containsKey("showLongLeaveOnTeacherPage")) {
            p.setShowLongLeaveOnTeacherPage(Boolean.TRUE.equals(config.get("showLongLeaveOnTeacherPage")));
        }
        return repo.save(p);
    }

    /** Web 端表单保存（checkbox 未勾选时不提交，用 @RequestParam defaultValue="false" 兜底） */
    @Transactional
    public TeacherPermission updateFromForm(School school,
            boolean editStudentName, boolean editStudentGender,
            boolean editStudentNo, boolean editStudentStatus,
            boolean editStudentClass, boolean editStudentElectiveClass,
            boolean attendanceEdit, boolean physicalTestEdit,
            boolean termGradeEdit, boolean batchOperation,
            boolean showSuspendedOnTeacherPage, boolean showOutgoingBorrowOnTeacherPage,
            boolean showLongLeaveOnTeacherPage) {
        TeacherPermission p = getOrCreate(school);
        p.setEditStudentName(editStudentName);
        p.setEditStudentGender(editStudentGender);
        p.setEditStudentNo(editStudentNo);
        p.setEditStudentStatus(editStudentStatus);
        p.setEditStudentClass(editStudentClass);
        p.setEditStudentElectiveClass(editStudentElectiveClass);
        p.setAttendanceEdit(attendanceEdit);
        p.setPhysicalTestEdit(physicalTestEdit);
        p.setTermGradeEdit(termGradeEdit);
        p.setBatchOperation(batchOperation);
        p.setShowSuspendedOnTeacherPage(showSuspendedOnTeacherPage);
        p.setShowOutgoingBorrowOnTeacherPage(showOutgoingBorrowOnTeacherPage);
        p.setShowLongLeaveOnTeacherPage(showLongLeaveOnTeacherPage);
        return repo.save(p);
    }
}
