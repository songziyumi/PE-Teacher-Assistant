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

    @Test
    void shouldNormalizeStudentPrompts() {
        assertEquals("您已提交过该课程申请，请等待教师处理",
                CourseSelectionPromptHelper.normalizeStudentPrompt("您已经对该课程发送过申请，请等待教师处理"));
        assertEquals("当前课程暂未分配教师，暂时无法提交申请",
                CourseSelectionPromptHelper.normalizeStudentPrompt("该课程暂未指定授课教师，无法发送申请"));
        assertEquals("申请理由不能超过 200 字",
                CourseSelectionPromptHelper.normalizeStudentPrompt("申请理由不能超过200字"));
        assertEquals("仅已确认的课程支持退课",
                CourseSelectionPromptHelper.normalizeStudentPrompt("只能退已确认的课程"));
        assertEquals("当前仅支持第一轮已确认课程在第二轮期间退课",
                CourseSelectionPromptHelper.normalizeStudentPrompt("当前仅支持第一轮已确认课程在第二轮期间退课"));
    }
}
