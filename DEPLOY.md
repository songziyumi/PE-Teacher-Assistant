# 服务器部署指南

## 环境信息
- 服务器：腾讯云 CentOS / OpenCloudOS
- IP：175.24.131.74
- Java：17+（已安装）
- 数据库：MySQL（已安装）
- 部署目录：`/opt/pe-assistant/`

---

## 首次部署步骤

### 1. 本地打包
```bash
mvn clean package -DskipTests
# 生成文件：target/pe-teacher-assistant-1.0.0.jar
```

### 2. 上传 JAR 包
在本地 PowerShell 执行（一行命令）：
```bash
scp target/pe-teacher-assistant-1.0.0.jar root@175.24.131.74:/opt/pe-assistant/app.jar
```

### 3. 服务器配置文件
配置文件路径：`/opt/pe-assistant/application.yml`，内容如下：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pe_assistant?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: pe_user
    password: 你的数据库密码
  jpa:
    hibernate:
      ddl-auto: update
server:
  port: 8080
```

### 4. 配置 systemd 系统服务

服务配置文件已保存在项目根目录 `pe-assistant.service`，直接上传到服务器：

```bash
# 本地执行（上传服务文件）
scp pe-assistant.service root@175.24.131.74:/etc/systemd/system/pe-assistant.service
```

然后在服务器上启用服务：

```bash
systemctl daemon-reload
systemctl enable pe-assistant
systemctl start pe-assistant
```

---

## 更新部署（后续版本）

每次更新只需执行以下步骤：

```bash
# 1. 本地打包
mvn clean package -DskipTests

# 2. 上传新 JAR（本地执行）
scp target/pe-teacher-assistant-1.0.0.jar root@175.24.131.74:/opt/pe-assistant/app.jar

# 3. 服务器重启服务
systemctl restart pe-assistant
```

---

## 常用运维命令

```bash
systemctl start pe-assistant     # 启动
systemctl stop pe-assistant      # 停止
systemctl restart pe-assistant   # 重启
systemctl status pe-assistant    # 查看状态
journalctl -u pe-assistant -f    # 实时查看日志
journalctl -u pe-assistant -n 100  # 查看最近 100 行日志
```

---

## 数据库操作

```bash
# 登录数据库
mysql -u root -p

# 常用 SQL
SHOW DATABASES;
USE pe_assistant;
SHOW TABLES;
```

---

## 访问地址
- 网站：http://175.24.131.74:8080

## 注意事项
- 腾讯云安全组需开放 TCP 8080 端口（入站规则，来源 0.0.0.0/0）
- 服务器防火墙：`iptables -I INPUT -p tcp --dport 8080 -j ACCEPT`
- 应用已配置为系统服务，服务器重启后自动启动
