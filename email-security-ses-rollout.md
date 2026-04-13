# 邮箱绑定与忘记密码（腾讯云 SES API 方案）

更新日期：2026-04-13

## 当前范围

- 已继续开发并保留：
  - 邮箱绑定
  - 邮箱验证
  - 忘记密码
- 已明确暂停：
  - 选课结果邮件通知
  - 其他批量业务通知邮件
- 不纳入当前方案：
  - 一次性恢复码

## 发信方案

- 发信域名：使用自有域名
- 发信服务：腾讯云 `SES`
- 接入方式：当前项目改为 `SES API`
- 当前代码链路：
  - 业务服务写入 `mail_outbox`
  - `MailOutboxDispatcher` 定时拉取并发送
  - 当前仅启用 `VERIFY_EMAIL` 与 `RESET_PASSWORD`

## 为什么改 API

- 个人认证新开通的 SES 账号已不再支持 SMTP
- API 更适合后续生产环境接入
- 保留 outbox 后，底层从 SMTP 换到 API 的改动面较小

## 当前项目配置项

- `APP_MAIL_ENABLED=true`
- `APP_MAIL_TRANSPORT=ses-api`
- `APP_MAIL_PRODUCT_NAME=体育教师助手`
- `APP_MAIL_FROM=no-reply@your-domain.com`
- `APP_MAIL_BASE_URL=https://your-domain.com`
- `APP_MAIL_SES_API_SECRET_ID=你的SecretId`
- `APP_MAIL_SES_API_SECRET_KEY=你的SecretKey`
- `APP_MAIL_SES_API_REGION=ap-guangzhou`
- `APP_MAIL_SES_API_ENDPOINT=ses.tencentcloudapi.com`
- `APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID=邮件模板ID`
- `APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID=邮件模板ID`

## 模板要求

腾讯云 SES API 默认走模板发信，所以你需要先在控制台创建两个模板。

### 邮箱验证模板变量

- `verifyToken`
- `expireMinutes`
- `productName`

### 重置密码模板变量

- `resetToken`
- `expireMinutes`
- `productName`

注意：腾讯云 SES 模板审核不允许把完整链接全部放在变量里。模板中需要固定域名与路径，只把 token 放进变量，例如：

- `https://jsqyty.com/email-verify?token={{verifyToken}}`
- `https://jsqyty.com/reset-password?token={{resetToken}}`

## 本地启动脚本

- 启动脚本：`scripts/start-local-tencent-ses.ps1`

示例：已执行过数据库脚本：

source D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\db\add_account_email_support.sql

- 用 SES API 启动项目：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-tencent-ses.ps1 `
  -SenderAddress "no-reply@your-domain.com" `
  -SecretId "你的SecretId" `
  -SecretKey "你的SecretKey" `
  -VerifyEmailTemplateId 45308 `
  -ResetPasswordTemplateId 45309 `
  -BaseUrl "http://localhost:8080"
```

手机联调示例：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-tencent-ses.ps1 `
  -SenderAddress "no-reply@your-domain.com" `
  -SecretId "你的SecretId" `
  -SecretKey "你的SecretKey" `
  -VerifyEmailTemplateId 10001 `
  -ResetPasswordTemplateId 10002 `
  -BaseUrl "http://192.168.1.100:8080"
```

## 腾讯云 SES 使用前提

- 需要先完成发信域名验证
- 需要创建可用发件地址
- 需要创建模板并通过审核
- `APP_MAIL_FROM` 应与已验证发件地址一致
- 如腾讯云账号是在 `2026-03-02` 之后开通 SES 的个人认证用户，官方已不再支持 SMTP 发信，需改走 API / 控制台发信

## 当前产品口径

- 已验证邮箱当前用于邮箱验证确认与忘记密码
- 当前不再对用户承诺“选课结果邮件通知”

## 后续再开 P1 时再做

- 选课结果邮件模板
- 批量通知开关
- 业务通知触发点接入
- 发送状态后台查询与人工重试
