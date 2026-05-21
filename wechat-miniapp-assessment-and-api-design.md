# 微信小程序改造评估与 API 设计

## 1. 结论

这个项目适合做微信小程序，且不建议重写后端。

当前后端已经具备这些基础能力：

- 已有独立的 `/api/**` 接口层，不是纯 Thymeleaf 页面应用
- 已有 JWT 鉴权，移动端已在使用 `Authorization: Bearer <token>`
- 已有学生端、教师端、管理员端的大量 API
- 仓库内已经有 `mobile/` Flutter 客户端，说明业务流程已经做过移动端抽象

因此，小程序最合理的路线是：

1. 继续复用现有 Spring Boot 后端
2. 优先复用已有 `/api/auth`、`/api/student`、`/api/teacher`
3. 只为小程序补“缺的接口”和“更适合小程序的聚合接口”
4. 管理端先继续保留 Web，不作为小程序一期重点

## 2. 当前项目现状评估

### 2.1 后端架构现状

从代码看，当前系统分成两层：

- 页面控制器：`src/main/java/com/pe/assistant/controller`
- JSON API 控制器：`src/main/java/com/pe/assistant/controller/api`

其中小程序最相关的是：

- [`src/main/java/com/pe/assistant/controller/api/AuthApiController.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\controller\api\AuthApiController.java)
- [`src/main/java/com/pe/assistant/controller/api/StudentApiController.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\controller\api\StudentApiController.java)
- [`src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\controller\api\TeacherApiController.java)
- [`src/main/java/com/pe/assistant/controller/api/StudentSecurityApiController.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\controller\api\StudentSecurityApiController.java)
- [`src/main/java/com/pe/assistant/controller/api/TeacherSecurityApiController.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\controller\api\TeacherSecurityApiController.java)

### 2.2 认证现状

当前 API 已支持 JWT：

- [`src/main/java/com/pe/assistant/config/SecurityConfig.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\config\SecurityConfig.java)
- [`src/main/java/com/pe/assistant/security/JwtUtil.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\security\JwtUtil.java)
- [`src/main/java/com/pe/assistant/security/JwtAuthFilter.java`](D:\code\PE_TEACHER_ASSISTANT_JAVA\src\main\java\com\pe\assistant\security\JwtAuthFilter.java)

现状特点：

- `/api/auth/login` 已能返回 token
- `/api/auth/me` 已能返回当前登录人信息
- 学生、教师共用同一个登录入口
- 现有 token 里包含 `role` 和 `schoolId`
- API 过滤链已经按角色限制访问

这对小程序是直接可用的。

### 2.3 业务能力现状

学生端现有 API 已覆盖：

- 当前选课活动
- 课程列表
- 志愿提交、草稿保存、确认
- 第二轮抢课
- 退课
- 我的选课结果
- 第三轮申请课
- 站内消息
- 密码修改
- 邮箱绑定与通知开关

教师端现有 API 已覆盖：

- 个人资料
- 班级列表
- 学生列表与编辑
- 考勤查询与批量保存
- 体测录入
- 学期成绩录入
- 课程申请审批
- 消息中心
- 权限读取
- 数据导出
- 操作时间线

这意味着：

- 学生小程序一期几乎可以直接落地
- 教师小程序一期也可以落地
- 管理端不适合一期做进小程序

### 2.4 已有移动端现状

仓库中已有 Flutter 客户端：

- [`mobile/lib/services/auth_service.dart`](D:\code\PE_TEACHER_ASSISTANT_JAVA\mobile\lib\services\auth_service.dart)
- [`mobile/lib/services/student_service.dart`](D:\code\PE_TEACHER_ASSISTANT_JAVA\mobile\lib\services\student_service.dart)
- [`mobile/lib/services/teacher_service.dart`](D:\code\PE_TEACHER_ASSISTANT_JAVA\mobile\lib\services\teacher_service.dart)
- [`mobile/lib/services/api_service.dart`](D:\code\PE_TEACHER_ASSISTANT_JAVA\mobile\lib\services\api_service.dart)

说明后端已经满足“移动端前后端分离”这个前提。微信小程序不需要重新发明协议，只需要把 Flutter 当前依赖的接口，重新映射到小程序前端即可。

## 3. 适合小程序的功能边界

### 3.1 一期建议做的功能

学生端：

- 登录
- 我的信息
- 当前选课活动
- 课程列表
- 志愿填报
- 抢课
- 我的课程
- 课程申请
- 消息中心
- 密码修改

教师端：

- 登录
- 首页概览
- 我的班级
- 学生列表
- 考勤登记
- 课程申请审批
- 消息中心
- 基本资料

### 3.2 二期再做的功能

- 教师体测录入
- 教师成绩录入
- 操作时间线
- 邮箱绑定
- 批量处理增强
- 文件导出替代方案

### 3.3 不建议一期做进小程序的功能

- 超级管理员
- 管理员全量后台
- 大批量导入导出
- 复杂表格式编辑
- 需要下载 Excel 的场景

原因很直接：这些功能更适合 Web 后台，不适合小程序交互模型。

## 4. 现有 API 的可复用程度

### 4.1 可直接复用

这些接口可以直接给小程序使用：

- `POST /api/auth/login`
- `GET /api/auth/me`
- `GET /api/student/events/current`
- `GET /api/student/courses`
- `GET /api/student/courses/requestable`
- `POST /api/student/courses/{courseId}/prefer`
- `POST /api/student/courses/save-draft`
- `POST /api/student/courses/confirm`
- `POST /api/student/courses/{courseId}/select`
- `DELETE /api/student/selections/{selectionId}`
- `GET /api/student/my-selections`
- `POST /api/student/courses/{courseId}/request`
- `GET /api/student/messages`
- `GET /api/student/messages/unread-count`
- `POST /api/student/messages/send`
- `POST /api/student/messages/{id}/read`
- `POST /api/student/password/change`
- `GET /api/student/account-security`
- `POST /api/student/email/bind/request`
- `POST /api/student/email/notify-toggle`
- `GET /api/teacher/profile`
- `GET /api/teacher/profile/stats`
- `GET /api/teacher/classes`
- `GET /api/teacher/course-requests`
- `GET /api/teacher/course-requests/summary`
- `GET /api/teacher/course-requests/{id}`
- `POST /api/teacher/course-requests/{id}/approve`
- `POST /api/teacher/course-requests/{id}/reject`
- `POST /api/teacher/course-requests/batch-handle`
- `GET /api/teacher/messages`
- `GET /api/teacher/messages/unread-count`
- `POST /api/teacher/messages/{id}/read`
- `GET /api/teacher/permissions`

### 4.2 需要谨慎复用

这些接口能复用，但小程序体验通常不够理想：

- 文件上传接口
- Excel 导出接口
- 大表单批量录入接口
- 响应字段过多、明显面向页面模板的接口

### 4.3 建议补一层小程序聚合接口

虽然现有 API 能跑，但为了降低小程序页面请求次数，建议新增 `/api/miniapp/**` 聚合接口，而不是完全依赖前端拼装多个请求。

## 5. 当前缺口

### 5.1 登录模式缺口

当前只有“账号密码登录 + JWT”，没有微信原生登录能力。

这意味着一期可以先这样做：

- 小程序账号密码登录
- 后端继续调用现有 `/api/auth/login`

二期再扩展：

- 小程序 `wx.login`
- 后端换取 `openid`
- 建立微信账号与教师/学生账号绑定关系

### 5.2 会话续期能力偏弱

当前只有 access token，没有明确的 refresh token 机制。  
对小程序来说，这不是硬阻塞，但会带来这些问题：

- token 过期后只能重新登录
- 长时间不使用后用户体验一般

建议二期补：

- `POST /api/auth/refresh`
- refresh token 持久化或短期签发方案

### 5.3 小程序首页缺少聚合数据接口

当前小程序首页如果直接复用已有 API，通常需要多次请求：

- 当前用户
- 当前活动
- 课程
- 我的选课
- 未读消息数

建议补首页聚合接口。

### 5.4 下载与导出能力不适配

当前教师端已有导出接口，但小程序里：

- Excel 下载体验弱
- 文件存储和分享链路复杂
- 审批、考勤等更适合“列表查看 + 云端导出链接”而不是直接二进制下载

建议策略：

- 一期不做导出
- 二期改成“生成文件任务 + 下载地址”

## 6. 推荐的小程序 API 方案

## 6.1 设计原则

- 复用现有服务层，不重写业务规则
- 接口按小程序页面组织，而不是按 Web 页面组织
- 保留现有 `/api/student`、`/api/teacher`，新增 `/api/miniapp`
- `/api/miniapp` 只做聚合和小程序特有能力，不重复造轮子

## 6.2 推荐目录

建议新增控制器目录：

- `src/main/java/com/pe/assistant/controller/api/miniapp`

建议先拆成：

- `MiniAppAuthApiController`
- `MiniAppStudentApiController`
- `MiniAppTeacherApiController`
- `MiniAppCommonApiController`

## 6.3 一期接口设计

### 6.3.1 认证

#### `POST /api/miniapp/auth/login`

用途：

- 小程序账号密码登录

请求：

```json
{
  "username": "T001",
  "password": "123456"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "jwt-token",
    "user": {
      "id": 12,
      "name": "张老师",
      "role": "TEACHER",
      "schoolId": 1,
      "schoolName": "第一中学"
    },
    "mustChangePassword": false
  }
}
```

备注：

- 一期可以内部直接复用现有 `AuthApiController`
- 但建议把返回结构改成更适合前端消费的 `token + user`

#### `GET /api/miniapp/auth/me`

用途：

- 小程序冷启动恢复会话

### 6.3.2 通用首页

#### `GET /api/miniapp/home`

按角色返回首页聚合数据。

学生返回：

- 用户信息
- 当前活动摘要
- 我的选课摘要
- 未读消息数
- 快捷入口

教师返回：

- 用户信息
- 班级数
- 待审批数
- 未读消息数
- 最近操作摘要

这样可以替代前端多次并发请求。

### 6.3.3 学生端

#### `GET /api/miniapp/student/dashboard`

聚合：

- 当前活动
- 当前可选课程数量
- 我的已选课程
- 第三轮申请状态
- 未读消息数

#### `GET /api/miniapp/student/courses`

建议保留现有字段，但额外统一补：

- `canPrefer`
- `canSelect`
- `actionLabel`
- `actionDisabledReason`

这样前端按钮逻辑更简单。

#### `GET /api/miniapp/student/my-courses`

建议在现有 `my-selections` 基础上补：

- 活动名
- 课程教师名
- 当前轮次文案

#### `GET /api/miniapp/student/messages/summary`

聚合：

- 未读总数
- 课程申请待处理数
- 最近 5 条消息

### 6.3.4 教师端

#### `GET /api/miniapp/teacher/dashboard`

聚合：

- 用户资料摘要
- 班级列表摘要
- 待审批数
- 未读消息数
- 近 5 条操作记录

#### `GET /api/miniapp/teacher/classes`

建议直接返回：

- `id`
- `name`
- `type`
- `gradeName`
- `studentCount`
- `pendingAttendanceCount`

避免首页再多次查班级附加信息。

#### `GET /api/miniapp/teacher/classes/{classId}/students`

建议保留筛选参数，但补充：

- 默认精简字段集
- 支持 `keyword`
- 支持 `studentStatus`
- 支持 `page`、`size`

理由：

- 小程序列表更需要分页，当前教师学生列表更偏一次性全量拉取

#### `GET /api/miniapp/teacher/course-requests/dashboard`

聚合：

- 审批统计
- 待处理列表
- 最近处理记录

比现在前端分别请求 summary、list、detail 更适合小程序。

## 7. 与现有 API 的关系

建议这样分层：

### 7.1 保持不动

- 现有 `/api/student/**`
- 现有 `/api/teacher/**`
- 现有 `/api/auth/**`

这些继续服务现有 Flutter 客户端和其他调用方。

### 7.2 新增但不替换

- `/api/miniapp/**`

用于：

- 聚合接口
- 微信登录接口
- 小程序专用轻量返回结构

### 7.3 后续逐步收敛

等小程序稳定后，再决定是否把 Flutter 和小程序统一迁到 `/api/miniapp/**`。  
现阶段不建议直接重构现有 API，以免影响已经可用的移动端。

## 8. 数据与安全建议

### 8.1 一期建议

- 继续使用 JWT
- 小程序本地只存 token
- 继续使用现有角色权限控制
- 密码登录沿用当前逻辑

### 8.2 二期建议

新增微信账号绑定表，建议字段：

- `id`
- `principalType`
- `principalId`
- `openid`
- `unionId`
- `boundAt`
- `lastLoginAt`
- `status`

对应能力：

- 微信登录
- 账号绑定
- 解绑
- 仅允许一个微信号绑定一个主体账号

### 8.3 风险点

- 当前 CORS 是开放式配置，虽不影响小程序，但对公网接口偏宽松
- 当前 JWT 看起来只有单 token，没有 refresh token
- 当前部分返回 message 存在乱码历史痕迹，小程序前端会更敏感，建议顺手统一 UTF-8 输出文案

## 9. 推荐实施顺序

### 阶段 1：最小可用版

- 直接复用 `/api/auth/login`
- 直接复用现有 `/api/student/**`
- 直接复用现有 `/api/teacher/**`
- 小程序先做学生端和教师端首页、消息、选课、审批

目标：

- 尽快跑通端到端流程

### 阶段 2：补小程序聚合接口

- 新增 `/api/miniapp/home`
- 新增 `/api/miniapp/student/dashboard`
- 新增 `/api/miniapp/teacher/dashboard`
- 新增轻量分页接口

目标：

- 降低请求数
- 简化前端状态管理

### 阶段 3：接入微信能力

- 微信登录
- 账号绑定
- 模板消息或订阅消息

目标：

- 提升留存和通知触达能力

## 10. 具体开发建议

后端建议先做这些改造：

1. 抽出小程序聚合 DTO，不直接返回大量 `Map<String, Object>`
2. 给学生端、教师端首页补聚合接口
3. 给学生列表、消息列表补分页参数
4. 明确 token 过期策略
5. 统一 API 错误文案编码

前端建议这样切入：

1. 先做账号密码登录
2. 先做学生端
3. 教师端只做高频功能
4. 管理端继续保留 Web

## 11. 最终建议

对这个项目来说，微信小程序不是“能不能做”的问题，而是“应该怎样低风险上线”的问题。

最稳妥的策略是：

- 后端不重写
- 先复用现有 JWT API
- 先做学生端和教师端高频场景
- 再补小程序聚合接口
- 最后再做微信登录绑定

如果继续往下做，下一步最合理的是：

1. 先把小程序一期页面清单定下来
2. 再把 `/api/miniapp/**` 的接口 DTO 和 controller 骨架补进项目
