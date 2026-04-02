# 学生抢课模块完整方案 v2

## 需求概览

### 角色
| 角色 | 操作 |
|------|------|
| 管理员 | 创建选课活动、设置课程、配置名额模式、手动触发抽签、调整选课结果 |
| 学生 | 第一轮填报志愿（2门）、第二轮先到先得抢课、第三轮向教师申请（活动关闭后）、退课 |
| 教师 | 查看所授课程的报名名单、处理第三轮申请（同意/拒绝）、维护个人主页 |

---

## 选课流程

### 第一轮（志愿填报 + 异步抽签）
- 时间窗口：`round1Start` ～ `round1End`
- 每个学生最多提交 **2 个志愿**（1志愿 + 2志愿，有优先级顺序）
- 允许课程超额（不做实时拦截）
- **第一轮截止后**，管理员手动触发"抽签结算"：
  1. **按志愿优先级分两阶段异步结算**，管理员页面通过 JS 轮询 `/admin/courses/events/{id}/lottery-status` 查看进度
  2. **阶段一：统一结算全部第一志愿**
     - 查询当前活动中所有第一轮、第一志愿、已确认提交的记录
     - 按课程分组；若为 `PER_CLASS` 模式则按班级子名额分别结算
     - 报名人数 ≤ 可用名额 → 全部 `CONFIRMED`
     - 报名人数 > 可用名额 → 随机抽取至名额上限，其余标记为 `LOTTERY_FAIL`
  3. **阶段二：统一结算全部第二志愿**
     - 仅处理第一志愿未录取的学生
     - 仅使用阶段一结算后的剩余名额
     - 若为 `PER_CLASS` 模式，仍按班级剩余名额分别结算
     - 因第一志愿已录取而不再参与第二志愿的记录标记为 `CANCELLED`
  4. 第一轮全部结算完成后，活动自动推进到第二轮（`ROUND2`）

### 两轮之间
- 第一轮已确认的学生可以主动退课，不区分第一志愿或第二志愿
- 退课后，原录取结果立即失效，空出名额加入第二轮池，该学生同时进入第二轮
- 退课不会触发第一轮重算，也不会回到第一轮重新分配

### 第二轮（先到先得）
- 时间窗口：`round2Start` ～ `round2End`
- 仅面向第一轮未确认的学生（含主动退课学生）
- 悲观锁（`PESSIMISTIC_WRITE`）保证并发安全，不超卖
- 第二轮结束后管理员关闭活动（status → `CLOSED`）

### 第三轮（活动关闭后，站内邮件申请）
- 活动关闭（`CLOSED`）+ 学生无确认选课 → 选课页显示第三轮申请入口
- 学生向课程教师发送站内邮件申请，填写申请理由
- 教师在消息收件箱查看申请，点击"同意"调用 `adminEnroll` 将学生加入课程，点击"拒绝"则通知学生
- 同意/拒绝后自动向学生发送站内通知消息

---

## 数据层设计

### 新实体

#### 1. `SelectionEvent`（选课活动）
```
id
school（School）
name（活动名称，如"2026年春季选课"）
round1Start / round1End（LocalDateTime）
round2Start / round2End（LocalDateTime）
status（DRAFT / ROUND1 / PROCESSING / ROUND2 / CLOSED）
lotteryNote（String，抽签进度说明，如"第一志愿结算中：篮球"）
createdAt
```

#### 2. `Course`（课程）
```
id
event（SelectionEvent）
school（School）
name
description
teacher（Teacher，可空）
capacityMode（String: GLOBAL / PER_CLASS）
totalCapacity（int，GLOBAL模式下的总名额）
currentCount（int，冗余计数）
status（DRAFT / ACTIVE / CLOSED）
```

#### 3. `CourseClassCapacity`（按班名额，仅 PER_CLASS 模式使用）
```
id
course（Course）
schoolClass（SchoolClass）
maxCapacity（int）
currentCount（int）
唯一约束：(course_id, school_class_id)
```

#### 4. `EventStudent`（活动参与学生名单，可选）
```
id
event（SelectionEvent）
student（Student）
唯一约束：(event_id, student_id)
说明：若未设置名单，则全校学生均可参与
```

#### 5. `CourseSelection`（选课志愿记录）
```
id
event（SelectionEvent）
course（Course）
student（Student）
preference（int：1=1志愿，2=2志愿，0=第二轮/手动加入）
round（int：1=第一轮，2=第二轮，0=手动）
status（PENDING / CONFIRMED / LOTTERY_FAIL / CANCELLED）
selectedAt / confirmedAt（LocalDateTime）
唯一约束：(event_id, student_id, preference)
```

#### 6. `InternalMessage`（站内消息）
```
id
school（School）
senderType（TEACHER / STUDENT / SYSTEM）
senderId / senderName
recipientType（TEACHER / STUDENT）
recipientId / recipientName
subject（String）
content（TEXT）
type（GENERAL / COURSE_REQUEST）
isRead（Boolean，默认 false）
relatedCourseId / relatedCourseName（第三轮申请关联课程）
status（null=普通消息；PENDING/APPROVED/REJECTED=课程申请）
sentAt（LocalDateTime）
```

#### 7. 修改 `entity/Teacher.java`
新增个人主页字段：
- `gender`（String）
- `birthDate`（LocalDate，前端计算年龄展示）
- `specialty`（String，专业特长）
- `email`（String）
- `photoUrl`（String，本地路径 `/uploads/teachers/{id}.jpg`）
- `bio`（TEXT，个人简介）

#### 8. 修改 `entity/Student.java`
- `password`（String，BCrypt）
- `enabled`（Boolean，默认 true）
- `studentNo` 作为登录用户名，初始密码 = studentNo

---

## 名额模式详解

### GLOBAL 模式（全局名额）
- `course.totalCapacity` = 总名额，`CourseClassCapacity` 表不使用
- 第一轮阶段一：使用课程总名额 `totalCapacity` 结算第一志愿
- 第一轮阶段二：使用 `totalCapacity - 第一志愿已录取数` 结算第二志愿
- 第二轮：`totalCapacity - currentCount` = 剩余名额，先到先得

### PER_CLASS 模式（按班名额）
- `course.totalCapacity` = `SUM(各班 maxCapacity)`（冗余）
- 第一轮阶段一：按班级子名额独立结算第一志愿
- 第一轮阶段二：继续按班级剩余名额独立结算第二志愿
- 第二轮：学生只能抢**自己班级**对应的剩余名额

---

## 认证层

### 修改 `UserDetailsServiceImpl`
- `loadUserByUsername`：先查 TeacherRepository（按 username），再查 StudentRepository（按 studentNo）
- 学生 authority = `ROLE_STUDENT`

### 修改 `SecurityConfig`
- Web FilterChain：
  - `/student/**` → `hasRole("STUDENT")`
  - `/teacher/profile/**`, `/teacher/messages/**` → `hasAnyRole("TEACHER","ADMIN")`
  - `/uploads/**` → `permitAll()`（静态资源）
  - 成功处理器：STUDENT 跳转 `/student/courses`
- API FilterChain：
  - `/api/student/**` → `hasRole("STUDENT")`

---

## 业务层

### `LotteryService`（独立 Bean，`@Async`）
```
runLottery(eventId):
  加载当前活动全部第一轮志愿记录
  阶段一：按课程 / 班级结算全部第一志愿
    命中 → CONFIRMED
    未命中 → LOTTERY_FAIL
  记录第一志愿已录取学生集合与各维度剩余名额
  阶段二：按课程 / 班级结算全部第二志愿
    候选集仅保留第一志愿未录取学生
    命中 → CONFIRMED
    未命中 → LOTTERY_FAIL
    因第一志愿已录取被排除的第二志愿 → CANCELLED
  event.status = ROUND2
```
*独立 Bean 是为避免 Spring @Async 代理自调用失效*

### `SelectionEventService`
- `processRound1(eventId)` — 设 status=PROCESSING + 调用 `lotteryService.runLottery(eventId)`（异步立返）
- `getLotteryProgress(eventId)` — 返回 `{status, note}` 供前端轮询

### `CourseService`
- `submitPreference(student, eventId, courseId, preference)` — 第一轮提交志愿（幂等）
- `selectRound2(student, eventId, courseId)` — 第二轮抢课（悲观锁）
- `dropCourse(student, selectionId)` — 退课，归还名额
- `adminEnroll(courseId, studentId, eventId)` — 手动/第三轮同意时加入

### `MessageService`
- `sendMessage(...)` — 发普通站内消息
- `sendCourseRequest(student, course, content)` — 第三轮申请（防重复）
- `approveRequest(messageId, teacher)` — 同意 → 调 `adminEnroll` + 通知学生
- `rejectRequest(messageId, teacher)` — 拒绝 + 通知学生
- `getTeacherInbox(teacher)` / `getStudentInbox(student)` — 收件箱
- `getUnreadCount(type, id)` — 未读数（导航栏角标）

---

## 控制器

| 控制器 | 路由 | 功能 |
|--------|------|------|
| `AdminCourseController` | `/admin/courses/**` | 活动/课程 CRUD、触发抽签、轮询接口、查看名单、手动调整 |
| `StudentCourseController` | `/student/**` | 浏览课程、提交志愿、抢课、第三轮申请、退课、我的选课 |
| `TeacherCourseController` | `/teacher/courses/**` | 查看报名名单（只读） |
| `TeacherProfileController` | `/teacher/profile`, `/student/teachers/{id}` | 教师主页编辑、学生查看 |
| `MessageController` | `/teacher/messages/**`, `/student/messages/**` | 收件箱、审批、发消息 |

---

## 页面模板

| 模板 | 说明 |
|------|------|
| `admin/selection-events.html` | 活动列表 + PROCESSING 时进度轮询 |
| `admin/course-event-detail.html` | 课程管理标签 + 参与学生标签（年级/班级筛选联动） |
| `admin/course-enrollments.html` | 报名名单 + 手动调整 |
| `student/courses.html` | 选课页（第一轮志愿按钮 / 第二轮抢课按钮 / 第三轮申请按钮 + 教师主页链接 + 消息角标） |
| `student/my-courses.html` | 我的选课状态 |
| `student/teacher-profile.html` | 只读查看教师主页 |
| `student/messages.html` | 收件箱 + 发消息表单 |
| `teacher/courses.html` | 教师查看名单 |
| `teacher/course-enrollments.html` | 教师查看课程报名详情 |
| `teacher/profile.html` | 教师编辑个人主页（含照片上传） |
| `teacher/messages.html` | 收件箱 + 第三轮申请审批（同意/拒绝） |

---

## REST API（Flutter 移动端）

### 修改 `AuthApiController`
- `/api/auth/login` 支持学生登录（role=STUDENT）

### 新增 `StudentApiController`（`/api/student/**`）
```
GET    /api/student/events/current        当前活动信息
GET    /api/student/courses               可选课程列表（含剩余名额、已选状态）
POST   /api/student/courses/{id}/prefer   第一轮提交志愿（preference=1或2）
POST   /api/student/courses/{id}/select   第二轮抢课
DELETE /api/student/selections/{id}       退课
GET    /api/student/my-selections         我的选课
```

---

## 关键文件清单

| 操作 | 文件 |
|------|------|
| 修改 | `entity/Teacher.java`（+个人主页字段） |
| 修改 | `entity/SelectionEvent.java`（+lotteryNote） |
| 修改 | `entity/Student.java`（+password, enabled） |
| 新增 | `entity/InternalMessage.java` |
| 新增 | `repository/InternalMessageRepository.java` |
| 新增 | `service/LotteryService.java`（@Async） |
| 修改 | `service/SelectionEventService.java`（异步调用） |
| 新增 | `service/MessageService.java` |
| 新增 | `service/CourseService.java` |
| 修改 | `controller/AdminCourseController.java`（+轮询接口） |
| 修改 | `controller/StudentCourseController.java`（+第三轮） |
| 新增 | `controller/TeacherProfileController.java` |
| 新增 | `controller/MessageController.java` |
| 修改 | `controller/api/AuthApiController.java` |
| 新增 | `controller/api/StudentApiController.java` |
| 修改 | `config/SecurityConfig.java` |
| 修改 | `PeTeacherAssistantApplication.java`（+@EnableAsync） |
| 修改 | `src/main/resources/application.yml`（+upload-dir） |
| 修改 | `templates/admin/selection-events.html`（+进度轮询） |
| 修改 | `templates/admin/course-event-detail.html`（+筛选联动） |
| 修改 | `templates/student/courses.html`（+教师链接+第三轮+角标） |
| 新增 | `templates/teacher/profile.html` |
| 新增 | `templates/student/teacher-profile.html` |
| 新增 | `templates/teacher/messages.html` |
| 新增 | `templates/student/messages.html` |

---

## 关键约束与风险点

- **并发安全**：第二轮抢课必须悲观锁（`PESSIMISTIC_WRITE`）防超卖
- **异步抽签**：`LotteryService` 独立 Bean，避免 Spring @Async 代理自调用失效
- **第一志愿优先**：第一轮必须先统一结算第一志愿，再使用剩余名额结算第二志愿
- **结果唯一性**：同一学生在同一活动第一轮内最多只能有 1 条 `CONFIRMED`
- **退课口径统一**：第一轮已确认学生统一可退课，不区分第一志愿或第二志愿
- **退课后不回滚**：学生退课后仅进入第二轮，不触发第一轮重算或补录回滚
- **PER_CLASS 模式退课**：退课时必须找到对应的 `CourseClassCapacity` 记录减 1
- **学生登录**：初始密码 = studentNo，管理员批量初始化（BCrypt 加密）
- **时间窗口校验**：提交志愿/抢课前校验当前时间是否在对应轮次窗口内
- **第三轮防重复申请**：`InternalMessageRepository.existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot` 防止重复提交
- **照片路径**：开发环境相对路径 `src/main/resources/static/uploads/teachers/`，生产环境需配置绝对路径
- **data-class 属性冲突**：HTML data 属性避免使用 `data-class`（与 JS className 冲突），改用 `data-classid`
- **Thymeleaf onclick 限制**：字符串表达式禁止在 `th:onclick`，用 `data-*` + `DOMContentLoaded` 代替
