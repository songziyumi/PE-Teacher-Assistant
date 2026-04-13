# 邮箱绑定、忘记密码与选课邮件通知开发任务清单

更新时间：2026-04-12

## 1. 使用方式

本清单按 **P0 / P1 / P2** 拆分为可执行任务，适合作为：

- 开发排期清单
- 提测勾选清单
- 每次提交前的验收清单

执行建议：

- 每个任务尽量单独提交
- 优先保证 **P0 全部完成** 再进入 P1
- 涉及账号安全的任务必须补自动化测试

项目验证命令：

- 编译：`mvn -q -Dmaven.repo.local=.m2repo -DskipTests compile`
- Flutter 分析：`dart analyze --no-fatal-warnings`

---

## 2. P0：邮箱绑定 + 邮箱验证 + 忘记密码

目标：先把“邮箱可安全用于找回密码”这条主链路做通。

### P0-01 数据库迁移脚本

**目标**

- 补齐学生/教师邮箱验证字段
- 新增邮箱 token 表
- 新增邮件外发箱表

**执行项**

- [ ] 为 `student_accounts` 增加：
  - [ ] `email`
  - [ ] `email_verified`
  - [ ] `email_bound_at`
  - [ ] `email_verified_at`
  - [ ] `email_notify_enabled`
- [ ] 为 `teachers` 增加：
  - [ ] `email_verified`
  - [ ] `email_bound_at`
  - [ ] `email_verified_at`
  - [ ] `email_notify_enabled`
- [ ] 新建 `account_email_tokens`
- [ ] 新建 `mail_outbox`
- [ ] 为新增字段和新表补索引
- [ ] 编写检查脚本：
  - [ ] `check_email_binding_schema.sql`
  - [ ] `check_email_binding_data.sql`

**建议文件**

- `scripts/db/add_account_email_fields.sql`
- `scripts/db/check_account_email_schema.sql`
- `scripts/db/check_account_email_data.sql`

**完成标准**

- [ ] 本地数据库执行成功
- [ ] 检查脚本能输出字段与索引状态
- [ ] 不影响现有学生账号功能

---

### P0-02 后端实体、Repository、枚举补齐

**目标**

- 让邮箱验证与密码重置具备基础领域模型

**执行项**

- [ ] 扩展学生账号实体 `StudentAccount`
- [ ] 扩展教师实体 `Teacher`
- [ ] 新增邮箱 token 实体
- [ ] 新增邮件 outbox 实体
- [ ] 新增对应 Repository
- [ ] 增加按邮箱、按 token hash、按状态查询方法

**建议文件**

- `src/main/java/com/pe/assistant/entity/StudentAccount.java`
- `src/main/java/com/pe/assistant/entity/Teacher.java`
- `src/main/java/com/pe/assistant/entity/AccountEmailToken.java`
- `src/main/java/com/pe/assistant/entity/MailOutbox.java`
- `src/main/java/com/pe/assistant/repository/StudentAccountRepository.java`
- `src/main/java/com/pe/assistant/repository/TeacherRepository.java`
- `src/main/java/com/pe/assistant/repository/AccountEmailTokenRepository.java`
- `src/main/java/com/pe/assistant/repository/MailOutboxRepository.java`

**完成标准**

- [ ] JPA 实体字段与数据库结构一致
- [ ] Repository 方法覆盖查询需求
- [ ] `mvn compile` 通过

---

### P0-03 邮件基础设施接入

**目标**

- 提供发信能力，但不让发信失败阻断主业务

**执行项**

- [ ] 增加 `spring-boot-starter-mail`
- [ ] 增加 SMTP 配置项
- [ ] 新建 `MailService`
- [ ] 新建 `MailTemplateService`
- [ ] 新建 `MailOutboxService`
- [ ] 支持：
  - [ ] 直接写 outbox
  - [ ] 后台发送
  - [ ] 重试
  - [ ] 失败记录
- [ ] 增加 `app.mail.enabled` 开关

**建议文件**

- `pom.xml`
- `src/main/resources/application*.properties`
- `src/main/java/com/pe/assistant/service/MailService.java`
- `src/main/java/com/pe/assistant/service/MailTemplateService.java`
- `src/main/java/com/pe/assistant/service/MailOutboxService.java`
- `src/main/java/com/pe/assistant/task/MailOutboxSendTask.java`

**完成标准**

- [ ] 邮件能力可在配置开启时生效
- [ ] SMTP 失败不会影响业务接口成功返回
- [ ] outbox 状态可追踪

---

### P0-04 学生邮箱绑定与验证

**目标**

- 学生可以登录后绑定邮箱并完成验证

**执行项**

- [ ] 新增学生账号安全查询接口
- [ ] 新增学生发起邮箱绑定接口
- [ ] 新增学生确认邮箱绑定接口
- [ ] 增加邮箱格式校验
- [ ] 增加跨角色唯一性校验
- [ ] 绑定新邮箱时自动重置 `email_verified=false`
- [ ] 验证成功后写入：
  - [ ] `email`
  - [ ] `email_verified`
  - [ ] `email_bound_at`
  - [ ] `email_verified_at`
- [ ] 学生 Web 页面增加邮箱输入与验证状态提示
- [ ] 学生移动端页面增加邮箱输入与验证状态提示

**建议文件**

- `src/main/java/com/pe/assistant/controller/api/StudentApiController.java`
- `src/main/java/com/pe/assistant/service/StudentAccountService.java`
- `src/main/java/com/pe/assistant/service/AccountEmailService.java`
- `src/main/resources/templates/student/password.html`
- `mobile/lib/screens/student/student_password_screen.dart`
- `mobile/lib/services/student_service.dart`

**完成标准**

- [ ] 学生能提交邮箱绑定申请
- [ ] 收到验证邮件后能完成验证
- [ ] 已验证邮箱状态能在 Web / Mobile 显示

---

### P0-05 教师 / 管理员邮箱绑定与验证

**目标**

- 教师和管理员的邮箱从“资料字段”升级为“已验证邮箱”

**执行项**

- [ ] 扩展教师资料接口返回：
  - [ ] `emailVerified`
  - [ ] `emailNotifyEnabled`
- [ ] 新增教师发起邮箱绑定接口
- [ ] 新增教师确认邮箱绑定接口
- [ ] 修改教师资料页 / 移动端资料页
- [ ] 管理员复用教师接口或按角色复用同一逻辑

**建议文件**

- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
- `src/main/java/com/pe/assistant/controller/TeacherProfileController.java`
- `src/main/java/com/pe/assistant/service/AccountEmailService.java`
- `src/main/resources/templates/teacher/profile.html`
- `mobile/lib/screens/teacher/teacher_profile_screen.dart`
- `mobile/lib/services/teacher_service.dart`

**完成标准**

- [ ] 教师可绑定邮箱并完成验证
- [ ] 管理员登录后不受改造影响
- [ ] 已验证状态前后端一致

---

### P0-06 忘记密码主链路

**目标**

- 支持通过“账号 + 已验证邮箱”找回密码

**执行项**

- [ ] 新增匿名接口：
  - [ ] `POST /api/auth/password-reset/request`
  - [ ] `GET /api/auth/password-reset/verify`
  - [ ] `POST /api/auth/password-reset/confirm`
- [ ] 新增 `PasswordResetService`
- [ ] 请求找回时统一提示：
  - [ ] `如信息匹配，邮件已发送`
- [ ] 生成 `RESET_PASSWORD` token
- [ ] 重置成功后：
  - [ ] 更新密码
  - [ ] 废弃 token
  - [ ] 清理锁定状态
  - [ ] 清理失败次数
- [ ] 增加 Web 忘记密码页面
- [ ] 增加移动端忘记密码页面

**建议文件**

- `src/main/java/com/pe/assistant/controller/api/AuthApiController.java`
- `src/main/java/com/pe/assistant/service/PasswordResetService.java`
- `src/main/resources/templates/auth/forgot-password.html`
- `src/main/resources/templates/auth/reset-password.html`
- `mobile/lib/screens/auth/forgot_password_screen.dart`
- `mobile/lib/services/auth_service.dart`

**完成标准**

- [ ] 学生可用已验证邮箱找回密码
- [ ] 教师 / 管理员可用已验证邮箱找回密码
- [ ] 未验证邮箱不可找回密码
- [ ] 忘记密码接口不泄露账号存在性

---

### P0-07 安全与频控

**目标**

- 避免忘记密码接口被刷或被用来枚举账号

**执行项**

- [ ] 增加按账号频控
- [ ] 增加按 IP 频控
- [ ] 增加 token 过期校验
- [ ] 增加 token 单次使用校验
- [ ] 增加重复申请时旧 token 失效策略
- [ ] 记录请求 IP / UA
- [ ] 审计日志落库

**建议文件**

- `src/main/java/com/pe/assistant/service/PasswordResetService.java`
- `src/main/java/com/pe/assistant/service/AccountEmailService.java`
- `src/main/java/com/pe/assistant/service/RateLimitService.java`
- `src/main/java/com/pe/assistant/service/AuditLogService.java`

**完成标准**

- [ ] 频控超限时接口仍不暴露账号存在性
- [ ] token 过期后不可用
- [ ] 同 token 不可重复用

---

### P0-08 自动化测试与回归

**目标**

- 给邮箱绑定和忘记密码补足自动化保障

**执行项**

- [ ] Service 测试：
  - [ ] 邮箱唯一性
  - [ ] 验证 token
  - [ ] 重置密码
  - [ ] 锁定状态清理
- [ ] Controller 测试：
  - [ ] 匿名找回密码接口
  - [ ] 学生邮箱绑定接口
  - [ ] 教师邮箱绑定接口
- [ ] 移动端静态分析
- [ ] Web / Mobile 冒烟回归

**建议文件**

- `src/test/java/com/pe/assistant/service/...`
- `src/test/java/com/pe/assistant/controller/api/...`
- `mobile/lib/...`

**完成标准**

- [ ] 关键回归测试补齐
- [ ] `mvn compile` 通过
- [ ] 定向测试通过
- [ ] `dart analyze --no-fatal-warnings` 通过

---

## 3. P1：选课邮件通知

目标：在不破坏现有站内消息的前提下，补充邮件通知。

### P1-01 邮件模板与业务类型定义

**执行项**

- [ ] 定义邮件业务类型：
  - [ ] `EMAIL_VERIFY`
  - [ ] `PASSWORD_RESET`
  - [ ] `COURSE_RESULT`
  - [ ] `COURSE_ASSIGN`
  - [ ] `DROP_SUCCESS`
  - [ ] `ADMIN_ENROLL`
- [ ] 增加文本模板
- [ ] 增加 HTML 模板

**完成标准**

- [ ] 模板可独立渲染
- [ ] 标题、正文、变量替换正确

---

### P1-02 接入学生通知触发点

**执行项**

- [ ] 在第一轮结果通知处追加邮件 outbox 写入
- [ ] 在第二轮自动分配处追加邮件 outbox 写入
- [ ] 在第二轮未分配处追加邮件 outbox 写入
- [ ] 在退课成功处追加邮件 outbox 写入
- [ ] 在管理员加课成功处追加邮件 outbox 写入

**建议文件**

- `src/main/java/com/pe/assistant/service/StudentNotificationService.java`

**完成标准**

- [ ] 对应业务发生后生成 outbox 记录
- [ ] 未验证邮箱或关闭通知时不生成邮件任务

---

### P1-03 通知开关与偏好

**执行项**

- [ ] 学生增加“是否接收邮件通知”开关
- [ ] 教师增加“是否接收邮件通知”开关
- [ ] Web / Mobile 展示状态

**完成标准**

- [ ] 开关状态可保存
- [ ] 关闭后不再入队邮件

---

### P1-04 发送任务与失败重试

**执行项**

- [ ] 增加定时发送任务
- [ ] 增加退避重试
- [ ] 增加失败次数上限
- [ ] 增加失败原因记录

**完成标准**

- [ ] 邮件发送失败不影响业务事务
- [ ] 可重试
- [ ] 最终失败可排查

---

### P1-05 回归测试

**执行项**

- [ ] 验证触发点是否写入 outbox
- [ ] 验证未验证邮箱不会发邮件
- [ ] 验证通知开关关闭时不发送
- [ ] 验证发送失败不影响主流程

**完成标准**

- [ ] P1 定向测试通过

---

## 4. P2：后台治理与运营支持

目标：让邮箱体系可维护、可运营、可排查。

### P2-01 管理端展示邮箱状态

**执行项**

- [ ] 学生账号管理页展示：
  - [ ] 邮箱
  - [ ] 是否已验证
  - [ ] 邮件通知开关
- [ ] 教师列表页展示邮箱验证状态

**完成标准**

- [ ] 管理员可查看邮箱绑定率与验证状态

---

### P2-02 导入 / 导出支持

**执行项**

- [ ] 学生邮箱导入模板
- [ ] 学生邮箱导入逻辑
- [ ] 学生邮箱导出
- [ ] 邮箱绑定状态导出

**完成标准**

- [ ] 导入可校验邮箱格式与重复数据
- [ ] 导出字段齐全

---

### P2-03 重发验证邮件

**执行项**

- [ ] 管理端支持重发验证邮件
- [ ] 用户端支持重发验证邮件
- [ ] 增加重发频控

**完成标准**

- [ ] 不产生无限重发风险
- [ ] 能覆盖“用户没收到邮件”的常见场景

---

### P2-04 邮件发送监控与问题排查

**执行项**

- [ ] 邮件发送日志列表
- [ ] 失败原因查看
- [ ] 手动重试
- [ ] 统计报表

**完成标准**

- [ ] 失败邮件可追踪
- [ ] 可手动重试部分失败记录

---

## 5. 推荐开发顺序

推荐按以下顺序执行：

1. `P0-01` 数据库迁移脚本
2. `P0-02` 实体与 Repository
3. `P0-03` 邮件基础设施
4. `P0-04` 学生邮箱绑定
5. `P0-05` 教师 / 管理员邮箱绑定
6. `P0-06` 忘记密码
7. `P0-07` 频控与安全
8. `P0-08` 自动化测试
9. `P1-01 ~ P1-05` 选课邮件通知
10. `P2-01 ~ P2-04` 后台治理

不推荐：

- 先做选课邮件，再补邮箱验证
- 未完成邮箱验证就直接上线忘记密码

---

## 6. 里程碑验收

### M1：P0 完成

- [ ] 学生邮箱绑定验证可用
- [ ] 教师 / 管理员邮箱绑定验证可用
- [ ] 忘记密码可用
- [ ] 安全频控已到位
- [ ] 自动化测试通过

### M2：P1 完成

- [ ] 选课相关邮件通知可用
- [ ] 邮件发送失败不影响主业务
- [ ] 用户通知开关可控

### M3：P2 完成

- [ ] 管理端可查看邮箱状态
- [ ] 可导入 / 导出邮箱
- [ ] 可追踪邮件发送失败

---

## 7. 本周最小落地版本（推荐）

如果希望先快速落地一个可上线的最小版本，建议只做：

- [ ] `P0-01`
- [ ] `P0-02`
- [ ] `P0-03`
- [ ] `P0-04`
- [ ] `P0-05`
- [ ] `P0-06`
- [ ] `P0-07`
- [ ] `P0-08`

也就是：

- 先完成邮箱绑定
- 再完成邮箱验证
- 再完成忘记密码
- 暂时不做选课邮件通知和后台运营增强

这是当前投入产出比最高、风险最可控的版本。

