# 选课回归工具

`scripts/regression/` 是仓库内正式维护的选课回归目录，后续请优先使用这里的脚本，不再以 `temp/` 下的历史脚本作为主入口。

## 工具清单

- `prepare_course_selection_concurrency.py`
  - 准备班级、学生、活动、课程和测试账号。
- `run_round1_round2_regression.py`
  - 第一轮提交、第一轮结算、第二轮并发抢课。
- `run_round2_drop_reselect_regression.py`
  - 验证第一轮已确认课程在第二轮退课后重新选课。
- `run_round3_request_approval_regression.py`
  - 验证第三轮申请、教师审批、学生状态回显。
- `run_round4_badminton_per_class_regression.py`
  - 验证按班名额课程的同班消耗。
- `run_round5_badminton_cross_class_regression.py`
  - 验证跨班混抢隔离。
- `run_gender_limit_regression_suite.py`
  - 性别限制业务边界套件。
  - 覆盖编辑课程保护、第一轮待结算过滤、第二轮历史不合规拦截、管理员手动加课拦截、三端可见性回归。
- `run_gender_reason_visibility_regression.py`
  - 单独验证 admin / teacher / student 三端的原因文案可见性。
- `course_selection_regression_lib.py`
  - 公共登录、建活动、选课、汇总能力。

## 环境变量

也可以先设置环境变量，再执行脚本：

```powershell
$env:COURSE_SELECTION_BASE_URL = "http://127.0.0.1:8080"
$env:COURSE_SELECTION_ADMIN_USERNAME = "admin"
$env:COURSE_SELECTION_ADMIN_PASSWORD = "1234qwer"
```

## 常用命令

```powershell
python scripts/regression/prepare_course_selection_concurrency.py
python scripts/regression/run_round1_round2_regression.py
python scripts/regression/run_round2_drop_reselect_regression.py
python scripts/regression/run_round3_request_approval_regression.py
python scripts/regression/run_round4_badminton_per_class_regression.py
python scripts/regression/run_round5_badminton_cross_class_regression.py
python scripts/regression/run_gender_limit_regression_suite.py
python scripts/regression/run_gender_reason_visibility_regression.py
```

## 输出目录

- 默认输出根目录：`scripts/regression/results`
- 每次运行会创建独立时间戳目录
- 主要产物通常包括：
  - `summary.json`
  - 账号导出文件
  - 过程 CSV / HTML / JSON 证据文件

## 说明

- `temp/` 下旧脚本可以保留作历史参考。
- 新增回归或日常联调，请优先补到 `scripts/regression/`。
