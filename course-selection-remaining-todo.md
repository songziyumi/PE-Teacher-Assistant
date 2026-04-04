# 抢课功能剩余未完成清单（按优先级）
> 适用范围：学生抢课模块 v2  
> 最近更新：2026-04-04  
> 当前完成度评估：约 88%

## 已完成进度
### P0 已完成
- [x] 统一管理端 / 教师端 / 学生端统计口径
  - 报名人数统一按 `CONFIRMED` 唯一学生数统计
  - 已补 `remainingEnrollmentCapacity`、`overflowEnrollmentCount`
- [x] 全量复核“已确认 / 剩余 / 超编”口径
  - 管理页、明细页、教师端展示口径已对齐
- [x] 全链路高并发回归工具标准化
  - 已把历史 `temp/` 脚本正式迁移到 `scripts/regression/`
  - 已补回归说明与结果产物规范

### P1 已完成
- [x] 教师端名单页与管理端统计字段对齐
  - 教师端、管理端、学生端统计字段已基本对齐
- [x] 固化压测脚本与测试数据说明
  - 已整理：
    - `scripts/jmeter/README-course-selection-regression.md`
    - `scripts/regression/README.md`
    - `scripts/jmeter/course-selection-round2-select.jmx`
- [x] 管理员 / 教师端异常提示统一
  - 已统一申请已处理、名额已满、学生未分配行政班、无权处理等提示文案
  - 已补 `CourseSelectionPromptHelper` 与相关回归测试
- [x] 第三轮申请交互基础优化
  - 已支持 AJAX 提交、加载态、防重复提交、状态显示、教师备注显示
- [x] 运行监控与排障信息
  - 已补第二轮抢课、第一轮抽签、第二轮收尾的结构化日志
  - 已把强制超编审计接入管理员 / 教师时间线
- [x] 管理员诊断面板
  - 已新增管理员诊断接口与页面：`/admin/course-selection-diagnostics`
  - 已支持活动概况、风险问题、重点课程、近 24 小时排障活动
- [x] 第一轮自动抽签
  - 第一轮结束满 5 分钟后系统自动启动抽签
  - 管理员仍可手动提前执行，且已做重复触发保护
- [x] 抢课上线操作手册
  - 新增：`course-selection-operations-manual.md`
- [x] 抢课部署配置文档
  - 新增：`course-selection-deployment-config.md`

## 进行中
### P0：上线前仍需实际执行的事项
- [x] 再做一次真实环境全链路高并发回归
  - 当前状态：2026-04-04 已完成一轮真实环境回归留档，自动抽签与第二轮并发主链路已验证通过
  - 已验证结果：
    - 第一轮自动抽签：`eventId=25`，将 `round1End` 回拨到结束后 6 分钟后，约 `50.85s` 内从 `ROUND1` 自动切换到 `ROUND2`
    - 第一轮抽签 + 第二轮足球并发：`eventId=22`，`200` 并发下 `40` 成功、`160` 失败，`P95=3452.16ms`，最终足球确认 `40`，`oversold=false`
    - 第二轮羽毛球按班名额并发：`eventId=23`，第一轮每班 2 人，第二轮补齐到每班 4 人，`anyOversoldClass=false`
    - 羽毛球跨班混抢隔离：`eventId=24`，本班成功 `10`、跨班成功 `0`，`crossClassLeak=false`
    - 退课后再次进入第二轮：`eventId=30`，第一轮篮球确认后退课成功，随后第二轮成功抢到足球，原记录变为 `CANCELLED`
    - 第三轮申请与教师审批：`eventId=31`，教师账号 `19305450155` 完成 1 条同意、1 条拒绝，学生端 `requestable` / `requestStatus` / 审批审计回显均正确
  - 留档位置：
    - `scripts/regression/results/round1_auto_lottery_real_20260404_192954/summary.json`
    - `scripts/regression/results/round1_round2_20260404_175845/summary.json`
    - `scripts/regression/results/round4_badminton_per_class_20260404_190253/summary.json`
    - `scripts/regression/results/round5_badminton_cross_class_20260404_191536/summary.json`
    - `scripts/regression/results/round2_drop_reselect_20260404_201954/summary.json`
    - `scripts/regression/results/round3_request_approval_20260404_202322/summary.json`
  - 本轮结论：
    - 当前已验证场景未发现超卖
    - 自动抽签已按“第一轮结束后 5 分钟”逻辑生效
    - 第二轮全局名额与按班名额逻辑均符合预期
    - 退课回流、第三轮申请、教师审批与学生端状态回显链路均已通过真实环境验证

## 尚未完成
### P2（体验与运维增强）
- [ ] 继续优化第三轮申请交互
  - 申请理由输入体验
  - 提交成功 / 失败提示文案细化
  - 防重复提交后的页面反馈再收尾
- [ ] 诊断面板增强
  - 风险级别筛选
  - 导出诊断结果 JSON / 文本
  - 问题项跳转到对应活动 / 课程 / 明细页

### P3（持续完善）
- [ ] 增加更多自动化测试
  - 第一轮自动抽签测试
  - 诊断面板聚合逻辑测试
  - 第二轮失败边界场景测试
  - 第三轮申请防重复测试
- [ ] 页面与运维体验收尾
  - 更多状态提示文案统一
  - 关键操作后的成功 / 失败反馈再梳理

## 建议执行顺序
1. 在线上 / 预发环境执行一次完整高并发回归并留档（重点覆盖“自动抽签 + 第二轮并发 + 诊断面板”）
2. 补自动化测试（优先：自动抽签、诊断面板、第二轮失败边界）
3. 继续收尾第三轮申请交互体验
4. 基于诊断面板补导出 / 跳转等运维增强

## 当前结论
- 代码侧“统计口径统一 + 并发回归工具沉淀 + 运行监控 / 诊断面板 + 第一轮自动抽签”已基本完成
- 当前最关键的剩余工作，不是继续堆功能，而是：
  - 用标准工具跑一遍真实环境全链路回归，并验证自动抽签
  - 把关键路径补上自动化测试，避免后续改动回归
  - 做第三轮申请与少量页面体验的最后收尾
