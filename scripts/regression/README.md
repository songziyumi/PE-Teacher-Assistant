# 抢课全链路回归工具

`scripts/regression/` 是仓库内正式维护的抢课回归工具目录，用来替代 `temp/` 下的历史脚本。

## 工具清单

- `scripts/regression/prepare_course_selection_concurrency.py`
  - 生成班级/学生导入表
  - 导入测试数据
  - 创建活动与课程
  - 激活课程并推进到第二轮
  - 导出学生账号，供后续 JMeter / Python 回归复用
- `scripts/regression/run_round1_round2_regression.py`
  - 第一轮志愿提交
  - 第一轮抽签结算
  - 第二轮足球并发抢课
  - 输出标准 `summary.json`
- `scripts/regression/run_round4_badminton_per_class_regression.py`
  - 验证羽毛球按班名额
  - 验证每班剩余名额只被本班学生消化
- `scripts/regression/run_round5_badminton_cross_class_regression.py`
  - 验证羽毛球跨班混抢隔离
  - 检查“别班学生不能抢到本班剩余名额”
- `scripts/regression/run_round2_drop_reselect_regression.py`
  - 验证第一轮已确认学生在第二轮期间退课后，可重新抢第二轮课程
- `scripts/regression/run_round3_request_approval_regression.py`
  - 验证活动关闭后第三轮申请提交、教师同意、教师拒绝、学生侧状态回显
- `scripts/regression/course_selection_regression_lib.py`
  - 公共登录、活动创建、选课、汇总能力

## 环境变量

可以直接传命令行参数，也可以先设置：

```powershell
$env:COURSE_SELECTION_BASE_URL = "http://175.24.131.74:8080"
$env:COURSE_SELECTION_ADMIN_USERNAME = "qmadmin"
$env:COURSE_SELECTION_ADMIN_PASSWORD = "abc127!!!"
```

## 常用命令

### 1. 准备测试数据

```powershell
python scripts/regression/prepare_course_selection_concurrency.py `
  --grade-name 高一 `
  --class-count 10 `
  --students-per-class 50 `
  --prefix QC0404
```

### 2. 第一轮抽签 + 第二轮并发回归

```powershell
python scripts/regression/run_round1_round2_regression.py `
  --accounts-csv scripts/jmeter/data/student_accounts.csv `
  --round2-max-users 200
```

### 3. 羽毛球按班余量回归

```powershell
python scripts/regression/run_round4_badminton_per_class_regression.py `
  --accounts-csv scripts/jmeter/data/student_accounts.csv
```

### 4. 羽毛球跨班混抢隔离回归

```powershell
python scripts/regression/run_round5_badminton_cross_class_regression.py `
  --accounts-csv scripts/jmeter/data/student_accounts.csv
```

### 5. 退课后再次进入第二轮回归

```powershell
python scripts/regression/run_round2_drop_reselect_regression.py `
  --accounts-csv scripts/jmeter/data/student_accounts.csv
```

### 6. 第三轮申请与教师审批回归

```powershell
python scripts/regression/run_round3_request_approval_regression.py `
  --accounts-csv scripts/jmeter/data/student_accounts.csv `
  --auto-close-existing-events
```

## 输出目录

- 默认输出根目录：`scripts/regression/results`
- 每次执行会创建独立时间戳目录
- 标准产物包括：
  - `accounts_reset.xlsx`
  - `round1_preference_submit.csv`
  - `round1_confirm_submit.csv`
  - `round1_student_results.csv`
  - `round2_concurrency_results.csv`
  - `summary.json`

羽毛球专项脚本会额外输出：

- `badminton_per_class_summary.csv`
- `badminton_cross_class_summary.csv`

## 与 JMeter 的关系

- Python 脚本负责“全链路业务编排”和结果汇总
- JMeter 脚本负责“纯第二轮并发压测”
- 推荐组合方式：
  1. 先用 `prepare_course_selection_concurrency.py` 准备账号和活动
  2. 再用 `run_round1_round2_regression.py` 或 JMeter 做边界 / 热点压测
  3. 最后用教师端、学生端、报名明细页核对口径

## 迁移说明

以下历史脚本已由本目录正式接管：

- `temp/prepare_course_selection_concurrency.py`
- `temp/run_round1_round2_tests.py`
- `temp/run_round4_badminton_per_class_test.py`
- `temp/run_round5_badminton_cross_class_test.py`

`temp/` 可继续保留作历史参考，但后续回归请优先使用 `scripts/regression/`。
