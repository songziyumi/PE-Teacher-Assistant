package com.pe.assistant.controller.support;

public final class CourseSelectionPromptHelper {

    private CourseSelectionPromptHelper() {
    }

    public static String normalizeTeacherPrompt(String message) {
        if (message == null || message.isBlank()) {
            return "处理失败，请稍后重试";
        }
        if (message.contains("申请已处理") || message.contains("请求已处理")) {
            return "该申请已被处理，请刷新审批列表后再试";
        }
        if (message.contains("无权处理他人的申请") || message.contains("UNAUTHORIZED_COURSE_REQUEST")) {
            return "该申请不属于您负责的课程，无法处理";
        }
        if (message.contains("该消息不是选课申请")) {
            return "当前记录不是选课申请，无法处理";
        }
        if (message.contains("消息不存在")) {
            return "审批记录不存在或已被删除，请刷新后重试";
        }
        if (message.contains("该学生已有确认的选课记录")) {
            return "该学生已有已确认课程，不能重复通过申请";
        }
        if (message.contains("课程总名额已满")) {
            return "课程名额已满，无法通过该申请";
        }
        if (message.contains("该班级课程名额已满")) {
            return "该班级课程名额已满，无法通过该申请";
        }
        if (message.contains("学生未分配行政班")) {
            return "该学生未分配行政班，无法处理按班名额课程申请";
        }
        if (message.contains("该学生所在班级未配置课程名额")) {
            return "该学生所在班级未配置该课程名额，无法通过申请";
        }
        if (message.contains("课程不存在")) {
            return "课程不存在或已被删除，请刷新后重试";
        }
        return message;
    }

    public static String normalizeAdminPrompt(String message) {
        if (message == null || message.isBlank()) {
            return "操作失败，请稍后重试";
        }
        if (message.contains("强制超编时必须填写原因")) {
            return "勾选强制超编后，必须填写超编原因";
        }
        if (message.contains("该学生已有确认的选课记录")) {
            return "该学生已有已确认课程，不能重复加入";
        }
        if (message.contains("课程总名额已满")) {
            return "课程名额已满；如需超编，请勾选“强制超编”并填写原因";
        }
        if (message.contains("该班级课程名额已满")) {
            return "该班级课程名额已满；如需超编，请勾选“强制超编”并填写原因";
        }
        if (message.contains("学生未分配行政班")) {
            return "该学生未分配行政班，无法加入按班名额课程";
        }
        if (message.contains("该学生所在班级未配置课程名额")) {
            return "该学生所在班级未配置该课程名额";
        }
        if (message.contains("课程不属于该选课活动")) {
            return "课程与当前活动不匹配，请刷新页面后重试";
        }
        if (message.contains("课程不存在")) {
            return "课程不存在或已被删除，请刷新页面后重试";
        }
        return message;
    }

    public static String normalizeStudentPrompt(String message) {
        if (message == null || message.isBlank()) {
            return "提交失败，请稍后重试";
        }
        if (message.contains("已经对该课程发送过申请") || message.contains("已对该课程发送过申请")) {
            return "您已提交过该课程申请，请等待教师处理";
        }
        if (message.contains("暂未指定授课教师")) {
            return "当前课程暂未分配教师，暂时无法提交申请";
        }
        if (message.contains("申请理由不能超过200字")) {
            return "申请理由不能超过 200 字";
        }
        if (message.contains("没有可申请的选课活动")) {
            return "当前暂无可申请的选课活动";
        }
        if (message.contains("第三轮选课申请尚未开始")) {
            return "第三轮选课申请尚未开始";
        }
        if (message.contains("第三轮选课申请已结束")) {
            return "第三轮选课申请已结束";
        }
        if (message.contains("已有确认的选课") || message.contains("已有确认的选课记录")) {
            return "您已有已确认课程，无需重复申请";
        }
        if (message.contains("课程不属于当前活动")) {
            return "该课程不属于当前申请活动，请刷新页面后重试";
        }
        if (message.contains("课程不存在")) {
            return "课程不存在或已被删除，请刷新页面后重试";
        }
        if (message.contains("选课记录不存在")) {
            return "选课记录不存在或已被删除，请刷新页面后重试";
        }
        if (message.contains("无权操作他人选课记录")) {
            return "该选课记录不属于您，无法执行此操作";
        }
        if (message.contains("只能退已确认的课程")) {
            return "仅已确认的课程支持退课";
        }
        if (message.contains("当前仅支持第一轮已确认课程在第二轮期间退课")) {
            return "当前仅支持第一轮已确认课程在第二轮期间退课";
        }
        return message;
    }
}
