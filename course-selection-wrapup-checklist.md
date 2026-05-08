# 抢课功能收尾清单

> 更新时间：2026-04-08  
> 适用范围：第一轮志愿、第二轮抢课、退课再抢、第三轮申请、管理员干预、并发压测与结果核验

## 当前进度概览

### 已完成
- [x] 第二轮抢课核心并发问题已修复，已覆盖超卖场景
- [x] JMeter 抢课压测已完成，历史结果已留存在 `scripts/jmeter/results`
- [x] 端到端流程已完成验证
- [x] 诊断面板已完成验证
- [x] 第三轮申请流程已验证通过
- [x] 第二轮相关乱码文案已清理
- [x] 学生端网页/API 的第二轮抢课、退课提示已统一
- [x] 已增加统一汇总脚本，自动汇总成功数、业务拒绝数、异常数、是否超卖
- [x] 已生成固定格式的第二轮回归摘要文件，便于后续复用

### 进行中
- [ ] 补充一键校准工具
- [ ] 固化完整回归执行文档

### 待完成
- [ ] 统计报表、监控、审计、复盘文档

## P1

### 1. 清理第二轮相关乱码文案
- 状态：已完成
- 已完成项：
  - `CourseService` 第二轮抢课/退课提示恢复为正常中文
  - `StudentCourseController` 页面提示恢复正常
  - `StudentApiController` 接口返回提示恢复正常
  - `CourseSelectionPromptHelper` 第二轮相关提示归一化已统一
  - 对应回归测试已补齐并通过
- 验证结果：
  - 第二轮未开始：已验证
  - 不在参与名单：已验证
  - 已成功选课：已验证
  - 名额已满：已验证
  - 班级名额未配置：已验证
  - 第二轮期间退课限制：已验证

### 2. 固化并发验收脚本
- 状态：已完成
- 已完成项：
  - 新增统一汇总脚本：`scripts/regression/summarize_round2_concurrency.py`
  - 自动扫描 `scripts/regression/results/*/round2_concurrency_results.csv`
  - 自动汇总：
    - 成功数
    - 业务拒绝数
    - 异常数
    - 其他失败数
    - 是否超卖
    - 超卖数量
    - 失败原因分布
  - 为每个运行目录生成固定摘要：`round2_concurrency_summary.json`
  - 在结果根目录生成固定汇总：
    - `scripts/regression/results/round2_regression_summary.csv`
    - `scripts/regression/results/round2_regression_summary.json`
- 当前使用方式：
  - `python scripts/regression/summarize_round2_concurrency.py --include-existing-summary`

### 3. 增加容量一致性校验
- 状态：已完成
- 已完成项：
  - 新增只读校验脚本：`scripts/regression/check_capacity_consistency.py`
  - 支持校验：
    - `course.current_count`
    - `course_class_capacity.current_count`
    - `course_selections` 中 `CONFIRMED` 实际人数
  - 输出课程级明细、课程级差异、班级级明细、班级级差异、缺失容量配置、重复 `CONFIRMED` 记录
- 当前使用方式：
  - `python scripts/regression/check_capacity_consistency.py`
- 当前产物：
  - `course_capacity_check.csv`
  - `course_capacity_mismatches.csv`
  - `per_class_capacity_check.csv`
  - `per_class_capacity_mismatches.csv`
  - `per_class_missing_capacity.csv`
  - `duplicate_confirmed_selection_details.csv`
  - `summary.json`

### 4. 补管理员一键校准工具
- 状态：待完成
- 目标：
  - 支持校准单门课程总人数
  - 支持校准按班课程班级人数
  - 支持校准整场活动
- 约束：
  - 只修正统计字段
  - 不改历史选课记录

### 5. 固定回归执行清单
- 状态：部分完成
- 已完成项：
  - 第一轮/第二轮/第三轮关键流程已完成人工验证
  - JMeter 压测已完成
  - 第二轮并发摘要汇总已固化
- 待补文档：
  1. 导入测试数据
  2. 激活学生账号
  3. 配置课程与容量
  4. 第一轮确认与抽签
  5. 第二轮并发抢课
  6. 退课后再抢
  7. 第二轮自动收尾
  8. 第三轮申请与审批

## P2

### 6. 增加抢课结果统计报表
- 状态：待完成
- 建议输出：
  - 成功人数
  - 失败人数
  - 失败原因分布
  - 热门课程排行
  - 满额课程排行

### 7. 增强重复提交保护
- 状态：待完成
- 目标：
  - 降低同一学生短时间重复点击带来的无效并发
  - 保持现有业务提示不变

### 8. 统一压测结果归档规范
- 状态：部分完成
- 已完成项：
  - 第二轮统一汇总文件已落地
- 待补：
  - 统一命名规范
  - `.jtl`、HTML 报告、截图等归档要求

### 9. 补更多回归测试
- 状态：部分完成
- 已覆盖：
  - 第二轮关键服务层回归
  - 第二轮退课限制回归
  - 提示文案归一化回归
- 待补：
  - 退课后立刻再抢
  - 管理员强制加入后删除再加入
  - 第二轮结束瞬间提交
  - 自动收尾与教师未分配并存场景

## P3

### 10. 增加运行期监控
- 状态：待完成
- 建议监控：
  - 第二轮请求量
  - 成功率
  - 失败原因
  - 平均响应时间
  - ROUND2 超时未收尾告警

### 11. 增加操作审计视图
- 状态：待完成
- 建议记录：
  - 管理员强制加课
  - 管理员删选课
  - 容量校准
  - 活动手动关闭
  - 第三轮审批通过/拒绝

### 12. 增加活动复盘报表
- 状态：待完成
- 建议沉淀：
  - 第一轮报名与中签情况
  - 第二轮抢课情况
  - 第三轮申请情况
  - 管理员干预次数

### 13. 增加数据库修复预案文档
- 状态：待完成
- 建议覆盖故障：
  - 超卖
  - 课程人数不一致
  - 班级人数不一致
  - 教师未分配导致第二轮无法收尾
  - 学生已选中但班级未同步

## 建议执行顺序

### 本周优先
- 完成 P1-3：容量一致性只读核验
- 完成 P1-4：一键校准工具
- 完成 P1-5：补齐固定回归执行文档

### 下一阶段
- 推进 P2 的统计报表、归档规范、补充回归

### 后续优化
- 推进 P3 的监控、审计、复盘、修复预案
