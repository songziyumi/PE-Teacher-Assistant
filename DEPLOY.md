# 服务端部署指引

## 环境信息

- 服务器：CentOS / OpenCloudOS
- 部署目录：`/opt/pe-assistant/`
- 启动方式：`systemd`
- 应用基线：`main`

## 部署前准备

### 1. 确认发布代码

示例：

```bash
git checkout main
git pull
```

如果你使用发布分支或标签，按你的实际流程切换即可。

### 2. 本地编译

先按仓库约定做编译检查：

```bash
mvn -q "-Dmaven.repo.local=.m2repo" -DskipTests compile
```

如需正式打包：

```bash
mvn clean package -DskipTests
```

生成产物示例：

```bash
target/pe-teacher-assistant-*.jar
```

### 3. 必填环境变量

生产环境至少需要准备这些变量：

```env
DB_PASSWORD=你的数据库密码
APP_ADMIN_DEFAULT_PASSWORD=你的初始管理员密码
APP_SUPER_ADMIN_DEFAULT_PASSWORD=你的初始超级管理员密码
APP_JWT_SECRET=一串足够长且随机的 JWT 密钥
```

建议统一写入：

```bash
/opt/pe-assistant/pe-assistant.env
```

### 4. 邮件配置

如果启用邮箱验证或找回密码，还需要补充邮件配置。

SMTP 示例：

```env
APP_MAIL_ENABLED=true
APP_MAIL_TRANSPORT=smtp
APP_MAIL_FROM=no-reply@example.com
APP_MAIL_BASE_URL=https://your-domain.com
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=your-smtp-username
SPRING_MAIL_PASSWORD=your-smtp-password
```

SES API 示例：

```env
APP_MAIL_ENABLED=true
APP_MAIL_TRANSPORT=ses-api
APP_MAIL_FROM=no-reply@your-domain.com
APP_MAIL_BASE_URL=https://your-domain.com
APP_MAIL_SES_API_SECRET_ID=your-secret-id
APP_MAIL_SES_API_SECRET_KEY=your-secret-key
APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID=your-template-id
APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID=your-template-id
```

## 首次部署

### 1. 上传应用

```bash
scp target/pe-teacher-assistant-*.jar root@your-server:/opt/pe-assistant/app.jar
```

### 2. 上传服务文件

```bash
scp pe-assistant.service root@your-server:/etc/systemd/system/pe-assistant.service
```

### 3. 准备环境变量文件

可以先上传模板：

```bash
scp pe-assistant.env.template root@your-server:/opt/pe-assistant/pe-assistant.env
```

然后在服务器上编辑：

```bash
vi /opt/pe-assistant/pe-assistant.env
```

### 4. 准备外部配置文件

服务器外部配置文件路径建议为：

```bash
/opt/pe-assistant/application.yml
```

示例：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pe_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: pe_user

server:
  port: 8080
```

说明：

- 敏感信息不要写入 `application.yml`
- 数据库密码、管理员密码等统一从环境变量读取
- 启动参数继续使用 `--spring.config.additional-location=/opt/pe-assistant/application.yml`
- 不要改成 `--spring.config.location=...`，否则会覆盖 JAR 内默认配置

### 5. 启动服务

```bash
systemctl daemon-reload
systemctl enable pe-assistant
systemctl start pe-assistant
```

## 更新部署

每次更新版本时执行：

```bash
mvn -q "-Dmaven.repo.local=.m2repo" -DskipTests compile
mvn clean package -DskipTests
scp target/pe-teacher-assistant-4.0.0.jar root@175.24.131.74:/opt/pe-assistant/app.jar
ssh root@175.24.131.74 "systemctl restart pe-assistant"
```

如果 `pe-assistant.service` 有变更，再执行：

```bash
ssh root@your-server "systemctl daemon-reload && systemctl restart pe-assistant"
```

## 常用运维命令

```bash
systemctl start pe-assistant
systemctl stop pe-assistant
systemctl restart pe-assistant
systemctl status pe-assistant
journalctl -u pe-assistant -f
journalctl -u pe-assistant -n 100
```

## 数据库检查

```bash
mysql -u root -p
SHOW DATABASES;
USE pe_assistant;
SHOW TABLES;
```

## 访问地址

- Web：`https://your-domain.com`
- 如果未接反向代理，也可直接访问：`http://your-server-ip:8080`

## 注意事项

- 确保服务器放通应用端口或由反向代理转发
- 建议部署前备份旧版 `app.jar`
- 建议部署前备份数据库
- `pe-assistant.service` 当前通过 `EnvironmentFile=-/opt/pe-assistant/pe-assistant.env` 加载环境变量
- 生产环境如启用邮件，`APP_MAIL_BASE_URL` 应填写正式 HTTPS 域名
