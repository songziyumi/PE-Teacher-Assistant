package com.pe.assistant.controller;

import com.pe.assistant.controller.support.CourseSelectionPromptHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseSelectionPromptHelperTest {

    @Test
    void shouldNormalizeTeacherPrompts() {
        assertEquals("该申请已被处理，请刷新审批列表后再试",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("申请已处理，无法重复操作"));
        assertEquals("该申请不属于您负责的课程，无法处理",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("无权处理他人的申请"));
        assertEquals("审批记录不存在或已被删除，请刷新后重试",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("消息不存在"));
    }

    @Test
    void shouldNormalizeAdminPrompts() {
        assertEquals("课程名额已满；如需超编，请勾选“强制超编”并填写原因",
                CourseSelectionPromptHelper.normalizeAdminPrompt("课程总名额已满"));
        assertEquals("该学生未分配行政班，无法加入按班名额课程",
                CourseSelectionPromptHelper.normalizeAdminPrompt("学生未分配行政班"));
        assertEquals("勾选强制超编后，必须填写超编原因",
                CourseSelectionPromptHelper.normalizeAdminPrompt("强制超编时必须填写原因"));
    }
}
