# 服务端部署指引

## 环境信息
- 服务器：CentOS / OpenCloudOS
- 部署目录：`/opt/pe-assistant/`
- 启动方式：`systemd`
- 应用基线：`main`
- 发布标签：`5.0`

---

## 部署前准备

### 1. 本地确认基线
确认当前发布基线为：

```bash
git checkout main
git pull
git checkout 5.0
```

### 2. 本地编译
按仓库约定先做编译检查：

```bash
mvn -q -Dmaven.repo.local=.m2repo -DskipTests compile
```

如需正式打包，可执行：

```bash
mvn clean package -DskipTests
```

生成产物示例：

```bash
target/pe-teacher-assistant-*.jar
```

### 3. 必填环境变量清单
以下变量在生产环境部署前必须准备：

```env
DB_PASSWORD=你的数据库密码
APP_ADMIN_DEFAULT_PASSWORD=你的管理员初始密码
APP_SUPER_ADMIN_DEFAULT_PASSWORD=你的超级管理员初始密码
APP_JWT_SECRET=一串足够长且随机的JWT密钥
```

说明：
- 缺少 `DB_PASSWORD` 会导致数据源初始化失败
- 缺少 `APP_ADMIN_DEFAULT_PASSWORD` 或 `APP_SUPER_ADMIN_DEFAULT_PASSWORD` 会导致初始化配置无法解析
- 缺少 `APP_JWT_SECRET` 会导致应用无法启动
- 建议将以上变量统一写入 `/opt/pe-assistant/pe-assistant.env`

### 4. 邮件环境变量
如果启用邮箱验证或忘记密码，还需要补充邮件配置。

SMTP 方案至少需要：

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

SES API 方案至少需要：

```env
APP_MAIL_ENABLED=true
APP_MAIL_TRANSPORT=ses-api
APP_MAIL_FROM=no-reply@your-domain.com
APP_MAIL_BASE_URL=https://your-domain.com
APP_MAIL_SES_API_SECRET_ID=你的SecretId
APP_MAIL_SES_API_SECRET_KEY=你的SecretKey
APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID=邮件模板ID
APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID=邮件模板ID
```

---

## 首次部署

### 1. 上传应用
将打包后的 JAR 上传到服务器固定位置：

```bash
scp target/pe-teacher-assistant-*.jar root@175.24.131.74:/opt/pe-assistant/app.jar
```

### 2. 上传服务文件
将仓库中的服务文件上传到服务器：

```bash
scp pe-assistant.service root@175.24.131.74:/etc/systemd/system/pe-assistant.service
```

### 3. 准备环境变量文件
先将示例文件上传到服务器：

```bash
scp pe-assistant.env.example root@175.24.131.74:/opt/pe-assistant/pe-assistant.env
```

然后在服务器上编辑：

```bash
vi /opt/pe-assistant/pe-assistant.env
```

填写真实值：

```env
DB_PASSWORD=你的数据库密码
APP_ADMIN_DEFAULT_PASSWORD=你的管理员初始密码
APP_SUPER_ADMIN_DEFAULT_PASSWORD=你的超级管理员初始密码
```

### 4. 准备外部配置文件
服务器配置文件路径：

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
- 敏感信息不再写入 `application.yml`
- 数据库密码和默认管理员密码统一从环境文件读取
- 启动参数继续使用 `--spring.config.additional-location=/opt/pe-assistant/application.yml`
- 不要改成 `--spring.config.location=...`，否则会覆盖 JAR 内默认配置

### 5. 启用服务

```bash
systemctl daemon-reload
systemctl enable pe-assistant
systemctl start pe-assistant
```

---

## 更新部署

每次更新版本时执行：

```bash
# 1. 本地编译/打包
mvn -q -Dmaven.repo.local=.m2repo -DskipTests compile
mvn clean package -DskipTests

# 2. 上传新包
scp target/pe-teacher-assistant-*.jar root@175.24.131.74:/opt/pe-assistant/app.jar

# 3. 重启服务
ssh root@175.24.131.74 "systemctl restart pe-assistant"
```

如果服务文件有变更，再补执行：

```bash
ssh root@175.24.131.74 "systemctl daemon-reload && systemctl restart pe-assistant"
```

---

## 常用运维命令

```bash
systemctl start pe-assistant
systemctl stop pe-assistant
systemctl restart pe-assistant
systemctl status pe-assistant
journalctl -u pe-assistant -f
journalctl -u pe-assistant -n 100
```

---

## 数据库检查

```bash
mysql -u root -p
SHOW DATABASES;
USE pe_assistant;
SHOW TABLES;
```

---

## 访问地址
- 网站：`http://175.24.131.74:8080`

---

## 注意事项
- 需要放通服务器 `8080` 端口
- 建议部署前备份旧版 `app.jar`
- 建议部署前备份数据库
- `pe-assistant.service` 现已通过 `EnvironmentFile=-/opt/pe-assistant/pe-assistant.env` 加载环境变量
- `src/main/resources/application.yml` 中敏感配置已改为强制从环境变量读取
