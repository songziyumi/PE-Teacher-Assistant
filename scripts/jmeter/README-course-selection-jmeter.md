# 选课 JMeter 脚本

## 文件

- `scripts/jmeter/course-selection-load-test.jmx`
- `scripts/jmeter/data/student_accounts.sample.csv`

## 测试场景

- 管理端 API 页面加载压测
- 学生端 API 页面加载压测
- 管理端第一轮结算链路压测

## 使用前准备

1. 复制 `scripts/jmeter/data/student_accounts.sample.csv`
2. 重命名为 `scripts/jmeter/data/student_accounts.csv`
3. 填入真实学生账号密码

CSV 格式：

```csv
username,password
CS260001,123456
CS260002,123456
```

## 默认参数

- `host=127.0.0.1`
- `port=8080`
- `event_id=11`
- `student_csv=scripts/jmeter/data/student_accounts.csv`
- `admin_api_user=admin`
- `admin_api_password=Admin@2024!`
- `admin_web_user=admin`
- `admin_web_password=Admin@2024!`

## 在本机直接运行

如果你的 JMeter 安装在 `D:\tools\apache-jmeter-5.6.3`，PowerShell 可直接执行：

```powershell
& "D:\tools\apache-jmeter-5.6.3\bin\jmeter.bat" -t "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\course-selection-load-test.jmx"
```

## 命令行压测

```powershell
& "D:\tools\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\course-selection-load-test.jmx" -l "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\course-selection.jtl" -e -o "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\report"
```

## 覆盖参数示例

如果需要临时覆盖默认值，可追加 `-J` 参数：

```powershell
& "D:\tool\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\course-selection-load-test.jmx" -l "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\course-selection.jtl" -Jhost=127.0.0.1 -Jport=8080 -Jevent_id=11 -Jadmin_api_threads=2 -Jadmin_api_loops=5 -Jstudent_api_threads=20 -Jstudent_api_loops=3 -Jround1_poll_count=30 -Jround1_poll_interval_ms=1000
```

## 说明

- 第一轮结算线程组建议单用户执行
- API 场景使用 JWT `Bearer Token`
- 网页结算场景使用表单登录和 `CSRF`
- 提交到仓库时建议使用通用占位默认值；本地测试时再用 `-Jadmin_api_user`、`-Jadmin_api_password`、`-Jadmin_web_user`、`-Jadmin_web_password` 覆盖
