# 项目启动上下文（Session Start）

> 本文件合并自 `CLAUDE.md` + `course-selection-plan.md`，用于每次开始开发前快速加载上下文。
> 读取顺序：先看本文件；需要细节时再回看原始文件。

## 1. 项目定位
- 项目：体育教师助手系统（PE Teacher Assistant）
- 目标：服务学校体育教学管理，包含 Web 管理端 + Flutter 移动端
- 当前迭代主题：学生抢课模块 v2（三轮选课 + 消息申请）

## 2. 技术与结构基线

### 技术栈
- Java 17
- Spring Boot 3.2.3
- Spring Security + JWT（API 无状态认证）
- Spring Data JPA + MySQL
- Thymeleaf
- Flutter 3.x（`mobile/`）

### 关键目录
- `src/main/java/com/pe/assistant/`：后端代码（config/controller/entity/repository/service/security）
- `src/main/resources/templates/`：Web 模板
- `mobile/lib/`：Flutter 代码（models/services/screens）

## 3. 当前迭代（抢课 v2）范围

### 角色能力
- 管理员：活动/课程配置、触发抽签、调整结果
- 学生：第一轮志愿、第二轮抢课、第三轮申请、退课
- 教师：查看名单、审批第三轮申请、维护个人主页

### 选课流程（以 v2 为准）
1. 第一轮（`ROUND1`）：
- 学生提交 1/2 志愿（最多 2 门）
- 截止后管理员触发异步抽签（`PROCESSING`）
- 2 志愿排除已中签学生；冲突实时消解
2. 第二轮（`ROUND2`）：
- 面向第一轮未确认学生（含主动退课学生）
- 先到先得，悲观锁防超卖（`PESSIMISTIC_WRITE`）
3. 第三轮（活动 `CLOSED` 后）：
- 无确认课程的学生可通过站内消息向教师申请
- 教师同意后调用 `adminEnroll` 入课，拒绝则通知学生

### 核心实体（新增/扩展）
- `SelectionEvent`（含 `lotteryNote`、轮次时间、状态）
- `Course`（`capacityMode`、`totalCapacity`、`currentCount`）
- `CourseClassCapacity`（PER_CLASS 模式班级名额）
- `CourseSelection`（志愿/轮次/状态）
- `EventStudent`（活动参与名单，可选）
- `InternalMessage`（站内消息/课程申请）
- 扩展：`Teacher`（主页字段）、`Student`（`password`/`enabled`）

### 核心服务
- `LotteryService`：独立 Bean + `@Async`，顺序抽签、更新 `lotteryNote`
- `SelectionEventService`：`processRound1()` / `getLotteryProgress()`
- `CourseService`：`submitPreference` / `selectRound2` / `dropCourse` / `adminEnroll`
- `MessageService`：申请发送、审批同意/拒绝、收件箱、未读数

### 关键控制器与页面
- 管理端：`AdminCourseController`、`admin/selection-events.html`、`admin/course-event-detail.html`
- 学生端：`StudentCourseController`、`student/courses.html`、`student/my-courses.html`、`student/messages.html`
- 教师端：`TeacherCourseController`、`TeacherProfileController`、`MessageController`、`teacher/messages.html`

### 移动端 API（学生）
- `GET /api/student/events/current`
- `GET /api/student/courses`
- `POST /api/student/courses/{id}/prefer`
- `POST /api/student/courses/{id}/select`
- `DELETE /api/student/selections/{id}`
- `GET /api/student/my-selections`

## 4. 安全与规则基线

### 认证与授权
- `UserDetailsServiceImpl`：先查 `Teacher.username`，再查 `Student.studentNo`
- 学生角色：`ROLE_STUDENT`
- Web 路由：`/student/**` 需学生权限
- API 路由：`/api/student/**` 需学生权限
- 学生登录后默认跳转：`/student/courses`

### 必守约束（高优先级）
- 第二轮并发：必须使用悲观锁，防止超卖
- 抽签执行：`LotteryService` 必须保持独立 Bean，避免 `@Async` 自调用失效
- 退课回收：PER_CLASS 必须回收到对应 `CourseClassCapacity`
- 时间校验：志愿提交/抢课前必须校验轮次时间窗口
- 防重复申请：第三轮课程申请需查重
- 模板规范：避免 `th:onclick` 字符串表达式，优先 `data-* + JS`

## 5. 版本与分支基线
- 主分支：`main`
- 移动端分支：`flutter-mobile-app`
- 当前开发主题分支建议：`feature/course-selection*`

## 6. 每次开工清单（固定流程）
1. 运行 `git status --short --branch` 确认分支与工作区
2. 明确本次任务属于：数据层 / 认证层 / 业务层 / Web / API / Flutter
3. 若改动抢课逻辑，优先回归检查：
- 第一轮抽签状态流转（`ROUND1 -> PROCESSING -> ROUND2`）
- 第二轮并发与名额回收
- 第三轮申请审批链路
4. 涉及权限改动时，同时检查 Web 与 API 两条安全链
5. 提交前最少执行：`mvn test`（或至少 `mvn compile`）

## 7. 常用命令
```bash
mvn spring-boot:run
mvn clean package -DskipTests
mvn test
```

## 8. 原始资料入口
- 全局项目说明：`CLAUDE.md`
- 抢课专项方案：`course-selection-plan.md`
- 本地代理权限配置：`.claude/settings.local.json`

## 9. 备注
- `course-selection-plan.md` 存在历史版本内容重复；本文件已按 v2 方案去重整合。
- 后续若抢课流程或权限发生变更，优先更新本文件，再同步更新原始文档。
