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
  1. **按课程顺序逐项执行，每项间隔 1 分钟**（异步后台执行，管理员可实时查看进度）
  2. 每门课抽签前，**先查出本活动已确认的学生 ID 集合**：
     - 1志愿：全量参与抽签
     - 2志愿：**排除已在前面课程中中签的学生**（实时冲突消解，无需事后处理）
     - 被排除的2志愿直接标为 `CANCELLED`
  3. 报名人数 ≤ 名额 → 全部确认；报名人数 > 名额 → 随机抽取
  4. 所有课程处理完毕 → 自动推进到第二轮（`ROUND2`）
  5. 管理员页面通过 JS 轮询 `/admin/courses/events/{id}/lottery-status` 显示进度

### 两轮之间
- 已确认的学生可以主动退课 → 空出名额加入第二轮池，该学生同时进入第二轮

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
lotteryNote（String，抽签进度说明，如"正在处理：篮球 (2/5)"）
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
- 第一轮抽签：从所有报名该课程的学生中按优先级（1志愿→2志愿）随机抽取
- 第二轮：`totalCapacity - currentCount` = 剩余名额，先到先得

### PER_CLASS 模式（按班名额）
- `course.totalCapacity` = `SUM(各班 maxCapacity)`（冗余）
- 第一轮：**按班级分别抽签**，A班报名者从A班名额中抽，B班从B班名额中抽
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
  for each course（顺序）:
    confirmedIds = 查本活动已CONFIRMED的studentId集合
    1志愿全量参与 → 随机抽签 → CONFIRMED / LOTTERY_FAIL
    2志愿排除confirmedIds → 随机抽签 → CONFIRMED / LOTTERY_FAIL
    被排除的2志愿 → CANCELLED
    更新 lotteryNote → sleep(60s)
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
- **PER_CLASS 模式退课**：退课时必须找到对应的 `CourseClassCapacity` 记录减 1
- **学生登录**：初始密码 = studentNo，管理员批量初始化（BCrypt 加密）
- **时间窗口校验**：提交志愿/抢课前校验当前时间是否在对应轮次窗口内
- **第三轮防重复申请**：`InternalMessageRepository.existsByTypeAndRelatedCourseIdAndSenderIdAndStatusNot` 防止重复提交
- **照片路径**：开发环境相对路径 `src/main/resources/static/uploads/teachers/`，生产环境需配置绝对路径
- **data-class 属性冲突**：HTML data 属性避免使用 `data-class`（与 JS className 冲突），改用 `data-classid`
- **Thymeleaf onclick 限制**：字符串表达式禁止在 `th:onclick`，用 `data-*` + `DOMContentLoaded` 代替


## 需求概览

### 角色
| 角色 | 操作 |
|------|------|
| 管理员 | 创建选课活动、设置课程、配置名额模式、手动触发抽签、调整选课结果 |
| 学生 | 第一轮填报志愿（2门）、第二轮先到先得抢课、退课 |
| 教师 | 查看所授课程的报名名单（只读） |

---

## 选课流程

### 第一轮（志愿填报）
- 时间窗口：`round1Start` ～ `round1End`
- 每个学生最多提交 **2 个志愿**（1志愿 + 2志愿，有优先级顺序）
- 允许课程超额（不做实时拦截）
- **第一轮截止后**，管理员手动触发"抽签结算"（或定时任务自动执行）：
  1. 遍历每门课程：
     - 报名人数 ≤ 名额 → 全部确认
     - 报名人数 > 名额 → 在报名学生中随机抽取「名额数量」个学生，其余进入第二轮
  2. 冲突处理：如果同一学生同时中了 1志愿 和 2志愿：
     - 保留 **1志愿**，从 2志愿课程中移除该学生
     - 2志愿课程空出的名额进入第二轮池
  3. 所有未获任何课程的学生自动进入第二轮

### 两轮之间
- 已确认的学生可以主动退课 → 空出名额加入第二轮池，该学生同时进入第二轮

### 第二轮（先到先得）
- 时间窗口：`round2Start` ～ `round2End`
- 仅面向第一轮未确认的学生（含主动退课学生）
- 按提交时间顺序确认名额
- 名额满后提示学生选其他有余量的课程
- 目标：所有参与学生都获得至少一门课程
- 第二轮结束后仍无课程的学生：系统标记，管理员可手动处理

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

#### 4. `EventStudent`（活动参与学生名单）
```
id
event（SelectionEvent）
student（Student）
唯一约束：(event_id, student_id)
```

#### 5. `CourseSelection`（选课志愿记录）
```
id
event（SelectionEvent）
course（Course）
student（Student）
preference（int：1=1志愿，2=2志愿）
round（int：1=第一轮，2=第二轮）
status（PENDING / CONFIRMED / LOTTERY_FAIL / CANCELLED）
selectedAt（LocalDateTime）
confirmedAt（LocalDateTime，确认时间）
唯一约束：(event_id, student_id, preference) —— 每轮每个志愿只能填一门
```

#### 6. 修改 `entity/Student.java`
- 新增 `password`（String，BCrypt）
- 新增 `enabled`（Boolean，默认 true）
- `studentNo` 作为登录用户名，初始密码 = studentNo

#### 7. 修改 `entity/School.java`
- 无需 maxCoursesPerStudent 字段（每人限选由 Event 逻辑控制：第一轮2志愿，最终1门）

---

## 名额模式详解

### GLOBAL 模式（全局名额）
- `course.totalCapacity` = 总名额
- `CourseClassCapacity` 表不使用
- 第一轮抽签：从所有报名该课程的学生中随机抽取 `totalCapacity` 人
- 第二轮：`totalCapacity - confirmedCount` = 剩余名额，先到先得

### PER_CLASS 模式（按班名额）
- `course.totalCapacity` = `SUM(所有班的 maxCapacity)`（冗余，方便展示）
- `CourseClassCapacity` 表记录每班名额
- 第一轮抽签：**按班级分别抽签**，A班报名者从A班名额中抽，B班从B班名额中抽
- 第二轮：学生只能抢**自己班级**对应的剩余名额

---

## 认证层

### 修改 `UserDetailsServiceImpl`
- `loadUserByUsername`：先查 TeacherRepository（按 username），再查 StudentRepository（按 studentNo）
- 学生 authority = `ROLE_STUDENT`

### 修改 `SecurityConfig`
- Web FilterChain：
  - `/student/**` → `hasRole("STUDENT")`
  - 成功处理器：STUDENT 跳转 `/student/courses`
- API FilterChain：
  - `/api/student/**` → `hasRole("STUDENT")`

---

## 业务层（`service/CourseService.java` + `service/SelectionEventService.java`）

### `SelectionEventService`
- `processRound1(eventId)` — 抽签结算（管理员手动触发或定时任务）：
  ```
  for each course:
    if GLOBAL: 随机从 PENDING 记录中抽取 totalCapacity 条 → CONFIRMED，其余 → LOTTERY_FAIL
    if PER_CLASS: 按班分组后各自随机抽取
  冲突处理:
    找出同时有1志愿CONFIRMED + 2志愿CONFIRMED的学生
    2志愿 → CANCELLED，对应CourseClassCapacity.currentCount--
  更新Event.status = ROUND2
  ```
- `getRound2EligibleStudents(eventId)` — 返回第一轮未确认的学生列表

### `CourseService`
- `submitPreference(studentId, eventId, courseId, preference)` — 第一轮提交志愿（幂等）
- `selectRound2(studentId, eventId, courseId)` — 第二轮抢课（加悲观锁）：
  ```
  检查：学生是否在第二轮资格名单内
  检查：该学生是否已有CONFIRMED记录（防止重复选）
  GLOBAL: SELECT FOR UPDATE course → currentCount < totalCapacity → 写入CONFIRMED，+1
  PER_CLASS: SELECT FOR UPDATE courseClassCapacity (by student's class) → currentCount < maxCapacity → 写入CONFIRMED，+1
  ```
- `dropCourse(studentId, selectionId)` — 退课：status→CANCELLED，对应名额-1，学生加入第二轮资格

---

## Web 端

### 控制器
| 控制器 | 路由 | 功能 |
|--------|------|------|
| `AdminCourseController` | `/admin/courses/**` | 活动/课程 CRUD，触发抽签，查看名单，手动调整 |
| `StudentCourseController` | `/student/**` | 浏览课程，提交志愿，退课，查看我的选课 |
| `TeacherCourseController` | `/teacher/courses/**` | 查看报名名单（只读） |

### 页面
| 模板 | 说明 |
|------|------|
| `templates/admin/selection-events.html` | 活动列表 + 新增/编辑弹窗 |
| `templates/admin/courses.html` | 课程管理（名额模式切换，PER_CLASS时按班配置） |
| `templates/admin/course-enrollments.html` | 报名名单 + 手动调整 |
| `templates/student/courses.html` | 学生选课页（第一轮展示志愿按钮，第二轮展示抢课按钮） |
| `templates/student/my-courses.html` | 我的选课状态（PENDING/CONFIRMED/LOTTERY_FAIL） |
| `templates/teacher/courses.html` | 教师查看名单 |

---

## REST API 端（Flutter）

### 修改 `AuthApiController`
- `/api/auth/login` 扩展：支持学生登录（role=STUDENT 时从 Student 表加载）

### 新增 `StudentApiController`（`/api/student/**`）
```
GET  /api/student/events/current          当前活动信息
GET  /api/student/courses                 可选课程列表（含剩余名额、已选状态）
POST /api/student/courses/{id}/prefer     第一轮提交志愿（参数：preference=1或2）
POST /api/student/courses/{id}/select     第二轮抢课
DELETE /api/student/selections/{id}       退课
GET  /api/student/my-selections           我的选课
```

---

## Flutter 移动端

### 新增文件
| 文件 | 说明 |
|------|------|
| `mobile/lib/models/course.dart` | Course 模型 |
| `mobile/lib/models/selection_event.dart` | 活动模型（含轮次时间和状态） |
| `mobile/lib/models/course_selection.dart` | 选课记录模型 |
| `mobile/lib/services/student_course_service.dart` | API 调用封装 |
| `mobile/lib/screens/student/course_list.dart` | 课程列表（自动区分第一轮/第二轮 UI） |
| `mobile/lib/screens/student/my_courses.dart` | 我的选课 |

### 修改文件
- `mobile/lib/app.dart`：添加 STUDENT 角色路由 `/student/courses`、`/student/my-courses`
- `mobile/lib/services/auth_service.dart`：支持 STUDENT 角色跳转

---

## 开发顺序

1. **数据层**：Student(+password)、SelectionEvent、Course、CourseClassCapacity、EventStudent、CourseSelection、Repository
2. **认证层**：UserDetailsServiceImpl 扩展、SecurityConfig 路由
3. **业务层**：SelectionEventService（抽签结算）、CourseService（志愿提交/抢课/退课）
4. **Web 后端**：3个 Controller
5. **Web 模板**：6个 HTML
6. **REST API**：AuthApiController 扩展、StudentApiController
7. **Flutter**：model、service、screens、路由

---

## 关键约束与风险点

- **并发安全**：第二轮抢课必须悲观锁（`PESSIMISTIC_WRITE`）防超卖
- **抽签幂等**：`processRound1` 加状态检查，避免重复执行
- **PER_CLASS 模式退课**：退课时必须找到对应的 `CourseClassCapacity` 记录减 1，而不是操作 course.totalCapacity
- **学生登录**：初始密码 = studentNo，管理员导入学生时批量初始化密码（BCrypt 加密）
- **时间窗口校验**：提交志愿/抢课前校验当前时间是否在对应轮次窗口内
