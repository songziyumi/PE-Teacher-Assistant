# 课程性别限制改造方案

## 目标

为选课课程增加性别限制能力，支持：

- 不限
- 仅男生
- 仅女生

示例：

- 啦啦操：仅女生
- 足球：仅男生

## 设计原则

- 课程级限制，而不是仅前端提示
- 后端强校验，防止绕过页面直接调用接口
- 自动分配必须遵守限制
- 管理员手动加人也必须遵守限制

## 数据层

### 课程实体

文件：

- `src/main/java/com/pe/assistant/entity/Course.java`

新增字段：

- `genderLimit`

建议值：

- `ALL`
- `MALE_ONLY`
- `FEMALE_ONLY`

默认值：

- `ALL`

### 数据库

当前项目启用了 Hibernate `ddl-auto: update`，预计可自动补列。

若需要手动 SQL：

```sql
ALTER TABLE courses
ADD COLUMN gender_limit VARCHAR(20) NOT NULL DEFAULT 'ALL';
```

## 后端改动

### 课程保存

文件：

- `src/main/java/com/pe/assistant/controller/AdminCourseController.java`

改动：

- 新增 `genderLimit` 请求参数
- 新增/编辑课程时保存 `course.genderLimit`
- 空值按 `ALL`

### 统一性别校验

文件：

- `src/main/java/com/pe/assistant/service/CourseService.java`

新增统一方法：

- `normalizeGenderLimit(String value)`
- `isStudentEligibleForCourse(Student student, Course course)`
- `validateStudentEligibleForCourse(Student student, Course course)`

统一异常文案：

- `该课程仅限男生选择`
- `该课程仅限女生选择`

### 必须接入校验的方法

文件：

- `src/main/java/com/pe/assistant/service/CourseService.java`

方法：

- `submitPreference(...)`
- `selectRound2(...)`
- `adminEnroll(...)`
- `autoAssignRound2Fallback(...)`
- `tryAutoAssignStudent(...)`

文件：

- `src/main/java/com/pe/assistant/controller/StudentCourseController.java`

方法：

- `sendCourseRequest(...)`

说明：

- 第一轮志愿提交要校验
- 第二轮抢课要校验
- 第三轮申请要校验
- 管理员手动加人要校验
- 第二轮结束自动分配时，只能分配到性别匹配课程

## 前端改动

### 管理端课程配置页

文件：

- `src/main/resources/templates/admin/course-event-detail.html`

改动：

- 新增课程表单字段：`性别限制`
- 选项：
  - 不限
  - 仅男生
  - 仅女生
- 课程列表中增加“性别限制”展示

### 学生选课页

文件：

- `src/main/resources/templates/student/courses.html`

改动：

- 课程卡片展示限制标签：
  - `仅限男生`
  - `仅限女生`
- 当前学生不符合时：
  - 保留课程展示
  - 禁用对应选课按钮
  - 展示提示文案

建议提示：

- `该课程仅限男生选择`
- `该课程仅限女生选择`

### 可选展示页

建议同步展示课程性别限制，方便教师/管理员识别：

- `src/main/resources/templates/teacher/courses.html`
- `src/main/resources/templates/teacher/course-enrollments.html`
- `src/main/resources/templates/admin/course-enrollments.html`

## 历史数据与编辑冲突

场景：

- 已有已选学生
- 管理员再把课程改成限定性别

第一版策略：

- 如果当前课程已有不符合限制的已选学生，则不允许改成新的限制

建议提示：

- `当前课程已有不符合条件的已选学生，请先处理后再修改性别限制`

## 测试清单

1. 女生看到仅男生课程
- 能看到课程
- 按钮禁用
- 后端接口仍拦截

2. 男生看到仅女生课程
- 同上

3. 第一轮提交志愿接口拦截

4. 第二轮抢课接口拦截

5. 第三轮申请接口拦截

6. 管理员手动加人拦截

7. 第二轮结束自动分配不跨性别分配

8. 不限性别课程原流程不受影响

## 实施顺序

1. 课程实体新增 `genderLimit`
2. 课程保存链路支持 `genderLimit`
3. `CourseService` 增加统一性别校验
4. 第一轮/第二轮/第三轮/管理员加人/自动分配接入校验
5. 管理端课程配置页新增性别限制 UI
6. 学生端课程卡片展示与禁用逻辑
7. 历史数据冲突提示
8. 回归验证
