package com.pe.assistant.controller;

import com.pe.assistant.controller.support.CourseSelectionPromptHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseSelectionPromptHelperTest {

    @Test
    void shouldNormalizeTeacherPrompts() {
        assertEquals("\u8be5\u7533\u8bf7\u5df2\u88ab\u5904\u7406\uff0c\u8bf7\u5237\u65b0\u5ba1\u6279\u5217\u8868\u540e\u518d\u8bd5",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("\u7533\u8bf7\u5df2\u5904\u7406\uff0c\u65e0\u6cd5\u91cd\u590d\u64cd\u4f5c"));
        assertEquals("\u8be5\u7533\u8bf7\u4e0d\u5c5e\u4e8e\u60a8\u8d1f\u8d23\u7684\u8bfe\u7a0b\uff0c\u65e0\u6cd5\u5904\u7406",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("\u65e0\u6743\u5904\u7406\u4ed6\u4eba\u7684\u7533\u8bf7"));
        assertEquals("\u5ba1\u6279\u8bb0\u5f55\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u5220\u9664\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5",
                CourseSelectionPromptHelper.normalizeTeacherPrompt("\u6d88\u606f\u4e0d\u5b58\u5728"));
    }

    @Test
    void shouldNormalizeAdminPrompts() {
        assertEquals("\u8bfe\u7a0b\u540d\u989d\u5df2\u6ee1\uff1b\u5982\u9700\u8d85\u7f16\uff0c\u8bf7\u52fe\u9009\u201c\u5f3a\u5236\u8d85\u7f16\u201d\u5e76\u586b\u5199\u539f\u56e0",
                CourseSelectionPromptHelper.normalizeAdminPrompt("\u8bfe\u7a0b\u603b\u540d\u989d\u5df2\u6ee1"));
        assertEquals("\u8be5\u5b66\u751f\u672a\u5206\u914d\u884c\u653f\u73ed\uff0c\u65e0\u6cd5\u52a0\u5165\u6309\u73ed\u540d\u989d\u8bfe\u7a0b",
                CourseSelectionPromptHelper.normalizeAdminPrompt("\u5b66\u751f\u672a\u5206\u914d\u884c\u653f\u73ed"));
        assertEquals("\u52fe\u9009\u5f3a\u5236\u8d85\u7f16\u540e\uff0c\u5fc5\u987b\u586b\u5199\u8d85\u7f16\u539f\u56e0",
                CourseSelectionPromptHelper.normalizeAdminPrompt("\u5f3a\u5236\u8d85\u7f16\u65f6\u5fc5\u987b\u586b\u5199\u539f\u56e0"));
    }

    @Test
    void shouldNormalizeStudentPrompts() {
        assertEquals("\u60a8\u5df2\u63d0\u4ea4\u8fc7\u8be5\u8bfe\u7a0b\u7533\u8bf7\uff0c\u8bf7\u7b49\u5f85\u6559\u5e08\u5904\u7406",
                CourseSelectionPromptHelper.normalizeStudentPrompt("\u60a8\u5df2\u7ecf\u5bf9\u8be5\u8bfe\u7a0b\u53d1\u9001\u8fc7\u7533\u8bf7\uff0c\u8bf7\u7b49\u5f85\u6559\u5e08\u5904\u7406"));
        assertEquals("\u5f53\u524d\u8bfe\u7a0b\u6682\u672a\u5206\u914d\u6559\u5e08\uff0c\u6682\u65f6\u65e0\u6cd5\u63d0\u4ea4\u7533\u8bf7",
                CourseSelectionPromptHelper.normalizeStudentPrompt("\u8be5\u8bfe\u7a0b\u6682\u672a\u6307\u5b9a\u6388\u8bfe\u6559\u5e08\uff0c\u65e0\u6cd5\u53d1\u9001\u7533\u8bf7"));
        assertEquals("\u7533\u8bf7\u7406\u7531\u4e0d\u80fd\u8d85\u8fc7 200 \u5b57",
                CourseSelectionPromptHelper.normalizeStudentPrompt("\u7533\u8bf7\u7406\u7531\u4e0d\u80fd\u8d85\u8fc7200\u5b57"));
        assertEquals("\u4ec5\u5df2\u786e\u8ba4\u7684\u8bfe\u7a0b\u652f\u6301\u9000\u8bfe",
                CourseSelectionPromptHelper.normalizeStudentPrompt("\u53ea\u80fd\u9000\u5df2\u786e\u8ba4\u7684\u8bfe\u7a0b"));
        assertEquals("\u5f53\u524d\u4ec5\u652f\u6301\u7b2c\u4e00\u8f6e\u5df2\u786e\u8ba4\u8bfe\u7a0b\u5728\u7b2c\u4e8c\u8f6e\u671f\u95f4\u9000\u8bfe",
                CourseSelectionPromptHelper.normalizeStudentPrompt("\u5f53\u524d\u4ec5\u652f\u6301\u7b2c\u4e00\u8f6e\u5df2\u786e\u8ba4\u8bfe\u7a0b\u5728\u7b2c\u4e8c\u8f6e\u671f\u95f4\u9000\u8bfe"));
    }
}
