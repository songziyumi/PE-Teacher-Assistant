# 邮箱绑定、忘记密码与选课邮件通知落地方案

更新时间：2026-04-12

## 1. 目标

为当前项目补齐以下能力：

1. 账号绑定邮箱
2. 邮箱验证
3. 通过邮箱找回密码
4. 向已验证邮箱发送选课相关邮件通知

本方案默认遵循以下原则：

- 邮箱仅在 **验证通过后** 才能用于安全能力
- 忘记密码只发送 **一次性重置链接 / 验证码**，不发送明文新密码
- 选课邮件只是 **站内消息的补充通道**，不替代站内消息
- 学生、教师、管理员的邮箱应纳入统一治理

---

## 2. 现状分析

### 2.1 当前已有能力

- 教师账号已有 `email` 字段，但目前只是资料字段
- 学生账号当前无邮箱字段
- 学生已具备账号安全信息域：
  - `loginId`
  - `loginAlias`
  - `passwordResetRequired`
  - 锁定状态 / 登录失败次数
- 学生已有站内消息通知能力
- 选课结果通知已有稳定业务触发点

### 2.2 当前缺失能力

- 无统一邮箱验证体系
- 无邮箱唯一性治理
- 无忘记密码自助流程
- 无邮件发送依赖与 SMTP 配置
- 无邮件发送日志、失败重试与频控

### 2.3 为什么不能直接“加个邮箱字段”

如果只是简单保存邮箱，但不做验证与安全治理，会有这些问题：

- 他人可绑定错误邮箱或恶意邮箱
- 找回密码会变成高风险入口
- 无法确认邮箱归属
- 邮件通知会发错人
- 后期难以治理重复邮箱、脏数据、历史数据迁移

因此建议把该需求视为 **账号安全能力增强**，而不是普通资料字段扩展。

---

## 3. 设计原则

### 3.1 邮箱挂载位置

#### 学生

学生邮箱建议挂在 `student_accounts`，而不是 `students`。

原因：

- 邮箱属于账号安全信息，不是学籍资料
- 与 `loginAlias`、密码重置状态、锁定状态属于同一域
- 后续找回密码、邮箱验证、通知开关都更适合放在账号表

#### 教师 / 管理员

教师、管理员继续使用 `teachers.email`，但需升级为“已验证邮箱”模型。

### 3.2 验证通过前不可用于安全能力

只有 `email_verified = true` 时，邮箱才可用于：

- 忘记密码
- 接收选课邮件
- 作为安全通知接收地址

### 3.3 忘记密码的安全策略

- 不回显“账号不存在”或“邮箱不匹配”
- 前端统一提示：`如信息匹配，邮件已发送`
- 使用一次性 token
- token 必须有过期时间、使用状态、用途类型
- 必须做频控、防刷、防枚举

### 3.4 选课邮件只做补充

现有站内消息仍是主通知通道，邮件通知只作为增强能力：

- 用户没登录时也能收到提醒
- 适合第一轮结果、第二轮结果、管理员代选等场景
- 发送失败不影响业务主流程

---

## 4. 数据库设计

## 4.1 学生账号表扩展

表：`student_accounts`

新增字段：

- `email varchar(100) null`
- `email_verified bit not null default 0`
- `email_bound_at datetime null`
- `email_verified_at datetime null`
- `email_notify_enabled bit not null default 1`

说明：

- `email`：当前绑定邮箱
- `email_verified`：是否已验证
- `email_bound_at`：最近一次绑定时间
- `email_verified_at`：邮箱验证通过时间
- `email_notify_enabled`：是否接收邮件通知

建议索引：

- `idx_student_account_email(email)`

> 说明：由于教师和学生分属不同表，邮箱“全局唯一”首版建议先走服务层校验，不强行做数据库跨表唯一约束。

### 4.1.1 参考 SQL

```sql
ALTER TABLE student_accounts
    ADD COLUMN email VARCHAR(100) NULL,
    ADD COLUMN email_verified BIT NOT NULL DEFAULT b'0',
    ADD COLUMN email_bound_at DATETIME NULL,
    ADD COLUMN email_verified_at DATETIME NULL,
    ADD COLUMN email_notify_enabled BIT NOT NULL DEFAULT b'1';

CREATE INDEX idx_student_account_email ON student_accounts (email);
```

## 4.2 教师表扩展

表：`teachers`

保留现有：

- `email varchar(100) null`

新增字段：

- `email_verified bit not null default 0`
- `email_bound_at datetime null`
- `email_verified_at datetime null`
- `email_notify_enabled bit not null default 1`

建议索引：

- `idx_teacher_email(email)`

### 4.2.1 参考 SQL

```sql
ALTER TABLE teachers
    ADD COLUMN email_verified BIT NOT NULL DEFAULT b'0',
    ADD COLUMN email_bound_at DATETIME NULL,
    ADD COLUMN email_verified_at DATETIME NULL,
    ADD COLUMN email_notify_enabled BIT NOT NULL DEFAULT b'1';

CREATE INDEX idx_teacher_email ON teachers (email);
```

## 4.3 邮箱验证 / 重置密码令牌表

表：`account_email_tokens`

字段建议：

- `id bigint primary key auto_increment`
- `purpose varchar(20) not null`
- `principal_type varchar(20) not null`
- `principal_id bigint not null`
- `target_email varchar(100) not null`
- `token_hash varchar(128) not null`
- `expires_at datetime not null`
- `used_at datetime null`
- `created_at datetime not null`
- `request_ip varchar(45) null`
- `user_agent varchar(255) null`

字段说明：

- `purpose`：
  - `VERIFY_EMAIL`
  - `RESET_PASSWORD`
- `principal_type`：
  - `STUDENT`
  - `TEACHER`
- `token_hash`：仅存 hash，不明文存 token
- `used_at`：防止重复使用

建议索引：

- 唯一索引：`uk_account_email_token_hash(token_hash)`
- 普通索引：`idx_account_email_token_principal(principal_type, principal_id, purpose)`
- 普通索引：`idx_account_email_token_expires(expires_at)`

### 4.3.1 参考 SQL

```sql
CREATE TABLE account_email_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purpose VARCHAR(20) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id BIGINT NOT NULL,
    target_email VARCHAR(100) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    request_ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    CONSTRAINT uk_account_email_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_account_email_token_principal
    ON account_email_tokens (principal_type, principal_id, purpose);

CREATE INDEX idx_account_email_token_expires
    ON account_email_tokens (expires_at);
```

## 4.4 邮件发送外发箱表

表：`mail_outbox`

字段建议：

- `id bigint primary key auto_increment`
- `biz_type varchar(30) not null`
- `principal_type varchar(20) not null`
- `principal_id bigint not null`
- `recipient_email varchar(100) not null`
- `subject varchar(200) not null`
- `body_text text`
- `body_html text`
- `status varchar(20) not null`
- `retry_count int not null default 0`
- `last_error varchar(500) null`
- `next_retry_at datetime null`
- `created_at datetime not null`
- `sent_at datetime null`

建议状态：

- `PENDING`
- `SENT`
- `FAILED`

建议索引：

- `idx_mail_outbox_status(status, next_retry_at)`
- `idx_mail_outbox_principal(principal_type, principal_id)`

### 4.4.1 参考 SQL

```sql
CREATE TABLE mail_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_type VARCHAR(30) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id BIGINT NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body_text TEXT NULL,
    body_html TEXT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    next_retry_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    sent_at DATETIME NULL
);

CREATE INDEX idx_mail_outbox_status
    ON mail_outbox (status, next_retry_at);

CREATE INDEX idx_mail_outbox_principal
    ON mail_outbox (principal_type, principal_id);
```

---

## 5. 服务设计

## 5.1 邮箱绑定服务

建议新增：

- `AccountEmailService`

核心职责：

- 绑定邮箱申请
- 邮箱格式校验
- 跨角色唯一性校验
- 发送验证邮件
- 验证 token
- 邮箱验证成功落库
- 开关邮件通知

## 5.2 忘记密码服务

建议新增：

- `PasswordResetService`

核心职责：

- 根据账号定位用户
- 校验邮箱是否匹配且已验证
- 生成重置 token
- 发送重置邮件
- 校验 token
- 重置密码
- 清理学生锁定状态 / 登录失败次数

## 5.3 邮件发送服务

建议新增：

- `MailService`
- `MailOutboxService`
- `MailTemplateService`

建议模式：

- 业务代码只写入 `mail_outbox`
- 定时任务或异步任务负责真正发送
- 失败可重试
- 失败不影响主业务事务

---

## 6. 接口清单

## 6.1 匿名接口

### 6.1.1 申请找回密码

`POST /api/auth/password-reset/request`

入参：

```json
{
  "account": "S123456",
  "email": "student@example.com"
}
```

返回：

```json
{
  "code": 200,
  "message": "如信息匹配，邮件已发送"
}
```

### 6.1.2 校验重置链接

`GET /api/auth/password-reset/verify?token=...`

返回：

```json
{
  "code": 200,
  "data": {
    "valid": true,
    "principalType": "STUDENT"
  }
}
```

### 6.1.3 提交新密码

`POST /api/auth/password-reset/confirm`

入参：

```json
{
  "token": "xxx",
  "newPassword": "NewPass123"
}
```

返回：

```json
{
  "code": 200,
  "message": "密码重置成功"
}
```

---

## 6.2 学生接口

### 6.2.1 查询账号安全信息

`GET /api/student/account-security`

返回：

```json
{
  "code": 200,
  "data": {
    "loginId": "S123456",
    "loginAlias": "easy001",
    "email": "student@example.com",
    "emailVerified": true,
    "emailNotifyEnabled": true
  }
}
```

### 6.2.2 发起邮箱绑定

`POST /api/student/email/bind/request`

入参：

```json
{
  "email": "student@example.com"
}
```

### 6.2.3 确认邮箱绑定

`POST /api/student/email/bind/confirm`

入参：

```json
{
  "token": "xxx"
}
```

### 6.2.4 设置邮件通知开关

`POST /api/student/email/notify-toggle`

入参：

```json
{
  "enabled": true
}
```

---

## 6.3 教师 / 管理员接口

### 6.3.1 查询教师资料时扩展字段

扩展：

- `emailVerified`
- `emailNotifyEnabled`

### 6.3.2 发起邮箱绑定

`POST /api/teacher/email/bind/request`

### 6.3.3 确认邮箱绑定

`POST /api/teacher/email/bind/confirm`

### 6.3.4 设置通知开关

`POST /api/teacher/email/notify-toggle`

---

## 6.4 管理端接口

首版建议只加展示，不急着开放改写：

- 在学生账号管理页增加：
  - 邮箱
  - 邮箱是否已验证
  - 邮件通知是否开启

可选增强：

- 批量导入学生邮箱
- 批量导出邮箱绑定状态
- 重发邮箱验证邮件

---

## 7. 页面与交互建议

## 7.1 学生端

建议入口：

- Web：学生密码修改页 / 学生账号安全页
- Mobile：学生密码修改页 / 学生个人设置页

建议交互：

- 首次改密时可同时绑定便捷账号和邮箱
- 邮箱未验证时提示“仅验证后可用于找回密码”
- 提供“重新发送验证邮件”

## 7.2 教师 / 管理员端

建议入口：

- 教师资料页
- 管理员资料页

建议交互：

- 现有邮箱字段升级为“邮箱 + 验证状态”
- 若邮箱已变更，自动清空验证状态并重新验证

## 7.3 忘记密码页面

建议新增：

- Web 忘记密码页
- Mobile 忘记密码页

交互建议：

- 输入账号 + 邮箱
- 提示统一，不暴露是否存在该账号
- 点击邮件里的链接后进入设置新密码页

---

## 8. 选课邮件通知接入点

建议优先复用现有学生站内通知触发点：

1. 第一轮选课结果
2. 第二轮结束自动分配
3. 第二轮结束未分配课程
4. 退课成功
5. 管理员手动加课成功

接入方式：

- 业务层先写站内消息
- 同时写 `mail_outbox`
- 由邮件任务异步发送

不建议：

- 在开奖或结算事务里同步调用 SMTP

---

## 9. 安全要求

## 9.1 账号枚举防护

忘记密码接口统一返回：

`如信息匹配，邮件已发送`

禁止返回：

- 账号不存在
- 邮箱未绑定
- 邮箱不匹配

## 9.2 频控

至少做两层：

- 同账号频控：如 10 分钟内最多 3 次
- 同 IP 频控：如 10 分钟内最多 10 次

## 9.3 Token 安全

- token 必须带过期时间
- token 使用后立即失效
- token 仅存 hash
- 重置密码成功后，自动废弃该账号未使用 token

## 9.4 审计

建议记录：

- 谁申请了找回密码
- 发到了哪个邮箱
- token 是否使用
- 是否成功重置

---

## 10. 配置与依赖

## 10.1 Maven 依赖

建议增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
```

## 10.2 配置项建议

```properties
spring.mail.host=smtp.example.com
spring.mail.port=465
spring.mail.username=no-reply@example.com
spring.mail.password=***
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.ssl.enable=true

app.mail.from=no-reply@example.com
app.mail.enabled=true
app.mail.reset-password-expire-minutes=30
app.mail.verify-email-expire-minutes=30
app.mail.reset-password-limit-per-account=3
app.mail.reset-password-limit-per-ip=10
```

---

## 11. 开发排期

## 11.1 P0：账号安全基础

预计：`5 - 7 人天`

内容：

- 学生邮箱字段扩展
- 教师邮箱验证状态扩展
- 邮件发送能力接入
- 邮箱绑定 / 验证
- 忘记密码申请 / 验证 / 重置
- token 表与频控
- 基础自动化测试

交付标准：

- 已验证邮箱可找回密码
- 未验证邮箱不可找回密码
- 忘记密码链路可跑通

## 11.2 P1：选课邮件通知

预计：`3 - 4 人天`

内容：

- `mail_outbox`
- 邮件模板
- 选课通知接入
- 异步发送与失败重试
- 通知开关

交付标准：

- 第一轮/第二轮/退课/管理员加课邮件可发送
- 邮件失败不影响主业务

## 11.3 P2：后台治理与运营

预计：`2 - 3 人天`

内容：

- 管理端邮箱状态展示
- 导入 / 导出邮箱
- 重发验证邮件
- 发送日志与失败排查

交付标准：

- 可查看邮箱绑定率
- 可定位邮件发送失败原因

---

## 12. 风险评估

## 12.1 适用人群风险

如果学生群体较低龄，学生本人邮箱不一定稳定，可能更适合：

- 家长邮箱
- 学校统一邮箱

因此在立项前需先确认：

- 邮箱由谁维护
- 是否允许一个邮箱对应多个学生

## 12.2 数据治理风险

教师已有历史 `email` 数据，但并未验证，可能存在：

- 空值
- 错误邮箱
- 重复邮箱

上线前需做一次数据摸底与清洗。

## 12.3 发送性能风险

批量选课结果通知可能在短时间内发送大量邮件。

应对建议：

- 走 outbox + 异步发送
- 限流
- 重试退避

## 12.4 实施建议

最推荐顺序：

1. 先做邮箱绑定与验证
2. 再做忘记密码
3. 最后做选课邮件通知

不要建议的顺序：

1. 先上选课邮件
2. 再补邮箱验证
3. 最后补找回密码

因为这样会让“邮箱是否可信”长期处于不确定状态。

---

## 13. 最终建议

从投入产出比看，推荐如下：

- **第一阶段**：邮箱绑定 + 邮箱验证
- **第二阶段**：忘记密码
- **第三阶段**：选课邮件通知

其中：

- 忘记密码属于 **高优先级安全需求**
- 选课邮件属于 **中优先级体验增强**

如果资源有限，应先保证：

1. 邮箱可信
2. 找回密码安全
3. 通知异步不影响主流程

