# 抢课上线操作手册

> 适用范围：课程抽签 + 第二轮抢课 + 第三轮申请审批  
> 推荐分支：`feature/course-selection`

## 1. 上线前检查

### 代码与配置

- 确认已部署包含以下能力的版本：
  - 第二轮原子扣减修复
  - 管理端 / 教师端 / 学生端统计口径统一
  - 学生端防重复提交
- 确认配置文件已切到目标档位：
  - 低配：`src/main/resources/app-public.yml:48`
  - 高配：`src/main/resources/app-public.yml:82`

### 数据库

- 确认课程、报名记录、学生账号表结构已是最新版本
- 确认测试环境或正式环境中不存在旧活动脏数据干扰
- 抢课前建议备份数据库

### 服务状态

- 启动应用后检查登录页可访问
- 检查管理员账号可正常登录
- 检查学生 API 可正常返回当前活动与课程列表

## 2. 创建活动

### 管理端操作

1. 登录管理员后台
2. 进入课程活动管理页
3. 创建新的抢课活动
4. 设置：
   - 第一轮开始时间
   - 第一轮结束时间
   - 第二轮开始时间
   - 第二轮结束时间

### 注意

- 第一轮与第二轮时间窗口要连续且清晰
- 正式环境不要复用旧活动，避免历史报名记录干扰

## 3. 导入参与学生

### 方式一：使用现有学校数据

- 在后台按学校、年级、班级筛选并加入活动参与名单

### 方式二：使用标准测试脚本准备数据

- 参考：`scripts/regression/prepare_course_selection_concurrency.py:1`
- 示例：

```powershell
python scripts/regression/prepare_course_selection_concurrency.py `
  --base-url http://127.0.0.1:8080 `
  --admin-username qmadmin `
  --admin-password abc127!!! `
  --grade-name 高一 `
  --class-count 10 `
  --students-per-class 50 `
  --prefix QC0404
```

## 4. 配置课程与名额

### 推荐课程配置

- 篮球：120
- 足球：40
- 排球：40
- 羽毛球：每班 4
- 飞盘：40
- 匹克球：40
- 手球：40
- 武术：40
- 啦啦操：40
- 乒乓球：80

### 配置原则

- 全局名额课程使用总容量
- 羽毛球等按班限额课程使用 `PER_CLASS`
- 配好后逐门激活课程

## 5. 第一轮执行

### 学生端

- 学生登录并提交第一、第二志愿
- 学生确认参与第一轮抽签

### 管理端

- 确认第一轮时间已开启
- 在合适时间触发第一轮结算

### 结算后检查

- 活动状态是否正常进入第二轮
- 第一志愿优先是否生效
- 已确认人数是否未超过总名额 / 按班名额

## 6. 第二轮执行

### 学生端

- 仅第一轮未确认课程的学生可进入第二轮抢课
- 学生端按钮已支持防重复提交与加载态

### 并发压测

- 纯第二轮并发压测脚本：`scripts/jmeter/course-selection-round2-select.jmx:1`
- 全链路回归脚本：`scripts/regression/run_round1_round2_regression.py:1`

示例：

```powershell
python scripts/regression/run_round1_round2_regression.py `
  --base-url http://127.0.0.1:8080 `
  --admin-username qmadmin `
  --admin-password abc127!!! `
  --accounts-csv scripts/jmeter/data/student_accounts.csv `
  --round2-max-users 200
```

### 第二轮验收重点

- 是否无超卖
- 失败是否主要表现为业务拒绝，而不是 5xx
- 管理页、教师页、报名明细页统计是否一致

## 7. 第三轮执行

### 适用对象

- 活动关闭后，仍未确认课程的学生

### 流程

1. 学生提交第三轮申请
2. 教师审批通过 / 拒绝
3. 学生查看审批结果

### 验收点

- 仅未确认课程学生可申请
- 不允许重复申请
- 审批结果能正确反映到课程统计与名单

## 8. 回归验证与留档

### 标准回归文档

- `scripts/jmeter/README-course-selection-regression.md:1`
- `scripts/regression/README.md:1`

### 建议保留产物

- 回归脚本输出的 `summary.json`
- JMeter 原始 `.jtl`
- JMeter HTML 报告
- 管理端截图
- 教师端截图
- 学生端截图

## 9. 上线后巡检

### 重点关注

- 登录是否正常
- 当前活动是否正常显示
- 第二轮高峰期是否出现超时 / 5xx
- 抽签结算耗时是否明显异常
- 报名明细页与统计数字是否一致

### 如发现异常，优先排查

1. 当前活动时间窗口配置
2. 课程容量模式配置是否正确
3. 数据库连接池 / 线程池是否达到瓶颈
4. 是否存在旧活动或旧报名记录干扰

## 10. 相关资料

- 待办清单：`course-selection-remaining-todo.md:1`
- 全链路回归说明：`scripts/jmeter/README-course-selection-regression.md:1`
- 回归工具入口：`scripts/regression/README.md:1`
- 服务器档位配置：`src/main/resources/app-public.yml:1`

## 11. 运行监控与排障

### 日志关键字

- 第二轮抢课提交：`courseSelection.round2.submit`
- 第二轮结束收尾：`courseSelection.round2.finalize`
- 第二轮自动补分配：`courseSelection.round2.autoAssign`
- 第一轮抽签执行：`courseSelection.round1.lottery`
- 第一轮分志愿结算：`courseSelection.round1.phase`

### 推荐关注字段

- `eventId`：定位具体选课活动
- `courseId` / `studentId` / `classId`：定位课程、学生、班级
- `capacityMode` / `remainingCapacity`：定位名额模式与剩余名额
- `latencyMs`：观察高峰期耗时
- `reason` / `message`：快速判断失败原因分类

### 时间线排障入口

- 管理员时间线接口：`/api/admin/operation-timeline`
- 教师时间线接口：`/api/teacher/operation-timeline`
- 管理员诊断面板接口：`/api/admin/course-selection-diagnostics`
- 本轮已纳入：普通操作日志、申请审批审计、强制超编审计

### 排障建议

1. 先看 `courseSelection.round2.submit` 的 `reason` 分布，确认是名额已满、资格问题还是并发冲突
2. 再看 `courseSelection.round2.finalize` / `courseSelection.round2.autoAssign`，确认活动结束后是否存在未分配学生
3. 若涉及强制超编，直接在时间线中筛查 `FORCED_OVERFLOW`
4. 若涉及第一轮抽签异常，按 `courseSelection.round1.lottery` 和 `courseSelection.round1.phase` 追踪到具体课程
