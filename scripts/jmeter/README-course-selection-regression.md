# 抢课全链路高并发回归说明

## 目标

用于回归验证抢课模块的关键链路：

1. 第一轮志愿提交
2. 第一轮抽签结算
3. 第二轮并发抢课
4. 退课后再次进入第二轮
5. 第三轮申请与教师审批
6. 管理端、教师端、学生端统计口径一致

## 当前正式工具

### JMeter

- `scripts/jmeter/course-selection-load-test.jmx`
  - 登录
  - 学生课程列表
  - 我的选课
  - 适合基础链路 smoke test
- `scripts/jmeter/course-selection-round2-select.jmx`
  - 第二轮并发抢课
  - 登录 -> 读取当前活动 -> 读取课程列表 -> 同步发起 `POST /api/student/courses/{id}/select`
  - 业务类 `400` 响应视为有效业务结果，不算脚本故障

### Python 全链路工具

- `scripts/regression/prepare_course_selection_concurrency.py`
- `scripts/regression/run_round1_round2_regression.py`
- `scripts/regression/run_round4_badminton_per_class_regression.py`
- `scripts/regression/run_round5_badminton_cross_class_regression.py`
- `scripts/regression/README.md`

## 数据与账号

- `scripts/jmeter/data/student_accounts.csv`
- `scripts/jmeter/data/student_accounts.sample.csv`
- `scripts/testdata/course-selection-600/README.md`
- `scripts/testdata/course-selection-600/course-selection-600-school1.sql`
- `scripts/db/activate_test_student_accounts.sql`

CSV 示例：

```csv
username,password,student_no,student_name
student001,Password@123,202604040001,QC0404-01-001
student002,Password@123,202604040002,QC0404-01-002
```

## 推荐执行顺序

1. 导入测试数据
2. 激活学生账号
3. 管理端确认课程和名额配置
4. 执行第一轮回归
5. 校验第一轮结算结果
6. 执行第二轮并发压测
7. 校验不超卖和统计口径
8. 做羽毛球按班余量 / 跨班隔离专项回归
9. 抽样执行退课后二次抢课
10. 关闭活动并验证第三轮申请审批

## 核心场景

### 场景 A：基础链路 smoke test

- 工具：`scripts/jmeter/course-selection-load-test.jmx`
- 关注：
  - 登录成功率
  - 课程列表接口耗时
  - 是否出现 5xx

### 场景 B：第一轮抽签 + 第二轮足球边界验证

- 工具：`scripts/regression/run_round1_round2_regression.py`
- 目标：
  - 第一轮完成后正确进入第二轮
  - 足球容量 40 时不超卖
  - 第二轮结果能落到标准 `summary.json`

### 场景 C：第二轮足球热点压测

- 工具：`scripts/jmeter/course-selection-round2-select.jmx`
- 推荐参数：
  - `users=200`
  - `syncUsers=200`
  - `targetCourseName=足球`

### 场景 D：羽毛球按班余量验证

- 工具：`scripts/regression/run_round4_badminton_per_class_regression.py`
- 目标：
  - 每班独立名额不超编
  - 每班剩余名额只允许本班学生抢

### 场景 E：羽毛球跨班混抢隔离验证

- 工具：`scripts/regression/run_round5_badminton_cross_class_regression.py`
- 目标：
  - 满额班学生不能抢到开放班剩余名额
  - `crossClassLeak` 必须为 `false`

## 第二轮 JMeter 命令示例

### 足球 40 人边界验证

```powershell
& "D:\tools\apache-jmeter-5.6.3\bin\jmeter.bat" `
  -n `
  -t "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\course-selection-round2-select.jmx" `
  -Jhost=127.0.0.1 `
  -Jport=8080 `
  -Jprotocol=http `
  -JaccountsCsv=scripts/jmeter/data/student_accounts.csv `
  -Jusers=40 `
  -JsyncUsers=40 `
  -JrampUp=1 `
  -Jloops=1 `
  -JsyncTimeoutMs=15000 `
  -JtargetCourseName=足球 `
  -l "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\round2-football-40.jtl" `
  -e -o "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\round2-football-40-report"
```

### 足球 200 人热点压测

```powershell
& "D:\tools\apache-jmeter-5.6.3\bin\jmeter.bat" `
  -n `
  -t "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\course-selection-round2-select.jmx" `
  -Jhost=127.0.0.1 `
  -Jport=8080 `
  -Jprotocol=http `
  -JaccountsCsv=scripts/jmeter/data/student_accounts.csv `
  -Jusers=200 `
  -JsyncUsers=200 `
  -JrampUp=1 `
  -Jloops=1 `
  -JsyncTimeoutMs=15000 `
  -JtargetCourseName=足球 `
  -l "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\round2-football-200.jtl" `
  -e -o "D:\code\PE_TEACHER_ASSISTANT_JAVA\scripts\jmeter\results\round2-football-200-report"
```

## 通过标准

### 第一轮

- 活动状态正常进入第二轮
- 已确认人数不超过总名额 / 班级名额
- 第一志愿优先规则生效

### 第二轮

- 目标课程无超卖
- 高并发下无大面积 5xx
- 失败主要表现为业务拒绝，而不是服务异常
- 管理端、教师端、学生端统计口径一致

### 羽毛球按班 / 跨班专项

- 任一班级最终确认人数不得超过配置上限
- 跨班混抢场景下 `crossClassLeak=false`

## 结果产物建议

每次回归建议保留：

- JMeter 原始结果 `.jtl`
- JMeter HTML 报告目录
- Python 回归脚本输出的 `summary.json`
- 各类 CSV 明细
- 管理端 / 教师端 / 学生端截图

建议命名格式：

- `scripts/jmeter/results/2026-04-04-round2-football-200.jtl`
- `scripts/regression/results/round1_round2_20260404_220000/summary.json`
